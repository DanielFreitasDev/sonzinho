---
name: Opus em Ogg exige Android 10
description: MediaExtractor só lê Opus em contêiner Ogg a partir da API 29; com minSdk 26 use WebM
type: project
category: gotcha
date: 2026-10-25
---
As amostras são Opus em contêiner WebM (`.webm`), não `.ogg`/`.opus`.

**Problema/Contexto:** a tabela de formatos do Android lista Opus em Ogg só a partir do Android 10; em Matroska/WebM desde o 5.0. O projeto tem minSdk 26.
**Por que importa:** em aparelhos 8.0–9.0 os sons não carregariam (só o sino de reserva tocaria).
**Como aplicar:** mantenha `-f webm` no `prepare_samples.py`. WebM está na lista padrão de `noCompress` do AAPT, então `AssetManager.openFd` funciona sem configuração extra.
**Evidência:** `tools/samples/prepare_samples.py`; `ffprobe` dos arquivos gerados mostra `matroska,webm` / `opus` / 48000 Hz / 2 canais.
