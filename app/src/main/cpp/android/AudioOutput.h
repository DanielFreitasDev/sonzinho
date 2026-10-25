#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>

#include "../core/Engine.h"

namespace sonzinho {

// Saída de baixa latência: AAudio (MMAP exclusivo quando disponível) via Oboe.
class AudioOutput : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    explicit AudioOutput(Engine& engine) : engine_(engine) {}
    ~AudioOutput() override { stop(); }

    bool start();
    void stop();
    double latencyMillis();
    int32_t sampleRate() const { return sampleRate_.load(); }
    int32_t framesPerBurst() const { return framesPerBurst_.load(); }
    bool isMmap() const { return mmap_.load(); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openLocked();

    Engine& engine_;
    std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<oboe::LatencyTuner> tuner_;
    std::atomic<bool> wanted_{false};
    std::atomic<bool> denormalsConfigured_{false};
    std::atomic<int32_t> sampleRate_{48000};
    std::atomic<int32_t> framesPerBurst_{0};
    std::atomic<bool> mmap_{false};
};

}  // namespace sonzinho
