#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include "CommandQueue.h"
#include "Effects.h"
#include "Sequencer.h"
#include "SynthPatch.h"
#include "Voice.h"

namespace sonzinho {

// Identificadores de instrumento: espelhados em `InstrumentId.kt`.
namespace instrument {
constexpr int kSampledCount = 16;     // 0..15: instrumentos multi-sampleados
constexpr int kFirstSynth = 16;       // 16..31: timbres sintetizados de fábrica
constexpr int kFirstAnimal = 32;      // 32..47: vozes de bichinhos
constexpr int kFirstCustom = 64;      // 64..127: sons criados pelos pais
constexpr int kCount = 128;
constexpr int kFallback = 18;         // sino suave usado enquanto as amostras carregam
}  // namespace instrument

struct EngineConfig {
    int maxPolyphony = 24;   // vozes audíveis simultâneas antes de roubar
    float masterVolume = 0.5f;
    float reverbWet = 0.9f;
    float brightness = 1.0f;  // teto de brilho global (área dos pais)
};

class Engine {
public:
    static constexpr int kVoicePool = 48;      // inclui folga para vozes em fade de roubo
    static constexpr int kMaxBlock = 4096;
    static constexpr float kCeiling = 0.89f;   // -1 dBFS
    static constexpr double kRetriggerGuardSeconds = 0.028;

    Engine();
    ~Engine();

    // ---- Chamado por qualquer thread (UI, IO, corrotinas) ----
    int32_t noteOn(int instrument, float midi, float velocity, float pan, float duration);
    void noteOff(int32_t handle);
    void setVoiceTarget(int32_t handle, float midi, float brightness);
    void allNotesOff();
    void definePatch(int slot, const float* params, int count);
    void setConfig(const EngineConfig& config);
    void publishInstrument(int slot, std::unique_ptr<SampledInstrument> inst);
    void setSequence(std::unique_ptr<Sequence> sequence);
    void playSequence(bool fromStart);
    void stopSequence();
    void setMuted(bool muted);
    void collectGarbage();

    bool isInstrumentReady(int slot) const;
    double sequencePosition() const { return seqPosition_.load(std::memory_order_relaxed); }
    bool sequencePlaying() const { return seqPlaying_.load(std::memory_order_relaxed); }
    int activeVoices() const { return activeVoices_.load(std::memory_order_relaxed); }

    // ---- Thread de áudio ----
    void prepare(float sampleRate);
    void render(float* interleavedStereo, int frames);
    float sampleRate() const { return sampleRate_; }

private:
    enum class CommandType : uint8_t {
        NoteOn, NoteOff, SetTarget, AllOff, Patch, Config, Instrument, SetSequence, Play, Stop, Mute
    };
    struct Command {
        CommandType type = CommandType::NoteOn;
        int32_t handle = 0;
        int instrument = 0;
        float a = 0, b = 0, c = 0, d = 0;
        void* pointer = nullptr;
        std::array<float, kPatchParamCount> patch{};
    };
    struct Retired {
        enum class Kind : uint8_t { Instrument, Sequence } kind = Kind::Instrument;
        void* pointer = nullptr;
    };

    void push(const Command& cmd);
    void handle(const Command& cmd);
    void startNote(const NoteRequest& req);
    void renderVoices(int offset, int frames);
    Voice* allocateVoice();

    float sampleRate_ = 48000.0f;
    EngineConfig config_{};
    std::array<Voice, kVoicePool> voices_{};
    std::array<SynthPatch, instrument::kCount> patches_{};
    std::array<SampledInstrument*, instrument::kSampledCount> instruments_{};
    std::array<std::atomic<bool>, instrument::kSampledCount> instrumentReady_{};

    CommandQueue<Command, 1024> commands_;
    SpscQueue<Retired, 256> retired_;
    std::mutex garbageMutex_;
    std::atomic<int32_t> nextHandle_{1};

    SequencePlayer player_;
    std::atomic<double> seqPosition_{0.0};
    std::atomic<bool> seqPlaying_{false};
    std::atomic<int> activeVoices_{0};

    Reverb reverb_;
    Echo echo_;
    Compressor compressor_;
    Limiter limiter_;
    ToneShaper tone_;
    std::vector<float> left_, right_, reverbBus_, echoBus_, wetL_, wetR_;
    int64_t time_ = 0;  // quadros desde o início
    float voiceGain_ = 1.0f;
    float masterGain_ = 0.0f;  // começa em 0 e sobe suavemente (sem estalo ao abrir o stream)
    bool muted_ = false;
};

}  // namespace sonzinho
