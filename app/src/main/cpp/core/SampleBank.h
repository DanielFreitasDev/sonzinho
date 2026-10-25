#pragma once

#include <cstdint>
#include <memory>
#include <vector>

namespace sonzinho {

// Uma amostra decodificada (PCM 16 bits intercalado) e sua altura real medida.
struct SampleZone {
    float rootMidi = 60.0f;
    float sampleRate = 48000.0f;
    int channels = 2;
    int frames = 0;
    std::vector<int16_t> pcm;
};

// Instrumento multi-sampleado: zonas ordenadas por raiz. Imutável depois de publicado
// para a thread de áudio.
struct SampledInstrument {
    std::vector<SampleZone> zones;
    float releaseSeconds = 0.35f;
    float gain = 1.0f;

    const SampleZone* zoneFor(float midi) const {
        if (zones.empty()) return nullptr;
        const SampleZone* best = &zones.front();
        float bestDistance = 1.0e9f;
        for (const auto& z : zones) {
            // Preferir transpor para baixo (soa mais natural que acelerar a amostra).
            const float d = midi - z.rootMidi;
            const float cost = d >= 0.0f ? d * 1.15f : -d;
            if (cost < bestDistance) {
                bestDistance = cost;
                best = &z;
            }
        }
        return best;
    }
};

}  // namespace sonzinho
