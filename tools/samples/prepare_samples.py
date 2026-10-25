#!/usr/bin/env python3
"""Prepara as amostras da VCSL (CC0) para o app Sonzinho.

Para cada amostra escolhida em ``selection.json``:
  1. reamostra para 48 kHz com SoX-resampler (precisão 28 bits);
  2. mede a frequência fundamental real (a VCSL mistura convenções de oitava
     e a kalimba tem afinação tradicional), guardando a raiz em MIDI fracionário;
  3. recorta silêncio inicial preservando o ataque e corta a cauda abaixo de -60 dB
     ou no comprimento máximo do instrumento, com fade-out cossenoidal;
  4. remove DC, normaliza o volume de curto prazo e limita o pico a -1 dBFS;
  5. codifica em Opus/WebM (48 kHz, estéreo, 192 kbit/s VBR) e grava ``manifest.json``.
     WebM e não Ogg: o MediaExtractor só lê Opus em Ogg a partir do Android 10.

Uso: python3 -I tools/samples/prepare_samples.py <raiz-da-VCSL> <saida-assets-sounds>
Requer: ffmpeg com libsoxr e libopus, numpy, soundfile.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

import numpy as np
import soundfile as sf

SR = 48000
NOTES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
HERE = os.path.dirname(os.path.abspath(__file__))


def note_from_name(name):
    m = re.search(r"_([A-G]#?)(-?\d)_", name)
    n, o = m.group(1), int(m.group(2))
    return (o + 1) * 12 + NOTES[n[0]] + (1 if "#" in n else 0)


def resample(path):
    fd, tmp = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", path, "-af",
         "aresample=48000:resampler=soxr:precision=28", "-ac", "2", "-c:a", "pcm_f32le", tmp],
        check=True,
    )
    data, sr = sf.read(tmp, dtype="float64", always_2d=True)
    os.remove(tmp)
    assert sr == SR
    return data


def fundamental(mono, hint_midi, octave_hint):
    """Procura o pico espectral perto de hint+12*k e refina com interpolação parabólica."""
    onset = int(np.argmax(np.abs(mono) > 0.05 * np.abs(mono).max()))
    seg = mono[onset + int(0.03 * SR): onset + int(0.6 * SR)]
    n = 1 << 17
    spec = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), n))
    freqs = np.fft.rfftfreq(n, 1 / SR)
    fc = 440 * 2 ** ((hint_midi + 12 * octave_hint - 69) / 12)
    lo = np.searchsorted(freqs, fc * 2 ** (-1.5 / 12))
    hi = np.searchsorted(freqs, fc * 2 ** (1.5 / 12))
    i = lo + int(np.argmax(spec[lo:hi]))
    a, b, c = (np.log(spec[j] + 1e-12) for j in (i - 1, i, i + 1))
    p = 0.5 * (a - c) / (a - 2 * b + c)
    f = (i + p) * SR / n
    return 69 + 12 * np.log2(f / 440)


def envelope(mono, win=480):
    sq = np.convolve(mono ** 2, np.ones(win) / win, mode="same")
    return np.sqrt(sq)


def process(data, max_seconds):
    data = data - data.mean(axis=0)
    mono = data.mean(axis=1)
    peak = np.abs(mono).max()
    # Início: primeiro ponto acima de -40 dB do pico, com 1 ms de pré-roll e fade-in de 1 ms.
    start = int(np.argmax(np.abs(mono) > peak * 10 ** (-40 / 20)))
    start = max(0, start - SR // 1000)
    data = data[start:]
    env = envelope(data.mean(axis=1))
    above = np.nonzero(env > env.max() * 10 ** (-60 / 20))[0]
    end = min(len(data), int(above[-1]) + 1 if len(above) else len(data), int(max_seconds * SR))
    data = data[:end].copy()
    fade_in = SR // 1000
    data[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    fade_out = min(len(data) // 3, SR // 2)
    data[-fade_out:] *= (0.5 * (1 + np.cos(np.linspace(0, np.pi, fade_out))))[:, None]
    return data


def normalize(data, target_db, gain_db):
    head = data[: int(0.3 * SR)].mean(axis=1)
    rms = np.sqrt(np.mean(head ** 2)) + 1e-12
    data = data * (10 ** (target_db / 20) / rms) * 10 ** (gain_db / 20)
    peak = np.abs(data).max()
    ceiling = 10 ** (-1 / 20)
    if peak > ceiling:
        data *= ceiling / peak
    return data


def encode_opus(data, out_path):
    fd, tmp = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    sf.write(tmp, data.astype(np.float32), SR, subtype="FLOAT")
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", tmp, "-c:a", "libopus", "-b:a", "192k", "-vbr", "on",
         "-application", "audio", "-compression_level", "10", "-map_metadata", "-1", "-f", "webm",
         out_path],
        check=True,
    )
    os.remove(tmp)


def main():
    vcsl, out_dir = sys.argv[1], sys.argv[2]
    with open(os.path.join(HERE, "selection.json"), encoding="utf-8") as fh:
        selection = json.load(fh)
    manifest = {"sampleRate": SR, "instruments": []}
    for inst in selection["instruments"]:
        inst_dir = os.path.join(out_dir, inst["id"])
        os.makedirs(inst_dir, exist_ok=True)
        zones = []
        for rel in inst["files"]:
            src = os.path.join(vcsl, inst["folder"], rel)
            data = resample(src)
            hint = note_from_name(os.path.basename(rel))
            root = fundamental(data.mean(axis=1), hint, inst["octaveHint"])
            data = normalize(process(data, inst["maxSeconds"]), selection["targetRmsDb"], inst["gainDb"])
            name = f"{round(root * 100):05d}.webm"
            encode_opus(data, os.path.join(inst_dir, name))
            zones.append({"file": f"{inst['id']}/{name}", "root": round(float(root), 3)})
            print(f"{inst['id']:10s} {os.path.basename(rel):44s} raiz={root:7.2f} {len(data) / SR:4.1f}s")
        zones.sort(key=lambda z: z["root"])
        manifest["instruments"].append({"id": inst["id"], "zones": zones})
    with open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, indent=1)


if __name__ == "__main__":
    main()
