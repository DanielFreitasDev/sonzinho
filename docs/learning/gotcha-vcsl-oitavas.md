---
name: Oitavas e afinação da VCSL
description: nomes de arquivo da VCSL usam convenções de oitava diferentes por instrumento; a kalimba não é temperada
type: project
category: gotcha
date: 2026-10-25
---
Não confie no nome da nota dos arquivos da VCSL: meça a fundamental e use a raiz medida (MIDI fracionário).

**Problema/Contexto:** piano Kawai, marimba, glockenspiel, xilofone, vibrafone e sinos soam uma oitava acima do nome (convenção C3 = dó central); a harpa segue a convenção padrão (C4 = 60); a kalimba da Tanzânia tem lâminas fora do temperamento (até ~50 cents) e algumas com o nome errado.
**Por que importa:** misturar convenções faz notas pularem de oitava dentro do mesmo instrumento e desafina tudo contra os sintetizadores.
**Como aplicar:** `tools/samples/prepare_samples.py` procura o pico perto de `nome + 12*octaveHint` e grava a raiz medida no `manifest.json`; o motor reamostra a partir dela. Ao adicionar instrumentos, confira o `octaveHint` com a medição.
**Evidência:** análise espectral feita em 2026-10-25 (saída do script lista `raiz=` por arquivo); kalimba A#2 e A#4 mediram a mesma altura (~58,6), por isso só lâminas consistentes foram mantidas.
