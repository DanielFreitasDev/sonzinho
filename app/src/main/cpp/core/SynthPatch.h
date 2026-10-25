#pragma once

#include <array>
#include <cstddef>

namespace sonzinho {

enum class Wave : int { Sine = 0, Triangle = 1, Saw = 2, Square = 3, Bell = 4, Pad = 5 };

// Parâmetros de um timbre sintetizado. A ordem dos campos é o contrato com o Kotlin
// (`SynthPatchCodec`): o patch trafega como um vetor de floats nesta ordem.
struct SynthPatch {
    float wave = 0;             // Wave
    float octave = 0;           // deslocamento em oitavas
    float detuneCents = 0;      // segundo oscilador desafinado (0 = desligado)
    float subLevel = 0;         // oscilador uma oitava abaixo (grave quente)
    float cutoffHz = 3000;      // brilho base do filtro passa-baixa
    float resonance = 0.7f;     // Q do filtro
    float filterEnv = 0;        // quanto o envelope abre o filtro (oitavas)
    float keyTrack = 0.5f;      // quanto o filtro acompanha a nota
    float attack = 0.005f;
    float decay = 0.4f;
    float sustain = 0.6f;
    float release = 0.5f;
    float vibratoRate = 5;      // Hz
    float vibratoDepth = 0;     // semitons
    float tremoloRate = 0;      // Hz
    float tremoloDepth = 0;     // 0..1
    float pitchStart = 0;       // semitons no início
    float pitchMid = 0;         // semitons no meio do glide
    float pitchMidTime = 0;     // segundos até o meio
    float pitchEndTime = 0;     // segundos até voltar a 0 (0 = sem glide)
    float formantStart = 0;     // Hz do passa-banda (0 = sem formante)
    float formantMid = 0;
    float formantEnd = 0;
    float formantTime = 0.3f;   // segundos para percorrer início→meio→fim
    float formantMix = 0;       // 0..1
    float fmRatio = 3.5f;       // usado em Bell
    float fmIndex = 2;          // usado em Bell
    float fmDecay = 0.6f;       // segundos de decaimento do índice FM
    float noise = 0;            // sopro
    float gain = 0.8f;
    float echoSend = 0;
    float reverbSend = 0.25f;
    float maxSeconds = 0;       // duração máxima de uma nota disparada sem note-off (0 = ilimitada)
};

constexpr size_t kPatchParamCount = sizeof(SynthPatch) / sizeof(float);
static_assert(kPatchParamCount == 33, "Atualize SynthPatchCodec no Kotlin ao mudar o patch");

}  // namespace sonzinho
