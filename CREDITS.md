# Créditos e licenças

Todo material de terceiros embarcado no Sonzinho está listado aqui. Nada usa licença
“NonCommercial”, incerta, ou personagens/marcas.

## Amostras de instrumentos — VCSL (CC0 1.0)

- **Origem:** Versilian Community Sample Library — <https://github.com/sgossner/VCSL>
  (commit da branch `master` baixado em 2026-10-25).
- **Autor:** Versilian Studios LLC (e colaboradores da VCSL).
- **Licença:** Creative Commons Zero 1.0 Universal (domínio público). Texto completo em
  `app/src/main/assets/licenses/vcsl-CC0.txt`. Crédito não é exigido; é dado por cortesia.
- **Processamento:** `tools/samples/prepare_samples.py` (lista exata em `tools/samples/selection.json`):
  reamostragem para 48 kHz (SoX, precisão de 28 bits), medição da altura real de cada amostra,
  recorte de silêncio e cauda abaixo de −60 dB, fade-out, normalização e codificação Opus/WebM
  estéreo a 192 kbit/s. Os originais são WAV de 44,1 kHz/16–24 bits (kalimba: 48 kHz/24 bits).

| Pasta no app (`app/src/main/assets/sounds/`) | Instrumento na VCSL | Arquivos usados |
| --- | --- | --- |
| `piano/` | Chordophones/Zithers/Grand Piano, Kawai/Sustains | 14 notas, camada `v2` (suave) |
| `marimba/` | Idiophones/Struck Idiophones/Marimba | 10 notas, camada `soft` |
| `glockenspiel/` | Idiophones/Struck Idiophones/Glockenspiel | 6 notas, camada `soft` |
| `xylophone/` | Idiophones/Struck Idiophones/Xylophone/Soft Mallets | 8 notas, `pp` |
| `vibraphone/` | Idiophones/Struck Idiophones/Vibraphone/Soft Mallets | 11 notas, `v1` |
| `kalimba/` | Idiophones/Plucked Idiophones/Kalimba, Tanzania | 14 lâminas |
| `harp/` | Chordophones/Composite Chordophones/Concert Harp | 19 notas, `mf` |
| `chimes/` | Idiophones/Struck Idiophones/Hand Chimes | 19 notas |

O nome de cada arquivo é a altura medida em centésimos de semitom MIDI (ex.: `07199.webm` ≈ MIDI 71,99).
O mapeamento completo está em `app/src/main/assets/sounds/manifest.json`.

## Fonte — Nunito (SIL Open Font License 1.1)

- **Origem:** <https://github.com/google/fonts/tree/main/ofl/nunito> (projeto: <https://github.com/googlefonts/nunito>).
- **Autores:** The Nunito Project Authors (Vernon Adams, Cyreal, Jacques Le Bailly).
- **Licença:** SIL Open Font License 1.1 — permite uso, embarque e redistribuição em apps.
  Texto completo em `app/src/main/assets/licenses/nunito-OFL.txt`.
- **Arquivo:** `app/src/main/res/font/nunito.ttf` (fonte variável `Nunito[wght].ttf`, sem modificações).

## Sons sintetizados (sem material externo)

Vozes dos bichinhos (passarinho, gatinho, patinho, sapinho, ursinho, baleia), nuvem (pad), grave,
sininho mágico, flautinha, bolhinhas, celesta, caixinha de música e todos os sons do criador são
gerados em tempo real pelo motor de áudio do app (`app/src/main/cpp/core/`).

## Bibliotecas

- **Oboe** (Google) — Apache 2.0 — <https://github.com/google/oboe>
- Jetpack (Compose, Room, DataStore, Lifecycle, Hilt…) — Apache 2.0.

Os créditos também aparecem no app em **Área dos pais → Créditos dos sons**.
