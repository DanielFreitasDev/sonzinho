---
name: Voz presa no fim da amostra deixava o app mudo
description: steal() sem next() nunca termina; vozes acumulavam até esgotar o pool
type: project
category: bug
date: 2026-10-26
---
Quando uma voz sampleada chegava ao fim da amostra, `Voice::renderSample` chamava `env_.steal()` e retornava sem chamar `env_.next()`. A rampa de roubo nunca andava: a voz ficava ativa para sempre, em silêncio.

**Sintoma:** depois de tocar muitas notas (fácil com toques frenéticos), o som ia baixando (vozes presas contam no ganho por densidade) e sumia de vez quando as 48 vozes do pool estavam presas — `allocateVoice()` devolvia `nullptr` e todo toque era descartado. Sair e voltar não resolvia, pois o `Engine` só é recriado com o processo.
**Correção:** `Envelope::stop()` encerra a voz na hora quando o som acabou (a amostra já termina em fade-out no preparo). `steal()` só serve para vozes que continuam sendo renderizadas.
**Como aplicar:** ao encerrar uma voz fora do laço de amostras, use `stop()`; qualquer estágio do envelope precisa avançar por `next()` para terminar.
**Evidência:** teste `fim da amostra libera a voz` em `cpp/tests/AudioCoreTests.cpp` falhava (vozes ativas ficavam em 48, pico 0) antes da correção e passa depois; estresse offline com 120 s de toques frenéticos manteve o volume da nota isolada igual ao de antes (2026-10-26).
