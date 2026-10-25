---
name: Tarefas Exec e configuration cache
description: onlyIf/commandLine de tarefas Exec não podem referenciar valores do script com o configuration cache ligado
type: project
category: gotcha
date: 2026-10-25
---
Nas tarefas `configureNativeHostTest` e `nativeHostTest`, a verificação de `cmake`/`c++` fica dentro do próprio `onlyIf`, sem usar `val` do script.

**Problema/Contexto:** `gradle.properties` liga `org.gradle.configuration-cache=true`. Com um `val hostToolsAvailable = providers.provider { ... }` no topo do `app/build.gradle.kts`, todos os testes passavam mas o build falhava ao gravar o cache ("cannot serialize Gradle script object references").
**Por que importa:** `./gradlew test` termina com BUILD FAILED mesmo com 0 falhas, o que engana quem lê só o resultado.
**Como aplicar:** em lambdas de tarefas (`onlyIf`, `doLast`), use apenas valores locais calculados dentro delas ou providers sem referência ao script.
**Evidência:** saída de `./gradlew test` em 2026-10-25 (2 problemas de configuration cache) e BUILD SUCCESSFUL após mover a checagem para dentro do `onlyIf`.
