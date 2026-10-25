---
name: Robolectric SDK 36 + testes de UI do Compose
description: JVM args necessários no JDK 17+, relógio manual por causa das animações infinitas e toques mantidos
type: project
category: gotcha
date: 2026-10-25
---
Testes de UI rodam no Robolectric com SDK 36, `--add-opens java.base/java.io` e `--add-exports java.base/jdk.internal.access`, e com `mainClock.autoAdvance = false`.

**Problema/Contexto:** (1) no SDK 36 o Robolectric 4.17 falha ao iniciar ("Failed to interact with raw FileDescriptor internals") sem os argumentos de JVM; (2) as telas do bebê e o cursor de `OutlinedTextField` animam para sempre, então `waitForIdle` estoura 60 s; (3) um `down()` sem movimento é solto pelo injetor de toques antes do relógio avançar, então segurar o canto por 3 s precisa de micro-movimentos com `advanceEventTime`.
**Por que importa:** sem isso os testes de UI travam ou falham por motivos alheios ao app.
**Como aplicar:** veja `testOptions.unitTests.all` em `app/build.gradle.kts`, `app/src/test/resources/robolectric.properties` e `holdCorner` em `ParentUnlockUiTest`. Desligue `autoAdvance` antes do `setContent` quando a tela anima.
**Evidência:** execuções de `./gradlew testDebugUnitTest --tests '*UiTest*'` em 2026-10-25 (falhas antes, aprovadas depois dos ajustes).
