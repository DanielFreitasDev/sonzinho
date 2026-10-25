---
name: Robolectric SDK 36 + testes de UI do Compose
description: JVM args e heap no JDK 17+, relógio manual por causa das animações infinitas, toques mantidos e diálogos com campo de texto
type: project
category: gotcha
date: 2026-10-25
---
Testes de UI rodam no Robolectric com SDK 36, `--add-opens java.base/java.io`, `--add-exports java.base/jdk.internal.access`, heap de 3 GB e `mainClock.autoAdvance = false` nas telas animadas.

**Problema/Contexto:** (1) no SDK 36 o Robolectric 4.17 falha ao iniciar ("Failed to interact with raw FileDescriptor internals") sem os argumentos de JVM; (2) as telas do bebê e o cursor de `OutlinedTextField` animam para sempre, então `waitForIdle` estoura 60 s; (3) um `down()` sem movimento é solto pelo injetor de toques antes do relógio avançar, então segurar o canto por 3 s precisa de micro-movimentos com `advanceEventTime` (e o app mede o gesto pelo `uptimeMillis` dos eventos); (4) um `AlertDialog` com `OutlinedTextField` não chegou a ficar ocioso nem com relógio manual, e travar um teste estoura a heap e leva a suíte ao timeout.
**Por que importa:** sem isso os testes de UI travam ou falham por motivos alheios ao app.
**Como aplicar:** rode classes de UI isoladas (`--tests '*Classe*' -x nativeHostTest`) para achar a que trava; teste diálogos de texto pelo ViewModel. Veja `testOptions.unitTests.all` em `app/build.gradle.kts`, `app/src/test/resources/robolectric.properties` e `holdCorner` em `ParentUnlockUiTest`. Desligue `autoAdvance` antes do `setContent` quando a tela anima.
**Evidência:** `SettingsUiTest` travava na abertura do diálogo de PIN (linha do `assertExists`); execuções de `./gradlew testDebugUnitTest --tests '*UiTest*'` em 2026-10-25 (falhas antes, aprovadas depois dos ajustes).
