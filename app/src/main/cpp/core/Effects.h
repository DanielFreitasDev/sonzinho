#pragma once

#include <array>
#include <vector>

#include "Dsp.h"

namespace sonzinho {

// Reverb de rede de atraso realimentada (FDN 8x8, matriz de Householder) com amortecimento
// por linha: ambiente macio e denso, sem o "metálico" de reverbs de pente simples.
class Reverb {
public:
    void prepare(float sampleRate) {
        sampleRate_ = sampleRate;
        static constexpr std::array<float, kLines> kMs = {31.7f, 37.3f, 41.9f, 47.1f, 53.3f, 59.9f, 67.1f, 73.7f};
        for (int i = 0; i < kLines; ++i) {
            const int len = static_cast<int>(kMs[i] * 0.001f * sampleRate);
            lines_[i].assign(len, 0.0f);
            pos_[i] = 0;
        }
        preDelay_.assign(static_cast<size_t>(0.018f * sampleRate), 0.0f);
        prePos_ = 0;
        setDecay(2.2f, 5200.0f);
    }

    void setDecay(float t60Seconds, float dampingHz) {
        for (int i = 0; i < kLines; ++i) {
            const float len = static_cast<float>(lines_[i].size());
            feedback_[i] = std::pow(10.0f, -3.0f * len / (t60Seconds * sampleRate_));
            damp_[i] = 0.0f;
        }
        dampCoef_ = 1.0f - std::exp(-kTwoPi * dampingHz / sampleRate_);
        hpCoef_ = 1.0f - std::exp(-kTwoPi * 160.0f / sampleRate_);
    }

    void process(const float* in, float* outL, float* outR, int frames, float wet) {
        for (int n = 0; n < frames; ++n) {
            // Corta graves do envio para o reverb não embolar.
            hpState_ += (in[n] - hpState_) * hpCoef_;
            const float x = in[n] - hpState_;
            float& slot = preDelay_[prePos_];
            const float delayed = slot;
            slot = x;
            if (++prePos_ >= preDelay_.size()) prePos_ = 0;

            std::array<float, kLines> outs{};
            float sum = 0.0f;
            for (int i = 0; i < kLines; ++i) {
                outs[i] = lines_[i][pos_[i]];
                sum += outs[i];
            }
            const float householder = sum * (2.0f / kLines);
            float l = 0.0f, r = 0.0f;
            for (int i = 0; i < kLines; ++i) {
                float v = (outs[i] - householder) * feedback_[i];
                damp_[i] += (v - damp_[i]) * dampCoef_;
                v = damp_[i] + ((i & 1) ? -delayed : delayed) * 0.35f;
                lines_[i][pos_[i]] = v;
                if (++pos_[i] >= static_cast<int>(lines_[i].size())) pos_[i] = 0;
                if (i & 1) {
                    r += outs[i];
                } else {
                    l += outs[i];
                }
            }
            outL[n] += l * wet * 0.5f;
            outR[n] += r * wet * 0.5f;
        }
    }

private:
    static constexpr int kLines = 8;
    float sampleRate_ = 48000.0f;
    std::array<std::vector<float>, kLines> lines_;
    std::array<int, kLines> pos_{};
    std::array<float, kLines> feedback_{};
    std::array<float, kLines> damp_{};
    std::vector<float> preDelay_;
    size_t prePos_ = 0;
    float dampCoef_ = 0.5f;
    float hpCoef_ = 0.02f;
    float hpState_ = 0.0f;
};

// Eco estéreo "pingue-pongue" com perda de agudos a cada repetição.
class Echo {
public:
    void prepare(float sampleRate) {
        const auto len = static_cast<size_t>(0.34f * sampleRate);
        left_.assign(len, 0.0f);
        right_.assign(len, 0.0f);
        pos_ = 0;
        lpCoef_ = 1.0f - std::exp(-kTwoPi * 2800.0f / sampleRate);
    }

    void process(const float* in, float* outL, float* outR, int frames) {
        for (int n = 0; n < frames; ++n) {
            const float dl = left_[pos_];
            const float dr = right_[pos_];
            lpL_ += (dl - lpL_) * lpCoef_;
            lpR_ += (dr - lpR_) * lpCoef_;
            left_[pos_] = in[n] + lpR_ * kFeedback;
            right_[pos_] = lpL_ * kFeedback;
            if (++pos_ >= left_.size()) pos_ = 0;
            outL[n] += dl * 0.6f;
            outR[n] += dr * 0.6f;
        }
    }

private:
    static constexpr float kFeedback = 0.42f;
    std::vector<float> left_, right_;
    size_t pos_ = 0;
    float lpCoef_ = 0.3f;
    float lpL_ = 0.0f, lpR_ = 0.0f;
};

// Compressor estéreo ligado, joelho suave: segura picos de muitos toques sem "bombear".
class Compressor {
public:
    void prepare(float sampleRate) {
        attack_ = onePoleCoef(0.004f, sampleRate);
        release_ = onePoleCoef(0.180f, sampleRate);
    }

