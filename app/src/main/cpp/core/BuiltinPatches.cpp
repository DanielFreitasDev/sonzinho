#include "BuiltinPatches.h"

namespace sonzinho {

namespace {

SynthPatch softPluck() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Sine);
    p.cutoffHz = 4000;
    p.attack = 0.004f;
    p.decay = 0.9f;
    p.sustain = 0.0f;
    p.release = 0.4f;
    p.gain = 0.7f;
    return p;
}

SynthPatch cloudPad() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Pad);
    p.detuneCents = 9;
    p.subLevel = 0.15f;
    p.cutoffHz = 1400;
    p.resonance = 0.6f;
    p.filterEnv = 1.0f;
    p.keyTrack = 0.3f;
    p.attack = 0.35f;
    p.decay = 1.2f;
    p.sustain = 0.75f;
    p.release = 1.6f;
    p.vibratoRate = 4.5f;
    p.vibratoDepth = 0.05f;
    p.gain = 0.5f;
    p.reverbSend = 0.5f;
    p.echoSend = 0.1f;
    p.maxSeconds = 3.0f;
    return p;
}

SynthPatch warmBass() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Triangle);
    p.subLevel = 0.6f;
    p.cutoffHz = 700;
    p.resonance = 0.7f;
    p.filterEnv = 1.2f;
    p.keyTrack = 0.2f;
    p.attack = 0.01f;
    p.decay = 0.6f;
    p.sustain = 0.5f;
    p.release = 0.35f;
    p.gain = 1.0f;
    p.reverbSend = 0.08f;
    p.maxSeconds = 1.2f;
    return p;
}

SynthPatch bell(float ratio, float index, float fmDecay, float decay, float gain) {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Bell);
    p.fmRatio = ratio;
    p.fmIndex = index;
    p.fmDecay = fmDecay;
    p.cutoffHz = 6000;
    p.keyTrack = 0.2f;
    p.attack = 0.002f;
    p.decay = decay;
    p.sustain = 0.0f;
    p.release = 0.8f;
    p.gain = gain;
    p.reverbSend = 0.35f;
    return p;
}

SynthPatch flute() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Triangle);
    p.cutoffHz = 2500;
    p.attack = 0.04f;
    p.decay = 0.3f;
    p.sustain = 0.7f;
    p.release = 0.35f;
    p.vibratoRate = 5.0f;
    p.vibratoDepth = 0.12f;
    p.noise = 0.03f;
    p.gain = 0.6f;
    p.reverbSend = 0.3f;
    p.maxSeconds = 1.5f;
    return p;
}

SynthPatch bubbles() {
    SynthPatch p = softPluck();
    p.pitchStart = -5;
    p.pitchEndTime = 0.06f;
    p.decay = 0.25f;
    p.release = 0.15f;
    p.cutoffHz = 5000;
    p.gain = 0.8f;
    p.reverbSend = 0.3f;
    p.echoSend = 0.2f;
    return p;
}

SynthPatch bird() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Sine);
    p.vibratoRate = 22;
    p.vibratoDepth = 1.2f;
    p.pitchStart = 7;
    p.pitchMid = -2;
    p.pitchMidTime = 0.06f;
    p.pitchEndTime = 0.14f;
    p.attack = 0.005f;
    p.decay = 0.28f;
    p.sustain = 0.0f;
    p.release = 0.08f;
    p.cutoffHz = 7000;
    p.gain = 0.8f;
    p.reverbSend = 0.3f;
    p.maxSeconds = 0.35f;
    return p;
}

