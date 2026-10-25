#pragma once

#include <cstdint>

#include "Dsp.h"
#include "Envelope.h"
#include "SampleBank.h"
#include "SynthPatch.h"

namespace sonzinho {

// Barramentos onde as vozes somam: seco estéreo e envios mono para reverb e eco.
struct MixBus {
    float* left;
    float* right;
    float* reverb;
    float* echo;
};

struct NoteRequest {
    int32_t handle = 0;
    int instrument = 0;
    float midi = 60.0f;
    float velocity = 0.8f;
    float pan = 0.0f;       // -1 (esquerda) .. 1 (direita)
    float duration = 0.0f;  // segundos até liberar sozinha; 0 = espera note-off ou fim natural
};

class Voice {
public:
    void startSample(const NoteRequest& req, const SampledInstrument* inst, float outputRate,
                     float brightness, int64_t startTime);
    void startSynth(const NoteRequest& req, const SynthPatch* patch, float outputRate,
                    float brightness, int64_t startTime);

    void release() { env_.release(); }
    void steal() { env_.steal(); }
    void setTarget(float midi, float brightness) {
        targetMidi_ = midi;
        brightnessMul_ = brightness;
    }

    // Soma `frames` quadros nos barramentos. Devolve false quando a voz terminou.
    bool render(const MixBus& bus, int frames);

    bool active() const { return env_.active(); }
    bool releasing() const { return env_.releasing(); }
    bool stealing() const { return env_.stage() == Envelope::Stage::Steal; }
    float level() const { return env_.level() * baseGain_; }
    int32_t handle() const { return handle_; }
    int instrument() const { return instrument_; }
    float midi() const { return targetMidi_; }
    int64_t startTime() const { return startTime_; }

private:
    void setPan(float pan);
    bool renderSample(const MixBus& bus, int frames);
    bool renderSynth(const MixBus& bus, int frames);
    float oscillator(float phase, float dt);

    enum class Kind { Sample, Synth } kind_ = Kind::Sample;
    Envelope env_;
    int32_t handle_ = 0;
    int instrument_ = 0;
    int64_t startTime_ = 0;
    float outputRate_ = 48000.0f;
    float targetMidi_ = 60.0f;
    float currentMidi_ = 60.0f;
    float velocity_ = 0.8f;
    float baseGain_ = 0.0f;
    float panL_ = 0.707f, panR_ = 0.707f;
    float brightnessMul_ = 1.0f;
    float elapsed_ = 0.0f;  // segundos desde o início
    float duration_ = 0.0f;
    float reverbSend_ = 0.25f;
    float echoSend_ = 0.0f;

    // Amostra
    const SampleZone* zone_ = nullptr;
    double position_ = 0.0;
    double step_ = 1.0;
    float lpCoef_ = 1.0f;
    float lpL_ = 0.0f, lpR_ = 0.0f;

    // Síntese
    const SynthPatch* patch_ = nullptr;
    float phase1_ = 0.0f, phase2_ = 0.0f, phaseSub_ = 0.0f, phaseMod_ = 0.0f;
    float vibPhase_ = 0.0f, tremPhase_ = 0.0f;
    Svf filter_;
    Svf formant_;
    Noise noise_;
};

}  // namespace sonzinho
