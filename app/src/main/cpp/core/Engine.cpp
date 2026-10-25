#include "Engine.h"

#include <cmath>
#include <cstring>

#include "BuiltinPatches.h"

namespace sonzinho {

Engine::Engine() {
    loadBuiltinPatches(patches_);
    for (auto& ready : instrumentReady_) ready.store(false);
    left_.assign(kMaxBlock, 0.0f);
    right_.assign(kMaxBlock, 0.0f);
    reverbBus_.assign(kMaxBlock, 0.0f);
    echoBus_.assign(kMaxBlock, 0.0f);
    wetL_.assign(kMaxBlock, 0.0f);
    wetR_.assign(kMaxBlock, 0.0f);
    prepare(48000.0f);
}

Engine::~Engine() {
    collectGarbage();
    for (auto* inst : instruments_) delete inst;
    if (player_.sequence() != nullptr) delete player_.sequence();
}

void Engine::prepare(float sampleRate) {
    sampleRate_ = sampleRate;
    reverb_.prepare(sampleRate);
    echo_.prepare(sampleRate);
    compressor_.prepare(sampleRate);
    limiter_.prepare(sampleRate, kCeiling);
    tone_.prepare(sampleRate);
}

// ---------------------------------------------------------------- produtores

void Engine::push(const Command& cmd) { commands_.push(cmd); }

int32_t Engine::noteOn(int inst, float midi, float velocity, float pan, float duration) {
    int32_t handle = nextHandle_.fetch_add(1, std::memory_order_relaxed);
    if (handle <= 0) {
        nextHandle_.store(2);
        handle = 1;
    }
    Command cmd;
    cmd.type = CommandType::NoteOn;
    cmd.handle = handle;
    cmd.instrument = inst;
    cmd.a = midi;
    cmd.b = velocity;
    cmd.c = pan;
    cmd.d = duration;
    push(cmd);
    return handle;
}

void Engine::noteOff(int32_t handle) {
    Command cmd;
    cmd.type = CommandType::NoteOff;
    cmd.handle = handle;
    push(cmd);
}

void Engine::setVoiceTarget(int32_t handle, float midi, float brightness) {
    Command cmd;
    cmd.type = CommandType::SetTarget;
    cmd.handle = handle;
    cmd.a = midi;
    cmd.b = brightness;
    push(cmd);
}

void Engine::allNotesOff() {
    Command cmd;
    cmd.type = CommandType::AllOff;
    push(cmd);
}

void Engine::definePatch(int slot, const float* params, int count) {
    if (slot < 0 || slot >= instrument::kCount) return;
    Command cmd;
    cmd.type = CommandType::Patch;
    cmd.instrument = slot;
    const int n = std::min(count, static_cast<int>(kPatchParamCount));
    SynthPatch defaults;
    std::memcpy(cmd.patch.data(), &defaults, sizeof(SynthPatch));
    for (int i = 0; i < n; ++i) cmd.patch[i] = params[i];
    push(cmd);
}

void Engine::setConfig(const EngineConfig& config) {
    Command cmd;
    cmd.type = CommandType::Config;
    cmd.instrument = std::max(4, std::min(config.maxPolyphony, kVoicePool - 8));
    cmd.a = clampf(config.masterVolume, 0.0f, 1.0f);
    cmd.b = clampf(config.reverbWet, 0.0f, 2.0f);
    cmd.c = clampf(config.brightness, 0.2f, 1.5f);
    push(cmd);
}

void Engine::publishInstrument(int slot, std::unique_ptr<SampledInstrument> inst) {
    if (slot < 0 || slot >= instrument::kSampledCount) return;
    Command cmd;
    cmd.type = CommandType::Instrument;
    cmd.instrument = slot;
    cmd.pointer = inst.release();
    push(cmd);
    instrumentReady_[slot].store(true);
}

void Engine::setSequence(std::unique_ptr<Sequence> sequence) {
    collectGarbage();
    Command cmd;
    cmd.type = CommandType::SetSequence;
    cmd.pointer = sequence.release();
    push(cmd);
}

void Engine::playSequence(bool fromStart) {
    Command cmd;
    cmd.type = CommandType::Play;
    cmd.a = fromStart ? 1.0f : 0.0f;
    push(cmd);
}

void Engine::stopSequence() {
    Command cmd;
    cmd.type = CommandType::Stop;
    push(cmd);
}

void Engine::setMuted(bool muted) {
    Command cmd;
    cmd.type = CommandType::Mute;
    cmd.a = muted ? 1.0f : 0.0f;
    push(cmd);
}

void Engine::collectGarbage() {
    std::lock_guard<std::mutex> lock(garbageMutex_);
    Retired item;
    while (retired_.pop(item)) {
        if (item.kind == Retired::Kind::Instrument) {
            delete static_cast<SampledInstrument*>(item.pointer);
        } else {
            delete static_cast<Sequence*>(item.pointer);
        }
    }
}

bool Engine::isInstrumentReady(int slot) const {
    if (slot < 0 || slot >= instrument::kSampledCount) return true;
    return instrumentReady_[slot].load();
}

// ---------------------------------------------------------------- thread de áudio

void Engine::handle(const Command& cmd) {
    switch (cmd.type) {
        case CommandType::NoteOn: {
            NoteRequest req;
            req.handle = cmd.handle;
            req.instrument = cmd.instrument;
            req.midi = cmd.a;
            req.velocity = cmd.b;
            req.pan = cmd.c;
            req.duration = cmd.d;
            startNote(req);
            break;
        }
        case CommandType::NoteOff:
            for (auto& v : voices_) {
                if (v.active() && v.handle() == cmd.handle) v.release();
            }
            break;
        case CommandType::SetTarget:
            for (auto& v : voices_) {
                if (v.active() && v.handle() == cmd.handle) v.setTarget(cmd.a, cmd.b * config_.brightness);
            }
            break;
        case CommandType::AllOff:
            for (auto& v : voices_) v.release();
            break;
        case CommandType::Patch:
            std::memcpy(static_cast<void*>(&patches_[cmd.instrument]), cmd.patch.data(), sizeof(SynthPatch));
            break;
        case CommandType::Config:
            config_.maxPolyphony = cmd.instrument;
            config_.masterVolume = cmd.a;
            config_.reverbWet = cmd.b;
            config_.brightness = cmd.c;
            break;
        case CommandType::Instrument: {
            SampledInstrument*& slot = instruments_[cmd.instrument];
            if (slot == nullptr) {
                slot = static_cast<SampledInstrument*>(cmd.pointer);
            } else {
                // Instrumentos são carregados uma única vez: vozes podem apontar para as zonas
                // publicadas, então uma segunda cópia é simplesmente aposentada.
                retired_.push({Retired::Kind::Instrument, cmd.pointer});
            }
            break;
        }
        case CommandType::SetSequence: {
            const Sequence* old = player_.setSequence(static_cast<const Sequence*>(cmd.pointer));
            if (old != nullptr) retired_.push({Retired::Kind::Sequence, const_cast<Sequence*>(old)});
            break;
        }
        case CommandType::Play:
            player_.play(cmd.a > 0.5f);
            break;
        case CommandType::Stop:
            player_.stop();
            break;
        case CommandType::Mute:
            muted_ = cmd.a > 0.5f;
            if (muted_) {
                for (auto& v : voices_) v.release();
            }
            break;
    }
}

Voice* Engine::allocateVoice() {
    for (auto& v : voices_) {
        if (!v.active()) return &v;
    }
    return nullptr;
}

void Engine::startNote(const NoteRequest& req) {
    if (muted_) return;
    if (req.instrument < 0 || req.instrument >= instrument::kCount) return;
    const auto guard = static_cast<int64_t>(kRetriggerGuardSeconds * sampleRate_);

    int audible = 0;
    Voice* releasingVictim = nullptr;
    Voice* oldestVictim = nullptr;
    for (auto& v : voices_) {
        if (!v.active() || v.stealing()) continue;
        // A mão inteira na tela gera vários toques na mesma nota quase ao mesmo tempo:
        // um único ataque soa mais bonito (e não estoura o volume).
        if (v.instrument() == req.instrument && std::fabs(v.midi() - req.midi) < 0.01f &&
            time_ - v.startTime() < guard && !v.releasing()) {
            return;
        }
        ++audible;
        if (v.releasing()) {
            if (releasingVictim == nullptr || v.level() < releasingVictim->level()) releasingVictim = &v;
        } else if (oldestVictim == nullptr || v.startTime() < oldestVictim->startTime()) {
            oldestVictim = &v;
        }
    }
    if (audible >= config_.maxPolyphony) {
        Voice* victim = releasingVictim != nullptr ? releasingVictim : oldestVictim;
        if (victim != nullptr) victim->steal();  // fade de 8 ms: sem clique
    }

    Voice* voice = allocateVoice();
    if (voice == nullptr) return;  // todas as vozes em fade de roubo: descarta esta nota

    const float brightness = config_.brightness;
    if (req.instrument < instrument::kSampledCount) {
        const SampledInstrument* inst = instruments_[req.instrument];
        if (inst != nullptr && !inst->zones.empty()) {
            voice->startSample(req, inst, sampleRate_, brightness, time_);
            return;
        }
        // Amostras ainda carregando: um sino suave garante que todo toque soe.
        NoteRequest fallback = req;
        fallback.instrument = instrument::kFallback;
        voice->startSynth(fallback, &patches_[instrument::kFallback], sampleRate_, brightness, time_);
        return;
    }
    voice->startSynth(req, &patches_[req.instrument], sampleRate_, brightness, time_);
}

void Engine::renderVoices(int offset, int frames) {
    const MixBus bus{left_.data() + offset, right_.data() + offset, reverbBus_.data() + offset,
                     echoBus_.data() + offset};
    for (auto& v : voices_) {
        if (v.active()) v.render(bus, frames);
    }
}

void Engine::render(float* out, int totalFrames) {
    int written = 0;
    while (written < totalFrames) {
        const int frames = std::min(totalFrames - written, kMaxBlock);
        Command cmd;
        while (commands_.pop(cmd)) handle(cmd);

        std::fill_n(left_.begin(), frames, 0.0f);
        std::fill_n(right_.begin(), frames, 0.0f);
        std::fill_n(reverbBus_.begin(), frames, 0.0f);
        std::fill_n(echoBus_.begin(), frames, 0.0f);

        // Divide o bloco nos instantes exatos dos eventos da sequência.
        int done = 0;
        int dueSpins = 0;
        while (done < frames) {
            while (const SequenceEvent* e = player_.popDue(sampleRate_)) {
                NoteRequest req;
                req.handle = 0;
                req.instrument = e->instrument;
                req.midi = e->midi;
                req.velocity = e->velocity;
                req.duration = e->duration;
                startNote(req);
            }
            int untilNext = player_.framesUntilNext(sampleRate_);
            if (untilNext == 0) {
                // Fim de ciclo recém-tratado: há eventos no início do próximo ciclo.
                if (++dueSpins < 64) continue;
                untilNext = 1;
            }
            int chunk = frames - done;
            if (untilNext > 0 && untilNext < chunk) chunk = untilNext;
            renderVoices(done, chunk);
            player_.advance(chunk, sampleRate_);
            time_ += chunk;
            done += chunk;
            dueSpins = 0;
        }

        int active = 0;
        for (const auto& v : voices_) active += v.active() ? 1 : 0;
        activeVoices_.store(active, std::memory_order_relaxed);
        seqPosition_.store(player_.position(), std::memory_order_relaxed);
        seqPlaying_.store(player_.playing(), std::memory_order_relaxed);

        // Ganho por densidade: muitas vozes juntas são atenuadas suavemente antes do compressor.
        const float targetVoiceGain = 1.0f / std::sqrt(std::max(1.0f, active / 3.0f));
        const float voiceStep = (targetVoiceGain - voiceGain_) / frames * 0.25f;
        float vg = voiceGain_;
        for (int n = 0; n < frames; ++n) {
            vg += voiceStep;
            left_[n] *= vg;
            right_[n] *= vg;
            reverbBus_[n] *= vg;
            echoBus_[n] *= vg;
        }
        voiceGain_ = vg;

        echo_.process(echoBus_.data(), left_.data(), right_.data(), frames);
        reverb_.process(reverbBus_.data(), left_.data(), right_.data(), frames, config_.reverbWet);
        compressor_.process(left_.data(), right_.data(), frames);
        tone_.process(left_.data(), right_.data(), frames);
        limiter_.process(left_.data(), right_.data(), frames);

        const float target = muted_ ? 0.0f : config_.masterVolume;
        const float step = (target - masterGain_) / frames;
        float g = masterGain_;
        float* dst = out + written * 2;
        for (int n = 0; n < frames; ++n) {
            g += step;
            dst[n * 2] = left_[n] * g;
            dst[n * 2 + 1] = right_[n] * g;
        }
        masterGain_ = target;
        written += frames;
    }
}

}  // namespace sonzinho
