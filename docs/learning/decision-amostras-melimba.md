---
name: Quais amostras do Melimba entram no Sonzinho
description: só kalimba, caixinha de música e flauta doce; o resto do Melimba é inferior à VCSL ou sintetizado
type: project
category: decision
date: 2026-10-26
---
Do Melimba (app do próprio autor, uso autorizado em 2026-10-26) entram só timbres que a VCSL não cobre: kalimba doce (slot 8), caixinha de música (9) e flauta doce (10).

**Problema/Contexto:** o autor pediu os sons "de alta definição" do Melimba. Medição: Vorbis mono 44,1 kHz a ~50–60 kbit/s, 3 notas por instrumento (uma por oitava). A VCSL embarcada é estéreo 48 kHz, 6–19 notas.
**Por que importa:** piano, marimba, xilofone e harpa do Melimba seriam um rebaixamento. "Triângulo" e "seno" do Melimba são ondas sintetizadas (harmônicos inteiros, 1,68 s fixos), não gravações.
**Como aplicar:** `tools/samples/import_melimba.py` gera Opus/WebM **mono** (o motor e o `SampleLoader` aceitam 1 canal) e atualiza só suas entradas no `manifest.json`; rode depois do `prepare_samples.py`, que reescreve o manifesto. O nome do arquivo do Melimba é a frequência real; nos `.asset` da flauta doce a frequência declarada é uma oitava abaixo (o Melimba a toca uma oitava acima), então use a medição.
**Evidência:** medição espectral de 2026-10-26 (raízes 59,99/72,00/84,15; 71,88/83,80/95,91; 74,01/84,06/91,08); `./gradlew test ktlintCheck detekt assembleDebug` passou com os novos instrumentos.
