#pragma once

#include "Dsp.h"

namespace sonzinho {

// ADSR sem cliques: ataque linear com duração mínima, decaimento e liberação exponenciais
// a partir do nível atual (nunca salta), e uma rampa curta dedicada ao "voice stealing".
class Envelope {
public:
    enum class Stage { Idle, Attack, Decay, Sustain, Release, Steal };

    static constexpr float kMinAttackSeconds = 0.0015f;
    static constexpr float kStealSeconds = 0.008f;
    static constexpr float kSilence = 1.0e-4f;

    void configure(float attack, float decay, float sustain, float release, float sampleRate) {
        sampleRate_ = sampleRate;
        attackStep_ = 1.0f / (std::max(attack, kMinAttackSeconds) * sampleRate);
        decayCoef_ = onePoleCoef(std::max(decay, 0.005f) / 4.0f, sampleRate);
        sustain_ = clampf(sustain, 0.0f, 1.0f);
        releaseCoef_ = onePoleCoef(std::max(release, 0.01f) / 4.0f, sampleRate);
    }

    void trigger() {
        stage_ = Stage::Attack;  // parte do nível atual: retrigger não estala
    }

    void release() {
        if (stage_ != Stage::Idle && stage_ != Stage::Steal) stage_ = Stage::Release;
    }

    void steal() {
        if (stage_ == Stage::Idle) return;
        stage_ = Stage::Steal;
        stealStep_ = std::max(level_, kSilence) / (kStealSeconds * sampleRate_);
    }

    // Encerra na hora: para quando o próprio som já acabou (fim da amostra, que termina em fade).
    void stop() { finish(); }

    float next() {
        switch (stage_) {
            case Stage::Idle:
                return 0.0f;
            case Stage::Attack:
                level_ += attackStep_;
                if (level_ >= 1.0f) {
                    level_ = 1.0f;
                    stage_ = Stage::Decay;
                }
                break;
            case Stage::Decay:
                level_ += (sustain_ - level_) * decayCoef_;
                if (std::fabs(level_ - sustain_) < 1.0e-4f) {
                    level_ = sustain_;
                    stage_ = Stage::Sustain;
                }
                if (level_ < kSilence) finish();
                break;
            case Stage::Sustain:
                if (sustain_ <= kSilence) finish();
                break;
            case Stage::Release:
                level_ -= level_ * releaseCoef_;
                if (level_ < kSilence) finish();
                break;
            case Stage::Steal:
                level_ -= stealStep_;
                if (level_ <= 0.0f) finish();
                break;
        }
        return level_;
    }

    Stage stage() const { return stage_; }
    float level() const { return level_; }
    bool active() const { return stage_ != Stage::Idle; }
    bool releasing() const { return stage_ == Stage::Release || stage_ == Stage::Steal; }

private:
    void finish() {
        level_ = 0.0f;
        stage_ = Stage::Idle;
    }

    Stage stage_ = Stage::Idle;
    float level_ = 0.0f;
    float sampleRate_ = 48000.0f;
    float attackStep_ = 0.01f;
    float decayCoef_ = 0.001f;
    float sustain_ = 1.0f;
    float releaseCoef_ = 0.001f;
    float stealStep_ = 0.01f;
};

}  // namespace sonzinho
