#!/usr/bin/env python3
"""Importa amostras do Melimba (projeto do mesmo autor) para o app Sonzinho.

Só entram os timbres que o Sonzinho não tem gravados: kalimba, caixinha de música e
flauta doce. Os demais instrumentos do Melimba repetem os da VCSL com menos notas e
menor qualidade, e o "triângulo" e o "seno" dele são ondas sintetizadas.

Os originais são Vorbis mono de 44,1 kHz com uma nota por oitava; o nome de cada
arquivo é a frequência da nota (ex.: ``Kalimba262.ogg``), confirmada pela medição.
O processamento reaproveita ``prepare_samples.py`` (corte, fade-out, normalização) e
codifica em Opus/WebM mono, pois a fonte é mono. Atualiza só as entradas destes
instrumentos em ``manifest.json``, preservando as da VCSL.

Uso: python3 -I tools/samples/import_melimba.py <clone-do-melimba> <saida-assets-sounds>
Requer: ffmpeg com libsoxr e libopus, numpy, soundfile.
"""
import json
import math
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402

from prepare_samples import SR, fundamental, normalize, process  # noqa: E402

TARGET_RMS_DB = -20
INSTRUMENTS = [
    {"id": "kalimba_soft", "prefix": "Kalimba", "freqs": [262, 523, 1056], "maxSeconds": 3.0, "gainDb": 0},
    {"id": "musicbox", "prefix": "MusicBox", "freqs": [519, 1034, 2082], "maxSeconds": 2.5, "gainDb": -2},
    {"id": "recorder", "prefix": "Recorder", "freqs": [588, 1050, 1573], "maxSeconds": 1.0, "gainDb": -3},
]


def resample_mono(path):
    fd, tmp = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", path, "-af",
         "aresample=48000:resampler=soxr:precision=28", "-ac", "1", "-c:a", "pcm_f32le", tmp],
        check=True,
    )
    data, sr = sf.read(tmp, dtype="float64", always_2d=True)
    os.remove(tmp)
    assert sr == SR
    return data


def encode_opus_mono(data, out_path):
    fd, tmp = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    sf.write(tmp, data.astype(np.float32), SR, subtype="FLOAT")
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", tmp, "-c:a", "libopus", "-b:a", "128k", "-vbr", "on",
         "-application", "audio", "-compression_level", "10", "-map_metadata", "-1", "-f", "webm",
         out_path],
        check=True,
    )
    os.remove(tmp)


def main():
    melimba, out_dir = sys.argv[1], sys.argv[2]
    manifest_path = os.path.join(out_dir, "manifest.json")
    with open(manifest_path, encoding="utf-8") as fh:
        manifest = json.load(fh)
    for inst in INSTRUMENTS:
        inst_dir = os.path.join(out_dir, inst["id"])
        os.makedirs(inst_dir, exist_ok=True)
        zones = []
        for freq in inst["freqs"]:
            src = os.path.join(melimba, "Assets", "AudioClip", f"{inst['prefix']}{freq}.ogg")
            data = resample_mono(src)
            hint = round(69 + 12 * math.log2(freq / 440))
            root = fundamental(data[:, 0], hint, 0)
            data = normalize(process(data, inst["maxSeconds"]), TARGET_RMS_DB, inst["gainDb"])
            name = f"{round(root * 100):05d}.webm"
            encode_opus_mono(data, os.path.join(inst_dir, name))
            zones.append({"file": f"{inst['id']}/{name}", "root": round(float(root), 3)})
            print(f"{inst['id']:13s} {os.path.basename(src):20s} raiz={root:7.2f} {len(data) / SR:4.1f}s")
        zones.sort(key=lambda z: z["root"])
        entry = {"id": inst["id"], "zones": zones}
        others = [i for i in manifest["instruments"] if i["id"] != inst["id"]]
        manifest["instruments"] = others + [entry]
    with open(manifest_path, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, indent=1)


if __name__ == "__main__":
    main()
