---
name: Proxy das sessões na nuvem bloqueia tags e configurações do repositório
description: push de tags cai e escrita de configurações do GitHub (ex. branch padrão) é recusada; a release é criada pelo Actions
type: project
category: gotcha
date: 2026-10-25
---
Nas sessões na nuvem, publique a release pelo workflow disparado em push na `main`, que cria a tag no próprio GitHub; não tente enviar tags.

**Problema/Contexto:** `git push origin v1.0.0` terminou com "unexpected disconnect while reading sideband packet", enquanto pushes de branches funcionaram. `PATCH /repos/...` para trocar a branch padrão respondeu "Repository settings writes are not permitted through this proxy". Não há `gh` autenticado nem ferramenta MCP para criar releases.
**Por que importa:** sem saber disso, a release fica presa esperando uma tag que nunca chega, e a branch criada primeiro na sessão vira a padrão do repositório e não pode ser apagada pelo agente.
**Como aplicar:** use `.github/workflows/release.yml` (push na `main`). Trocar a branch padrão e apagar branches padrão é tarefa do dono do repositório em Settings → General → Default branch.
**Evidência:** tentativas em 2026-10-25; a execução 40229654320 do Actions criou a release v1.0.0 com o APK e a 40230192670 publicou o package Maven.
