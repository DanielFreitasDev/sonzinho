---
name: Motor de áudio em C++ com Oboe
description: por que o áudio é nativo (Oboe/AAudio) com núcleo testável no host
type: project
category: decision
date: 2026-10-25
---
O áudio é um motor C++ próprio sobre Oboe; o núcleo (`cpp/core`) não depende de Android e é testado no computador.

**Problema/Contexto:** requisito de latência mínima, dezenas de toques simultâneos sem estalo nem clipping, síntese em tempo real e sequenciador preciso.
**Por que importa:** SoundPool/AudioTrack em Kotlin têm latência maior, pouca polifonia controlável e pausas de GC; trocar o motor exigiria reescrever vozes, efeitos e sequenciador.
**Como aplicar:** novas funções de áudio entram como comandos na `CommandQueue`; nada de alocação ou trava em `Engine::render`. Testes novos do núcleo vão em `cpp/tests/AudioCoreTests.cpp`. Medições de loudness/picos podem ser feitas com `demo_render`.
**Evidência:** `app/src/main/cpp/`; 378 verificações do `audio_core_tests` passando; render offline com 30 toques simultâneos a cada 250 ms chegou a −8,7 dBFS de pico, sem amostras acima de 0,999 (2026-10-25).
