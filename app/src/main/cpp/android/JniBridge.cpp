// Ponte JNI entre `NativeAudioBridge.kt` e o motor. Todas as funções são rápidas e não
// bloqueiam: apenas empilham comandos para a thread de áudio.
#include <jni.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "../core/Engine.h"
#include "AudioOutput.h"

using namespace sonzinho;

namespace {

struct Native {
    Engine engine;
    AudioOutput output{engine};
};

Native* fromHandle(jlong handle) { return reinterpret_cast<Native*>(handle); }

}  // namespace

#define JNI_FN(name) Java_dev_danielfreitas_sonzinho_data_audio_NativeAudioBridge_##name

extern "C" {

JNIEXPORT jlong JNICALL JNI_FN(nativeCreate)(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new Native());
}

JNIEXPORT void JNICALL JNI_FN(nativeDestroy)(JNIEnv*, jobject, jlong handle) {
    auto* n = fromHandle(handle);
    n->output.stop();
    delete n;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeStart)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->output.start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeStop)(JNIEnv*, jobject, jlong handle) { fromHandle(handle)->output.stop(); }

JNIEXPORT jint JNICALL JNI_FN(nativeNoteOn)(JNIEnv*, jobject, jlong handle, jint instrument, jfloat midi,
                                            jfloat velocity, jfloat pan, jfloat duration) {
    return fromHandle(handle)->engine.noteOn(instrument, midi, velocity, pan, duration);
}

JNIEXPORT void JNICALL JNI_FN(nativeNoteOff)(JNIEnv*, jobject, jlong handle, jint voice) {
    fromHandle(handle)->engine.noteOff(voice);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetVoiceTarget)(JNIEnv*, jobject, jlong handle, jint voice, jfloat midi,
                                                    jfloat brightness) {
    fromHandle(handle)->engine.setVoiceTarget(voice, midi, brightness);
}

JNIEXPORT void JNICALL JNI_FN(nativeAllNotesOff)(JNIEnv*, jobject, jlong handle) {
    fromHandle(handle)->engine.allNotesOff();
}

JNIEXPORT void JNICALL JNI_FN(nativeDefinePatch)(JNIEnv* env, jobject, jlong handle, jint slot, jfloatArray params) {
    const jsize count = env->GetArrayLength(params);
    std::vector<float> values(count);
    env->GetFloatArrayRegion(params, 0, count, values.data());
    fromHandle(handle)->engine.definePatch(slot, values.data(), count);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetConfig)(JNIEnv*, jobject, jlong handle, jint polyphony, jfloat volume,
                                               jfloat reverb, jfloat brightness) {
    EngineConfig config;
    config.maxPolyphony = polyphony;
    config.masterVolume = volume;
    config.reverbWet = reverb;
    config.brightness = brightness;
    fromHandle(handle)->engine.setConfig(config);
}

JNIEXPORT void JNICALL JNI_FN(nativeLoadInstrument)(JNIEnv* env, jobject, jlong handle, jint slot, jfloat gain,
                                                    jfloat release, jfloatArray roots, jfloatArray rates,
                                                    jintArray channels, jobjectArray pcm) {
    auto inst = std::make_unique<SampledInstrument>();
    inst->gain = gain;
    inst->releaseSeconds = release;
    const jsize zones = env->GetArrayLength(roots);
    std::vector<float> rootValues(zones), rateValues(zones);
    std::vector<jint> channelValues(zones);
    env->GetFloatArrayRegion(roots, 0, zones, rootValues.data());
    env->GetFloatArrayRegion(rates, 0, zones, rateValues.data());
    env->GetIntArrayRegion(channels, 0, zones, channelValues.data());
    for (jsize i = 0; i < zones; ++i) {
        auto data = static_cast<jshortArray>(env->GetObjectArrayElement(pcm, i));
        const jsize length = env->GetArrayLength(data);
        SampleZone zone;
        zone.rootMidi = rootValues[i];
        zone.sampleRate = rateValues[i];
        zone.channels = std::max(1, std::min(2, static_cast<int>(channelValues[i])));
        zone.pcm.resize(length);
        env->GetShortArrayRegion(data, 0, length, zone.pcm.data());
        zone.frames = length / zone.channels;
        env->DeleteLocalRef(data);
        inst->zones.push_back(std::move(zone));
    }
    std::sort(inst->zones.begin(), inst->zones.end(),
              [](const SampleZone& a, const SampleZone& b) { return a.rootMidi < b.rootMidi; });
    fromHandle(handle)->engine.publishInstrument(slot, std::move(inst));
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeIsInstrumentReady)(JNIEnv*, jobject, jlong handle, jint slot) {
    return fromHandle(handle)->engine.isInstrumentReady(slot) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeSetSequence)(JNIEnv* env, jobject, jlong handle, jint id, jdouble length,
                                                 jboolean loop, jdoubleArray times, jintArray instruments,
                                                 jfloatArray midis, jfloatArray velocities, jfloatArray durations) {
    auto seq = std::make_unique<Sequence>();
    seq->id = static_cast<uint32_t>(id);
    seq->length = std::max(0.05, static_cast<double>(length));
    seq->loop = loop == JNI_TRUE;
    const jsize count = env->GetArrayLength(times);
    std::vector<double> t(count);
    std::vector<jint> inst(count);
    std::vector<float> midi(count), vel(count), dur(count);
    env->GetDoubleArrayRegion(times, 0, count, t.data());
    env->GetIntArrayRegion(instruments, 0, count, inst.data());
    env->GetFloatArrayRegion(midis, 0, count, midi.data());
    env->GetFloatArrayRegion(velocities, 0, count, vel.data());
    env->GetFloatArrayRegion(durations, 0, count, dur.data());
    seq->events.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        if (t[i] < 0.0 || t[i] >= seq->length) continue;
        seq->events.push_back({t[i], inst[i], midi[i], vel[i], dur[i]});
    }
    std::stable_sort(seq->events.begin(), seq->events.end(),
                     [](const SequenceEvent& a, const SequenceEvent& b) { return a.time < b.time; });
    fromHandle(handle)->engine.setSequence(std::move(seq));
}

JNIEXPORT void JNICALL JNI_FN(nativePlaySequence)(JNIEnv*, jobject, jlong handle, jboolean fromStart) {
    fromHandle(handle)->engine.playSequence(fromStart == JNI_TRUE);
}

JNIEXPORT void JNICALL JNI_FN(nativeStopSequence)(JNIEnv*, jobject, jlong handle) {
    fromHandle(handle)->engine.stopSequence();
}

JNIEXPORT jdouble JNICALL JNI_FN(nativeSequencePosition)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->engine.sequencePosition();
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeSequencePlaying)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->engine.sequencePlaying() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeSetMuted)(JNIEnv*, jobject, jlong handle, jboolean muted) {
    fromHandle(handle)->engine.setMuted(muted == JNI_TRUE);
}

JNIEXPORT jdouble JNICALL JNI_FN(nativeLatencyMillis)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->output.latencyMillis();
}

JNIEXPORT jint JNICALL JNI_FN(nativeSampleRate)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->output.sampleRate();
}

JNIEXPORT jint JNICALL JNI_FN(nativeFramesPerBurst)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->output.framesPerBurst();
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeIsMmap)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->output.isMmap() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL JNI_FN(nativeActiveVoices)(JNIEnv*, jobject, jlong handle) {
    return fromHandle(handle)->engine.activeVoices();
}

}  // extern "C"
