#include "AudioOutput.h"

#include <android/log.h>

#if defined(__aarch64__)
#include <cstdint>
#elif defined(__x86_64__) || defined(__i386__)
#include <xmmintrin.h>
#endif

#define LOG_TAG "SonzinhoAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace sonzinho {

namespace {

// Números desnormais deixam reverb e filtros lentíssimos quando o som se apaga.
void flushDenormalsToZero() {
#if defined(__aarch64__)
    uint64_t fpcr;
    asm volatile("mrs %0, fpcr" : "=r"(fpcr));
    asm volatile("msr fpcr, %0" ::"r"(fpcr | (1ull << 24)));
#elif defined(__x86_64__) || defined(__i386__)
    _mm_setcsr(_mm_getcsr() | 0x8040);
#endif
}

}  // namespace

bool AudioOutput::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    wanted_.store(true);
    if (stream_) return stream_->requestStart() == oboe::Result::OK;
    return openLocked();
}

bool AudioOutput::openLocked() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(48000)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setUsage(oboe::Usage::Game)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        LOGW("Falha ao abrir stream exclusivo (%s); tentando compartilhado", oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(stream);
        if (result != oboe::Result::OK) {
            LOGW("Falha ao abrir stream: %s", oboe::convertToText(result));
            return false;
        }
    }
    stream_ = stream;
    sampleRate_.store(stream->getSampleRate());
    framesPerBurst_.store(stream->getFramesPerBurst());
    mmap_.store(oboe::OboeExtensions::isMMapUsed(stream.get()));
    engine_.prepare(static_cast<float>(stream->getSampleRate()));
    // Dois bursts de buffer: o mínimo estável; o LatencyTuner aumenta se houver underrun.
    stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2);
    tuner_ = std::make_unique<oboe::LatencyTuner>(*stream);
    stream->setPerformanceHintEnabled(true);
    LOGI("Stream aberto: %d Hz, burst %d, buffer %d, MMAP=%d, compartilhamento=%s", stream->getSampleRate(),
         stream->getFramesPerBurst(), stream->getBufferSizeInFrames(), mmap_.load() ? 1 : 0,
         oboe::convertToText(stream->getSharingMode()));
    return stream->requestStart() == oboe::Result::OK;
}

void AudioOutput::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    wanted_.store(false);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
        tuner_.reset();
    }
}

double AudioOutput::latencyMillis() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!stream_) return -1.0;
    auto latency = stream_->calculateLatencyMillis();
    return latency ? latency.value() : -1.0;
}

oboe::DataCallbackResult AudioOutput::onAudioReady(oboe::AudioStream* /*stream*/, void* audioData,
                                                   int32_t numFrames) {
    if (!denormalsConfigured_.exchange(true)) flushDenormalsToZero();
    engine_.render(static_cast<float*>(audioData), numFrames);
    if (tuner_) tuner_->tune();
    return oboe::DataCallbackResult::Continue;
}

void AudioOutput::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    // Fone conectado/desconectado ou troca de rota: reabre no novo dispositivo.
    LOGW("Stream fechado (%s); reabrindo", oboe::convertToText(error));
    std::lock_guard<std::mutex> lock(mutex_);
    stream_.reset();
    tuner_.reset();
    denormalsConfigured_.store(false);
    if (wanted_.load()) openLocked();
}

}  // namespace sonzinho
