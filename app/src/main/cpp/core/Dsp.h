#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace sonzinho {

constexpr float kPi = 3.14159265358979323846f;
constexpr float kTwoPi = 2.0f * kPi;

inline float clampf(float v, float lo, float hi) { return std::min(hi, std::max(lo, v)); }

inline float dbToGain(float db) { return std::pow(10.0f, db / 20.0f); }

inline float gainToDb(float g) { return 20.0f * std::log10(std::max(g, 1e-9f)); }

inline float midiToHz(float midi) { return 440.0f * std::exp2((midi - 69.0f) / 12.0f); }

// Coeficiente de um filtro de um polo que percorre ~63% do caminho em `seconds`.
inline float onePoleCoef(float seconds, float sampleRate) {
    if (seconds <= 0.0f) return 1.0f;
    return 1.0f - std::exp(-1.0f / (seconds * sampleRate));
}

// Interpolação cúbica de Hermite (4 pontos), boa para reamostrar instrumentos sem aliasing audível.
inline float hermite(float xm1, float x0, float x1, float x2, float t) {
    const float c1 = 0.5f * (x1 - xm1);
    const float c2 = xm1 - 2.5f * x0 + 2.0f * x1 - 0.5f * x2;
    const float c3 = 0.5f * (x2 - xm1) + 1.5f * (x0 - x1);
    return ((c3 * t + c2) * t + c1) * t + x0;
}

// PolyBLEP: suaviza as descontinuidades de serra/quadrada para evitar aliasing áspero.
inline float polyBlep(float t, float dt) {
    if (t < dt) {
        t /= dt;
        return t + t - t * t - 1.0f;
    }
    if (t > 1.0f - dt) {
        t = (t - 1.0f) / dt;
        return t * t + t + t + 1.0f;
    }
    return 0.0f;
}

// Filtro de variáveis de estado TPT (Zavalishin/Cytomic): estável com modulação rápida.
class Svf {
public:
    void setParams(float cutoffHz, float q, float sampleRate) {
        const float fc = clampf(cutoffHz, 20.0f, sampleRate * 0.45f);
        g_ = std::tan(kPi * fc / sampleRate);
        k_ = 1.0f / std::max(q, 0.3f);
        a1_ = 1.0f / (1.0f + g_ * (g_ + k_));
        a2_ = g_ * a1_;
        a3_ = g_ * a2_;
    }
    void reset() { ic1_ = ic2_ = 0.0f; }
    // Processa uma amostra; devolve passa-baixa e escreve passa-banda em `band`.
    float process(float v0, float& band) {
        const float v3 = v0 - ic2_;
        const float v1 = a1_ * ic1_ + a2_ * v3;
        const float v2 = ic2_ + a2_ * ic1_ + a3_ * v3;
        ic1_ = 2.0f * v1 - ic1_;
        ic2_ = 2.0f * v2 - ic2_;
        band = v1;
        return v2;
    }
    float lowpass(float v0) {
        float band;
        return process(v0, band);
    }

private:
    float g_ = 0.0f, k_ = 1.0f, a1_ = 0.0f, a2_ = 0.0f, a3_ = 0.0f;
    float ic1_ = 0.0f, ic2_ = 0.0f;
};

// Gerador de ruído barato e determinístico (xorshift), sem alocação.
class Noise {
public:
    float next() {
        state_ ^= state_ << 13;
        state_ ^= state_ >> 17;
        state_ ^= state_ << 5;
        return static_cast<float>(state_) * (1.0f / 2147483648.0f) - 1.0f;
    }

private:
    uint32_t state_ = 0x9E3779B9u;
};

}  // namespace sonzinho
