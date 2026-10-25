# LEARNING.md — base de conhecimento do projeto

Leia as entradas relacionadas à tarefa. Os detalhes ficam em `docs/learning/`.

## Bugs e soluções

## Pegadinhas de ambiente, dependências e bibliotecas
- [Oitavas e afinação da VCSL](docs/learning/gotcha-vcsl-oitavas.md) — nomes de nota inconsistentes; use a raiz medida no manifest.
- [Opus em Ogg exige Android 10](docs/learning/gotcha-opus-ogg-minsdk.md) — com minSdk 26 as amostras ficam em WebM.
- [Robolectric SDK 36 + testes Compose](docs/learning/gotcha-robolectric-testes-compose.md) — JVM args, heap, relógio manual, toques mantidos e diálogos de texto.
- [Tarefas Exec e configuration cache](docs/learning/gotcha-configuration-cache-exec.md) — `onlyIf` sem `val` do script, senão o build falha após os testes.
- [Proxy bloqueia tags e configurações](docs/learning/gotcha-proxy-git-tags-configuracoes.md) — release pelo Actions em push na main; branch padrão só pelo dono.

## Decisões de arquitetura
- [Motor de áudio em C++ com Oboe](docs/learning/decision-motor-oboe.md) — núcleo sem Android, testado no host; regras do callback.

## Abordagens que NÃO funcionaram

## Notas de performance

## Referências externas
