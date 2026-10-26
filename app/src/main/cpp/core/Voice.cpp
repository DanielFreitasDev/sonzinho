#include "Voice.h"

#include <cmath>

namespace sonzinho {

namespace {

constexpr int kControlBlock = 32;  // parâmetros (glide, filtro, vibrato) atualizados a cada 32 quadros

float velocityCurve(float v) {
    v = clampf(v, 0.05f, 1.0f);
    return std::pow(v, 1.3f);
}

float threePoint(float t, float start, float mid, float midTime, float endTime, float end) {
    if (endTime <= 0.0f) return end;
    if (t >= endTime) return end;
    if (midTime <= 0.0f || midTime >= endTime) return start + (end - start) * (t / endTime);
    if (t < midTime) return start + (mid - start) * (t / midTime);
    return mid + (end - mid) * ((t - midTime) / (endTime - midTime));
}

}  // namespace

void Voice::setPan(float pan) {
    const float p = clampf(pan, -1.0f, 1.0f);
    const float angle = (p + 1.0f) * 0.25f * kPi;
    panL_ = std::cos(angle);
    panR_ = std::sin(angle);
}

void Voice::startSample(const NoteRequest& req, const SampledInstrument* inst, float outputRate,
                        float brightness, int64_t startTime) {
    kind_ = Kind::Sample;
    handle_ = req.handle;
    instrument_ = req.instrument;
    startTime_ = startTime;
    outputRate_ = outputRate;
    targetMidi_ = currentMidi_ = req.midi;
    velocity_ = req.velocity;
    duration_ = req.duration;
    elapsed_ = 0.0f;
    brightnessMul_ = brightness;
    zone_ = inst->zoneFor(req.midi);
    position_ = 0.0;
    lpL_ = lpR_ = 0.0f;
    baseGain_ = 1.0f * velocityCurve(req.velocity) * inst->gain;
    reverbSend_ = 0.22f;
    echoSend_ = 0.0f;
    setPan(req.pan);
    env_.configure(0.0015f, 10.0f, 1.0f, inst->releaseSeconds, outputRate);
    env_.trigger();
}

void Voice::startSynth(const NoteRequest& req, const SynthPatch* patch, float outputRate, float brightness,
                       int64_t startTime) {
    kind_ = Kind::Synth;
    handle_ = req.handle;
    instrument_ = req.instrument;
    startTime_ = startTime;
    outputRate_ = outputRate;
    patch_ = patch;
    targetMidi_ = currentMidi_ = req.midi;
    velocity_ = req.velocity;
    duration_ = req.duration > 0.0f ? req.duration : patch->maxSeconds;
    elapsed_ = 0.0f;
    brightnessMul_ = brightness;
    baseGain_ = 0.42f * velocityCurve(req.velocity) * patch->gain;
    reverbSend_ = patch->reverbSend;
    echoSend_ = patch->echoSend;
    phase1_ = 0.0f;
    phase2_ = 0.37f;
    phaseSub_ = 0.0f;
    phaseMod_ = 0.0f;
    vibPhase_ = 0.0f;
    tremPhase_ = 0.0f;  // tremolo começa no volume cheio, sem "buraco" no ataque
    filter_.reset();
    formant_.reset();
    setPan(req.pan);
    env_.configure(patch->attack, patch->decay, patch->sustain, patch->release, outputRate);
    env_.trigger();
}

bool Voice::render(const MixBus& bus, int frames) {
    if (!env_.active()) return false;
    const bool alive = kind_ == Kind::Sample ? renderSample(bus, frames) : renderSynth(bus, frames);
    elapsed_ += static_cast<float>(frames) / outputRate_;
    if (duration_ > 0.0f && elapsed_ >= duration_) {
        env_.release();
        duration_ = 0.0f;
    }
    return alive && env_.active();
}

bool Voice::renderSample(const MixBus& bus, int frames) {
    if (zone_ == nullptr || zone_->frames < 4) {
        env_.stop();
        return false;
    }
    const int16_t* pcm = zone_->pcm.data();
    const int channels = zone_->channels;
    const int last = zone_->frames - 1;
    constexpr float kScale = 1.0f / 32768.0f;
    const float gain = baseGain_;

    for (int offset = 0; offset < frames; offset += kControlBlock) {
        const int n = std::min(kControlBlock, frames - offset);
        currentMidi_ += (targetMidi_ - currentMidi_) * 0.15f;
        step_ = std::exp2((currentMidi_ - zone_->rootMidi) / 12.0f) * (zone_->sampleRate / outputRate_);
        // Toques fracos soam mais macios: passa-baixa de um polo guiado pela velocidade.
        const float cutoff = (1800.0f + 16000.0f * velocity_ * velocity_) * brightnessMul_;
        lpCoef_ = 1.0f - std::exp(-kTwoPi * std::min(cutoff, outputRate_ * 0.45f) / outputRate_);

        for (int i = 0; i < n; ++i) {
            const int idx = static_cast<int>(position_);
            if (idx >= last - 2) {
                // A amostra já termina em silêncio (fade-out no preparo). Um steal() aqui nunca
                // concluiria, pois next() não é mais chamado: a voz ficaria presa para sempre e,
                // com o pool cheio dessas vozes, o motor descartaria todo toque novo.
                env_.stop();
                return false;
            }
            const float t = static_cast<float>(position_ - idx);
            const int i0 = std::max(idx - 1, 0);
            float l, r;
            if (channels == 2) {
                const int16_t* p = pcm;
                l = hermite(p[i0 * 2] * kScale, p[idx * 2] * kScale, p[(idx + 1) * 2] * kScale,
                            p[(idx + 2) * 2] * kScale, t);
                r = hermite(p[i0 * 2 + 1] * kScale, p[idx * 2 + 1] * kScale, p[(idx + 1) * 2 + 1] * kScale,
                            p[(idx + 2) * 2 + 1] * kScale, t);
            } else {
                l = r = hermite(pcm[i0] * kScale, pcm[idx] * kScale, pcm[idx + 1] * kScale, pcm[idx + 2] * kScale,
                                t);
            }
            position_ += step_;
            lpL_ += (l - lpL_) * lpCoef_;
            lpR_ += (r - lpR_) * lpCoef_;
            const float e = env_.next() * gain;
            const float outL = lpL_ * e * panL_ * 1.414f;
            const float outR = lpR_ * e * panR_ * 1.414f;
            const int o = offset + i;
            bus.left[o] += outL;
            bus.right[o] += outR;
            const float mono = 0.5f * (outL + outR);
            bus.reverb[o] += mono * reverbSend_;
            bus.echo[o] += mono * echoSend_;
            if (!env_.active()) return false;
        }
    }
    return true;
}

float Voice::oscillator(float phase, float dt) {
    switch (static_cast<Wave>(static_cast<int>(patch_->wave))) {
        case Wave::Sine:
        case Wave::Bell:
            return std::sin(kTwoPi * phase);
        case Wave::Triangle:
            return 1.0f - 4.0f * std::fabs(phase - 0.5f);
        case Wave::Saw:
        case Wave::Pad:
            return 2.0f * phase - 1.0f - polyBlep(phase, dt);
        case Wave::Square: {
            float v = phase < 0.5f ? 1.0f : -1.0f;
            v += polyBlep(phase, dt);
            float shifted = phase + 0.5f;
            if (shifted >= 1.0f) shifted -= 1.0f;
            v -= polyBlep(shifted, dt);
            return v * 0.7f;
        }
    }
    return 0.0f;
}

bool Voice::renderSynth(const MixBus& bus, int frames) {
    const SynthPatch& p = *patch_;
    const Wave wave = static_cast<Wave>(static_cast<int>(p.wave));
    const bool isPad = wave == Wave::Pad;
    const float detune = isPad && p.detuneCents <= 0.0f ? 9.0f : p.detuneCents;
    const float invRate = 1.0f / outputRate_;
    float t = elapsed_;

    for (int offset = 0; offset < frames; offset += kControlBlock) {
        const int n = std::min(kControlBlock, frames - offset);
        currentMidi_ += (targetMidi_ - currentMidi_) * 0.15f;
        vibPhase_ += p.vibratoRate * n * invRate;
        vibPhase_ -= std::floor(vibPhase_);
        const float vibrato = p.vibratoDepth * std::sin(kTwoPi * vibPhase_) * std::min(1.0f, t * 3.0f);
        const float glide = threePoint(t, p.pitchStart, p.pitchMid, p.pitchMidTime, p.pitchEndTime, 0.0f);
        const float midi = currentMidi_ + 12.0f * p.octave + glide + vibrato;
        const float freq = midiToHz(midi);
        const float dt1 = freq * invRate;
        const float dt2 = dt1 * std::exp2(detune / 1200.0f);
        const float dtSub = dt1 * 0.5f;
        const float dtMod = dt1 * p.fmRatio;
        const float fmIndex = p.fmIndex * std::exp(-t / std::max(p.fmDecay, 0.01f));
        const float envLevel = env_.level();
        const float keyFactor = std::pow(freq / 261.63f, p.keyTrack);
        const float cutoff = p.cutoffHz * keyFactor * std::exp2(p.filterEnv * envLevel) * brightnessMul_;
        filter_.setParams(cutoff, p.resonance, outputRate_);
        const bool useFormant = p.formantMix > 0.0f && p.formantStart > 0.0f;
        if (useFormant) {
            const float half = p.formantTime * 0.5f;
            const float fc = threePoint(t, p.formantStart, p.formantMid, half, p.formantTime, p.formantEnd);
            formant_.setParams(fc, 4.0f, outputRate_);
        }

        for (int i = 0; i < n; ++i) {
            float s;
            if (wave == Wave::Bell) {
                const float mod = std::sin(kTwoPi * phaseMod_) * fmIndex;
                s = std::sin(kTwoPi * phase1_ + mod);
                phaseMod_ += dtMod;
                phaseMod_ -= std::floor(phaseMod_);
            } else {
                s = oscillator(phase1_, dt1);
                if (detune > 0.0f) s = 0.5f * (s + oscillator(phase2_, dt2));
            }
            if (p.subLevel > 0.0f) s += p.subLevel * std::sin(kTwoPi * phaseSub_);
            if (p.noise > 0.0f) s += p.noise * noise_.next();
            phase1_ += dt1;
            phase1_ -= std::floor(phase1_);
            phase2_ += dt2;
            phase2_ -= std::floor(phase2_);
            phaseSub_ += dtSub;
            phaseSub_ -= std::floor(phaseSub_);

            float band;
            float y = filter_.process(s, band);
            if (useFormant) {
                float fBand;
                formant_.process(y, fBand);
                y = y * (1.0f - p.formantMix) + fBand * p.formantMix * 2.2f;
            }
            float amp = env_.next() * baseGain_;
            if (p.tremoloDepth > 0.0f) {
                tremPhase_ += p.tremoloRate * invRate;
                tremPhase_ -= std::floor(tremPhase_);
                amp *= 1.0f - p.tremoloDepth * (0.5f - 0.5f * std::cos(kTwoPi * tremPhase_));
            }
            const float outL = y * amp * panL_ * 1.414f;
            const float outR = y * amp * panR_ * 1.414f;
            const int o = offset + i;
            bus.left[o] += outL;
            bus.right[o] += outR;
            const float mono = 0.5f * (outL + outR);
            bus.reverb[o] += mono * reverbSend_;
            bus.echo[o] += mono * echoSend_;
            if (!env_.active()) return false;
        }
        t += static_cast<float>(n) * invRate;
    }
    return true;
}

}  // namespace sonzinho
