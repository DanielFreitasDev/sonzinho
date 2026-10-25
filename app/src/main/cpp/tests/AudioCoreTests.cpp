// Testes do núcleo de áudio, sem dependências externas. Executados por `./gradlew test`
// (tarefa nativeHostTest) ou diretamente: cmake -S tests -B build && cmake --build build && build/audio_core_tests
#include <cmath>
#include <cstdio>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "Effects.h"
#include "Engine.h"
#include "Envelope.h"
#include "Sequencer.h"

using namespace sonzinho;

namespace {

int failures = 0;
int checks = 0;

#define CHECK(cond)                                                              \
    do {                                                                         \
        ++checks;                                                                \
        if (!(cond)) {                                                           \
            ++failures;                                                          \
            std::printf("  FALHOU %s:%d: %s\n", __FILE__, __LINE__, #cond);      \
        }                                                                        \
    } while (0)

constexpr float kRate = 48000.0f;

std::unique_ptr<SampledInstrument> sineInstrument(float rootMidi = 69.0f, float seconds = 2.0f) {
    auto inst = std::make_unique<SampledInstrument>();
    SampleZone zone;
    zone.rootMidi = rootMidi;
    zone.sampleRate = kRate;
    zone.channels = 2;
    zone.frames = static_cast<int>(seconds * kRate);
    zone.pcm.resize(zone.frames * 2);
    const float f = midiToHz(rootMidi);
    for (int i = 0; i < zone.frames; ++i) {
        float a = 0.5f * std::sin(kTwoPi * f * i / kRate);
        a *= std::min(1.0f, i / 48.0f);                                  // fade-in de 1 ms
        a *= std::exp(-static_cast<float>(i) / (0.8f * kRate));          // decaimento natural
        const auto s = static_cast<int16_t>(a * 32767.0f);
        zone.pcm[i * 2] = s;
        zone.pcm[i * 2 + 1] = s;
    }
    inst->zones.push_back(std::move(zone));
    return inst;
}

struct Rendered {
    std::vector<float> left, right;
    float peak() const {
        float p = 0;
        for (size_t i = 0; i < left.size(); ++i) p = std::max({p, std::fabs(left[i]), std::fabs(right[i])});
        return p;
    }
    bool finite() const {
        for (size_t i = 0; i < left.size(); ++i) {
            if (!std::isfinite(left[i]) || !std::isfinite(right[i])) return false;
        }
        return true;
    }
    // Maior salto entre amostras consecutivas: estalos aparecem como saltos enormes.
    float maxDelta() const {
        float d = 0;
        for (size_t i = 1; i < left.size(); ++i) d = std::max(d, std::fabs(left[i] - left[i - 1]));
        return d;
    }
};

void render(Engine& engine, Rendered& out, int frames, int block = 192) {
    std::vector<float> buf(block * 2);
    for (int done = 0; done < frames; done += block) {
        const int n = std::min(block, frames - done);
        engine.render(buf.data(), n);
        for (int i = 0; i < n; ++i) {
            out.left.push_back(buf[i * 2]);
            out.right.push_back(buf[i * 2 + 1]);
        }
    }
}

EngineConfig dryConfig(float volume = 1.0f) {
    EngineConfig c;
    c.masterVolume = volume;
    c.reverbWet = 0.0f;
    c.maxPolyphony = 24;
    return c;
}

void testEnvelopeIsContinuous() {
    Envelope env;
    env.configure(0.0f, 0.2f, 0.5f, 0.3f, kRate);
    env.trigger();
    float prev = 0.0f, maxStep = 0.0f;
    for (int i = 0; i < 4800; ++i) {
        const float v = env.next();
        maxStep = std::max(maxStep, std::fabs(v - prev));
        prev = v;
    }
    env.release();
    for (int i = 0; i < 48000 && env.active(); ++i) {
        const float v = env.next();
        maxStep = std::max(maxStep, std::fabs(v - prev));
        prev = v;
    }
    // Ataque mínimo de 1,5 ms: nenhum passo maior que 1/72 do nível total.
    CHECK(maxStep <= 1.0f / (Envelope::kMinAttackSeconds * kRate) + 1e-6f);
    CHECK(!env.active());
}

void testStealRampIsShortAndSmooth() {
    Envelope env;
    env.configure(0.001f, 1.0f, 1.0f, 1.0f, kRate);
    env.trigger();
    for (int i = 0; i < 2000; ++i) env.next();
    env.steal();
    int frames = 0;
    float prev = env.level(), maxStep = 0;
    while (env.active() && frames < 10000) {
        const float v = env.next();
        maxStep = std::max(maxStep, std::fabs(v - prev));
        prev = v;
        ++frames;
    }
    CHECK(frames <= static_cast<int>(Envelope::kStealSeconds * kRate) + 2);
    CHECK(maxStep < 0.01f);
}

void testRetriggerGuardMergesPalmHits() {
    Engine engine;
    engine.setConfig(dryConfig());
    engine.publishInstrument(0, sineInstrument());
    for (int i = 0; i < 10; ++i) engine.noteOn(0, 69, 0.8f, 0, 0);
    Rendered r;
    render(engine, r, 480);
    CHECK(engine.activeVoices() == 1);
}

void testVoiceLimitWithDozensOfTouches() {
    Engine engine;
    EngineConfig cfg = dryConfig();
    cfg.maxPolyphony = 16;
    engine.setConfig(cfg);
    engine.publishInstrument(0, sineInstrument());
    Rendered r;
    for (int wave = 0; wave < 6; ++wave) {
        for (int i = 0; i < 40; ++i) engine.noteOn(0, 48.0f + i, 1.0f, (i % 5) / 2.5f - 1.0f, 0);
        render(engine, r, 2400);
        // As vozes em fade de roubo somem em 8 ms: depois disso só restam as audíveis.
        CHECK(engine.activeVoices() <= 16);
    }
    CHECK(r.finite());
    CHECK(r.peak() <= Engine::kCeiling + 1e-4f);
}

void testNoClickWhenStealing() {
    Engine engine;
    EngineConfig cfg = dryConfig();
    cfg.maxPolyphony = 4;
    engine.setConfig(cfg);
    engine.publishInstrument(0, sineInstrument(57.0f));
    Rendered r;
    render(engine, r, 4800);  // deixa o ganho mestre subir
    for (int i = 0; i < 64; ++i) {
        engine.noteOn(0, 57.0f, 1.0f, 0, 0.0f);
        engine.noteOn(0, 45.0f + (i % 12), 1.0f, 0, 0.0f);
        render(engine, r, 600);
    }
    // Duas senoides graves somadas a ~220 Hz: o maior passo natural fica bem abaixo de 0,1.
    CHECK(r.maxDelta() < 0.12f);
    CHECK(r.peak() <= Engine::kCeiling + 1e-4f);
}

void testLimiterNeverClips() {
    Limiter limiter;
    limiter.prepare(kRate, 0.89f);
    std::vector<float> l(48000), rr(48000);
    for (int i = 0; i < 48000; ++i) {
        l[i] = 6.0f * std::sin(kTwoPi * 330.0f * i / kRate) * (i % 9000 < 300 ? 3.0f : 1.0f);
        rr[i] = -l[i];
    }
    limiter.process(l.data(), rr.data(), 48000);
    float peak = 0;
    for (int i = 0; i < 48000; ++i) peak = std::max({peak, std::fabs(l[i]), std::fabs(rr[i])});
    CHECK(peak <= 0.89f + 1e-5f);
}

void testMasterVolumeCap() {
    Engine engine;
    engine.setConfig(dryConfig(0.3f));
    engine.publishInstrument(0, sineInstrument());
    Rendered r;
    for (int i = 0; i < 30; ++i) engine.noteOn(0, 50.0f + i, 1.0f, 0, 0);
    render(engine, r, 48000);
    CHECK(r.peak() <= 0.3f * Engine::kCeiling + 1e-4f);
}

void testFallbackSoundsBeforeSamplesLoad() {
    Engine engine;
    engine.setConfig(dryConfig());
    engine.noteOn(3, 72, 0.8f, 0, 0);  // instrumento 3 ainda não carregado
    Rendered r;
    render(engine, r, 9600);
    CHECK(r.peak() > 0.01f);
}

void testAllPatchesAreSafe() {
    for (int inst = 16; inst < 128; ++inst) {
        Engine engine;
        engine.setConfig(dryConfig());
        engine.noteOn(inst, 36, 1.0f, 0, 0.3f);
        engine.noteOn(inst, 60, 1.0f, -1, 0.3f);
        engine.noteOn(inst, 96, 1.0f, 1, 0.3f);
        Rendered r;
        render(engine, r, 6 * 48000);
        CHECK(r.finite());
        CHECK(r.peak() <= Engine::kCeiling + 1e-4f);
        if (engine.activeVoices() != 0) std::printf("  timbre %d ainda soando após 6 s\n", inst);
        // Todas as vozes terminam sozinhas (notas sem note-off não ficam presas).
        CHECK(engine.activeVoices() == 0);
    }
}

void testSequencePlayerIsSampleAccurate() {
    Sequence seq;
    seq.id = 7;
    seq.length = 2.0;
    seq.loop = true;
    for (int i = 0; i < 8; ++i) seq.events.push_back({i * 0.25, 0, 60.0f + i, 0.8f, 0.2f});
    SequencePlayer player;
    player.setSequence(&seq);
    player.play(true);
    std::vector<long> fired;
    long frame = 0;
    const int blocks[] = {37, 192, 256, 1, 999, 64};
    int b = 0;
    while (frame < static_cast<long>(4.0 * kRate)) {
        int block = blocks[b++ % 6];
        int done = 0;
        while (done < block) {
            while (player.popDue(kRate) != nullptr) {
                if (frame + done < static_cast<long>(4.0 * kRate)) fired.push_back(frame + done);
            }
            int until = player.framesUntilNext(kRate);
            if (until == 0) continue;
            int chunk = block - done;
            if (until > 0 && until < chunk) chunk = until;
            player.advance(chunk, kRate);
            done += chunk;
        }
        frame += block;
    }
    CHECK(fired.size() == 16);
    if (fired.size() != 16) std::printf("  disparos: %zu\n", fired.size());
    for (size_t i = 0; i < fired.size() && i < 16; ++i) {
        const long expected = static_cast<long>(std::lround((i % 8) * 0.25 * kRate + (i / 8) * 2.0 * kRate));
        CHECK(std::labs(fired[i] - expected) <= 1);
    }
}

void testSequenceTempoChangeKeepsPhase() {
    Sequence a;
    a.id = 1;
    a.length = 2.0;
    a.events.push_back({0.0, 0, 60, 0.8f, 0.2f});
    Sequence b = a;
    b.length = 1.0;  // mesmo padrão, andamento dobrado
    SequencePlayer player;
    player.setSequence(&a);
    player.play(true);
    player.advance(static_cast<int>(kRate), kRate);  // metade do ciclo
    player.setSequence(&b);
    CHECK(std::fabs(player.position() - 0.5) < 1e-6);
}

void testCommandQueueNeverBlocks() {
    CommandQueue<int, 8> q;
    int pushed = 0;
    for (int i = 0; i < 100; ++i) pushed += q.push(i) ? 1 : 0;
    CHECK(pushed == 8);
    int v = -1;
    CHECK(q.pop(v) && v == 0);
}

void testEngineSequenceTriggersNotes() {
    Engine engine;
    engine.setConfig(dryConfig());
    engine.publishInstrument(0, sineInstrument());
    auto seq = std::make_unique<Sequence>();
    seq->id = 3;
    seq->length = 0.5;
    seq->events.push_back({0.0, 0, 69, 0.8f, 0.1f});
    seq->events.push_back({0.25, 17, 45, 0.8f, 0.1f});
    engine.setSequence(std::move(seq));
    engine.playSequence(true);
    Rendered r;
    render(engine, r, 4800);
    CHECK(engine.sequencePlaying());
    CHECK(r.peak() > 0.01f);
    engine.stopSequence();
    render(engine, r, 96000);
    CHECK(!engine.sequencePlaying());
    CHECK(engine.activeVoices() == 0);
}

}  // namespace

int main() {
    const std::vector<std::pair<std::string, std::function<void()>>> tests = {
        {"envelope contínuo (sem cliques)", testEnvelopeIsContinuous},
        {"rampa de roubo curta e suave", testStealRampIsShortAndSmooth},
        {"toques simultâneos na mesma nota viram um", testRetriggerGuardMergesPalmHits},
        {"limite de vozes com dezenas de toques", testVoiceLimitWithDozensOfTouches},
        {"roubo de voz sem estalo", testNoClickWhenStealing},
        {"limitador nunca passa do teto", testLimiterNeverClips},
        {"volume máximo dos pais é respeitado", testMasterVolumeCap},
        {"som de reserva antes das amostras", testFallbackSoundsBeforeSamplesLoad},
        {"todos os timbres são seguros", testAllPatchesAreSafe},
        {"sequenciador com precisão de amostra", testSequencePlayerIsSampleAccurate},
        {"mudar andamento preserva a fase", testSequenceTempoChangeKeepsPhase},
        {"fila de comandos não bloqueia", testCommandQueueNeverBlocks},
        {"motor toca a sequência", testEngineSequenceTriggersNotes},
    };
    for (const auto& [name, fn] : tests) {
        const int before = failures;
        fn();
        std::printf("%s %s\n", failures == before ? "[ok]   " : "[FALHA]", name.c_str());
    }
    std::printf("%d verificações, %d falhas\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