    void process(float* left, float* right, int frames) {
        for (int n = 0; n < frames; ++n) {
            const float peak = std::max(std::fabs(left[n]), std::fabs(right[n]));
            const float coef = peak > env_ ? attack_ : release_;
            env_ += (peak - env_) * coef;
            const float g = gainFor(gainToDb(env_));
            left[n] *= g;
            right[n] *= g;
        }
    }

    float currentGain() const { return gainFor(gainToDb(env_)); }

private:
    static float gainFor(float levelDb) {
        constexpr float kThreshold = -14.0f, kRatio = 2.5f, kKnee = 6.0f;
        const float over = levelDb - kThreshold;
        float reductionDb;
        if (over <= -kKnee / 2) {
            reductionDb = 0.0f;
        } else if (over >= kKnee / 2) {
            reductionDb = over * (1.0f - 1.0f / kRatio);
        } else {
            const float x = over + kKnee / 2;
            reductionDb = (1.0f - 1.0f / kRatio) * x * x / (2.0f * kKnee);
        }
        return dbToGain(-reductionDb);
    }

    float attack_ = 0.01f, release_ = 0.001f, env_ = 0.0f;
};

// Limitador "brickwall" com antecipação: retenção do ganho mínimo + média móvel do mesmo
// comprimento garantem que nenhuma amostra ultrapassa o teto (sem clipping, sem estalos).
class Limiter {
public:
    void prepare(float sampleRate, float ceiling) {
        ceiling_ = ceiling;
        length_ = std::max(16, static_cast<int>(0.0015f * sampleRate));
        delayL_.assign(length_, 0.0f);
        delayR_.assign(length_, 0.0f);
        hold_.assign(length_, 1.0f);
        box_.assign(length_, 1.0f);
        boxSum_ = static_cast<double>(length_);
        pos_ = 0;
        smoothed_ = 1.0f;
        releaseCoef_ = onePoleCoef(0.120f, sampleRate);
    }

    int latencyFrames() const { return length_ - 1; }

    void process(float* left, float* right, int frames) {
        for (int n = 0; n < frames; ++n) {
            const float peak = std::max(std::fabs(left[n]), std::fabs(right[n]));
            const float required = peak > ceiling_ ? ceiling_ / peak : 1.0f;
            smoothed_ = required < smoothed_ ? required : smoothed_ + (required - smoothed_) * releaseCoef_;
            hold_[pos_] = smoothed_;
            float minGain = 1.0f;
            for (float h : hold_) minGain = std::min(minGain, h);
            boxSum_ += minGain - box_[pos_];
            box_[pos_] = minGain;
            const float gain = static_cast<float>(boxSum_ / length_);

            // Saída atrasada em (length_ - 1) quadros, alinhada ao ganho.
            const int readPos = (pos_ + 1) % length_;
            const float outL = delayL_[readPos] * gain;
            const float outR = delayR_[readPos] * gain;
            delayL_[pos_] = left[n];
            delayR_[pos_] = right[n];
            pos_ = readPos;
            // Proteção final contra erro numérico residual.
            left[n] = clampf(outL, -ceiling_, ceiling_);
            right[n] = clampf(outR, -ceiling_, ceiling_);
        }
    }

private:
    float ceiling_ = 0.89f;
    int length_ = 64;
    std::vector<float> delayL_, delayR_, hold_, box_;
    double boxSum_ = 64.0;
    int pos_ = 0;
    float smoothed_ = 1.0f;
    float releaseCoef_ = 0.001f;
};

// Remove DC e suaviza levemente os agudos do master (som gentil para ouvidos pequenos).
class ToneShaper {
public:
    void prepare(float sampleRate) {
        hpCoef_ = 1.0f - std::exp(-kTwoPi * 6500.0f / sampleRate);
        dcR_ = 1.0f - (kTwoPi * 18.0f / sampleRate);
    }

    void process(float* left, float* right, int frames) {
        for (int n = 0; n < frames; ++n) {
            left[n] = shape(left[n], dcInL_, dcOutL_, lowL_);
            right[n] = shape(right[n], dcInR_, dcOutR_, lowR_);
        }
    }

private:
    float shape(float x, float& dcIn, float& dcOut, float& low) {
        const float y = x - dcIn + dcR_ * dcOut;
        dcIn = x;
        dcOut = y;
        low += (y - low) * hpCoef_;
        const float high = y - low;
        return y - 0.3f * high;  // prateleira de agudos de aproximadamente -3 dB
    }

    float hpCoef_ = 0.5f, dcR_ = 0.999f;
    float dcInL_ = 0.0f, dcOutL_ = 0.0f, lowL_ = 0.0f;
    float dcInR_ = 0.0f, dcOutR_ = 0.0f, lowR_ = 0.0f;
};

}  // namespace sonzinho
