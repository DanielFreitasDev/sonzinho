---
name: Maven Central responde 429 no primeiro build na nuvem
description: o download inicial das dependências esbarra em limite de requisições; repetir o build resolve
type: project
category: gotcha
date: 2026-10-26
---
No primeiro `./gradlew` de uma sessão na nuvem, o Maven Central devolve "Received status code 429" e o build falha (às vezes como erro de configuration cache ou de plugin não encontrado).

**Por que importa:** a mensagem parece erro de configuração do projeto, mas é limite de taxa.
**Como aplicar:** repita o build com `--max-workers=2` até baixar tudo (cada tentativa avança no cache). SDK: `cmdline-tools` de dl.google.com + `sdkmanager` com os pacotes do `AGENTS.md`, `sdk.dir` no `local.properties` (ignorado pelo git).
**Evidência:** 2026-10-26: três falhas com 429/"Could not download", sucesso na sexta tentativa (`BUILD SUCCESSFUL in 3m 8s`).
