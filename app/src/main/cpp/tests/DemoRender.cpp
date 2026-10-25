// Renderização offline para avaliar o som com as amostras reais (não faz parte do app).
// Uso: demo_render <lista.txt> <saida.wav>
// lista.txt: linhas "<slot> <raiz_midi> <arquivo_s16le_48k_estereo.raw>"
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <map>
#include <memory>
#include <random>
#include <sstream>
#include <vector>

#include "Engine.h"

using namespace sonzinho;

namespace {

void writeWav(const char* path, const std::vector<float>& interleaved) {
    FILE* f = std::fopen(path, "wb");
    const uint32_t dataBytes = static_cast<uint32_t>(interleaved.size() * 2);
    auto u32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f); };
    std::fwrite("RIFF", 1, 4, f);
    u32(36 + dataBytes);
    std::fwrite("WAVEfmt ", 1, 8, f);
    u32(16);
    u16(1);
    u16(2);
    u32(48000);
    u32(48000 * 4);
    u16(4);
    u16(16);
    std::fwrite("data", 1, 4, f);
    u32(dataBytes);
    for (float v : interleaved) {
        const auto s = static_cast<int16_t>(std::lround(std::max(-1.0f, std::min(1.0f, v)) * 32767.0f));
        std::fwrite(&s, 2, 1, f);
    }
    std::fclose(f);
}

}  // namespace

int main(int argc, char** argv) {
    if (argc < 3) return 2;
    Engine engine;
    EngineConfig cfg;
    cfg.masterVolume = 1.0f;
    engine.setConfig(cfg);

    std::map<int, std::unique_ptr<SampledInstrument>> insts;
    std::ifstream list(argv[1]);
    std::string line;
    while (std::getline(list, line)) {
        std::istringstream in(line);
        int slot;
        float root;
        std::string path;
        if (!(in >> slot >> root >> path)) continue;
        std::ifstream raw(path, std::ios::binary | std::ios::ate);
        const auto bytes = static_cast<size_t>(raw.tellg());
        raw.seekg(0);
        SampleZone zone;
        zone.rootMidi = root;
        zone.channels = 2;
        zone.pcm.resize(bytes / 2);
        raw.read(reinterpret_cast<char*>(zone.pcm.data()), static_cast<std::streamsize>(bytes));
        zone.frames = static_cast<int>(zone.pcm.size() / 2);
        auto& inst = insts[slot];
        if (!inst) inst = std::make_unique<SampledInstrument>();
        inst->zones.push_back(std::move(zone));
    }
    const float gains[] = {1.0f, 0.95f, 1.0f, 1.1f, 1.3f, 1.2f, 1.2f, 1.5f};
    for (auto& [slot, inst] : insts) {
        inst->gain = gains[slot];
        engine.publishInstrument(slot, std::move(inst));
    }

    std::vector<float> out;
    std::vector<float> buf(192 * 2);
    auto run = [&](double seconds) {
        const int blocks = static_cast<int>(seconds * 48000 / 192);
        for (int b = 0; b < blocks; ++b) {
            engine.render(buf.data(), 192);
            out.insert(out.end(), buf.begin(), buf.end());
        }
    };
    const int penta[] = {0, 2, 4, 7, 9};
    // 1) Escala pentatônica em cada instrumento amostrado e em alguns sintetizados.
    for (int inst : {0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 21, 22, 32, 33, 34, 35, 36, 37}) {
        for (int i = 0; i < 8; ++i) {
            const int base = inst == 2 || inst == 3 ? 72 : (inst == 17 || inst == 36 || inst == 37 ? 40 : 60);
            engine.noteOn(inst, static_cast<float>(base + 12 * (i / 5) + penta[i % 5]), 0.75f, 0, 0.4f);
            run(0.22);
        }
        run(0.6);
    }
    // 2) Mão inteira: 30 toques simultâneos a cada 250 ms durante 6 s.
    std::mt19937 rng(42);
    for (int slap = 0; slap < 24; ++slap) {
        for (int k = 0; k < 30; ++k) {
            const int degree = static_cast<int>(rng() % 12);
            engine.noteOn(1 + static_cast<int>(rng() % 3), static_cast<float>(55 + 12 * (degree / 5) + penta[degree % 5]),
                          0.6f + 0.4f * (rng() % 100) / 100.0f, ((rng() % 200) / 100.0f) - 1.0f, 0);
        }
        run(0.25);
    }
    run(2.0);
    writeWav(argv[2], out);
    std::printf("%zu quadros\n", out.size() / 2);
    return 0;
}