SynthPatch cat() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Saw);
    p.cutoffHz = 2600;
    p.resonance = 0.8f;
    p.formantStart = 700;
    p.formantMid = 1700;
    p.formantEnd = 900;
    p.formantTime = 0.55f;
    p.formantMix = 0.8f;
    p.pitchStart = -2;
    p.pitchMid = 3;
    p.pitchMidTime = 0.18f;
    p.pitchEndTime = 0.6f;
    p.attack = 0.04f;
    p.decay = 0.5f;
    p.sustain = 0.4f;
    p.release = 0.25f;
    p.vibratoRate = 6;
    p.vibratoDepth = 0.15f;
    p.gain = 0.3f;
    p.reverbSend = 0.25f;
    p.maxSeconds = 0.55f;
    return p;
}

SynthPatch duck() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Square);
    p.cutoffHz = 2200;
    p.formantStart = 900;
    p.formantMid = 1300;
    p.formantEnd = 700;
    p.formantTime = 0.18f;
    p.formantMix = 0.85f;
    p.pitchStart = 2;
    p.pitchEndTime = 0.2f;
    p.tremoloRate = 30;
    p.tremoloDepth = 0.3f;
    p.attack = 0.01f;
    p.decay = 0.2f;
    p.sustain = 0.3f;
    p.release = 0.08f;
    p.gain = 0.32f;
    p.reverbSend = 0.2f;
    p.maxSeconds = 0.18f;
    return p;
}

SynthPatch frog() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Triangle);
    p.subLevel = 0.2f;
    p.tremoloRate = 24;
    p.tremoloDepth = 0.85f;
    p.pitchStart = 3;
    p.pitchEndTime = 0.25f;
    p.cutoffHz = 1500;
    p.attack = 0.01f;
    p.decay = 0.4f;
    p.sustain = 0.0f;
    p.release = 0.1f;
    p.gain = 1.3f;
    p.reverbSend = 0.2f;
    p.maxSeconds = 0.35f;
    return p;
}

SynthPatch bear() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Saw);
    p.cutoffHz = 500;
    p.formantStart = 350;
    p.formantMid = 500;
    p.formantEnd = 400;
    p.formantTime = 0.8f;
    p.formantMix = 0.6f;
    p.subLevel = 0.4f;
    p.attack = 0.08f;
    p.decay = 0.6f;
    p.sustain = 0.6f;
    p.release = 0.4f;
    p.vibratoRate = 5;
    p.vibratoDepth = 0.1f;
    p.pitchStart = -1;
    p.pitchEndTime = 0.3f;
    p.gain = 0.55f;
    p.reverbSend = 0.25f;
    p.maxSeconds = 0.8f;
    return p;
}

SynthPatch whale() {
    SynthPatch p;
    p.wave = static_cast<float>(Wave::Sine);
    p.subLevel = 0.2f;
    p.pitchStart = -3;
    p.pitchMid = 4;
    p.pitchMidTime = 0.7f;
    p.pitchEndTime = 1.6f;
    p.attack = 0.3f;
    p.decay = 1.0f;
    p.sustain = 0.7f;
    p.release = 1.2f;
    p.cutoffHz = 1200;
    p.vibratoRate = 3;
    p.vibratoDepth = 0.2f;
    p.gain = 0.55f;
    p.reverbSend = 0.8f;
    p.echoSend = 0.25f;
    p.maxSeconds = 1.4f;
    return p;
}

}  // namespace

void loadBuiltinPatches(std::array<SynthPatch, 128>& patches) {
    for (auto& p : patches) p = softPluck();
    patches[16] = cloudPad();
    patches[17] = warmBass();
    patches[18] = bell(3.5f, 1.6f, 0.5f, 2.2f, 0.55f);   // sininho (também usado como reserva)
    patches[19] = flute();
    patches[20] = bubbles();
    patches[21] = bell(4.0f, 1.0f, 0.25f, 1.6f, 0.6f);   // celesta
    patches[22] = bell(7.0f, 0.8f, 0.15f, 1.4f, 0.45f);  // caixinha de música
    patches[32] = bird();
    patches[33] = cat();
    patches[34] = duck();
    patches[35] = frog();
    patches[36] = bear();
    patches[37] = whale();
}

}  // namespace sonzinho
