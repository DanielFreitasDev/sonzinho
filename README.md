# Sonzinho 🎵

App Android de brincadeiras musicais para bebês (pensado para 1 ano), feito para o
Galaxy S24 Ultra com Android 17 / One UI 9. O bebê toca e se encanta com sons bonitos; os
pais criam sons, músicas e gravações sem precisar saber música.

- **100% offline**, sem anúncios, compras, login, analytics ou coleta de dados.
  O manifesto **não declara nenhuma permissão** (nem `INTERNET`).
- Toda a interface em português do Brasil.
- Sons de alta qualidade com licença livre documentada em [`CREDITS.md`](CREDITS.md).

## O que tem

**Modo bebê** (sem menus, só cores, formas e sons):

| Brincadeira | Como funciona |
| --- | --- |
| Tela mágica | A tela toda é um instrumento: faixas coloridas do grave (embaixo/à esquerda, cores quentes) ao agudo (em cima/à direita, cores frias). Arrastar faz glissando. Ondas e partículas suaves. |
| Bichinhos musicais | Seis bichinhos (passarinho, gatinho, patinho, sapinho, ursinho e baleia) cobrem a tela; cada toque faz um deles cantar (voz sintetizada suave ou nota musical) e pular. Sons criados pelos pais passeiam como bolhinhas. |
| Teclado colorido | De 5 a 8 teclas gigantes, cada uma com cor, forma e nota. |
| Música que toca sozinha | As músicas e gravações dos pais tocam em sequência com animação; por baixo há uma tela mágica na mesma escala, então o bebê toca junto e tudo combina. |

**Área dos pais** (protegida por gesto + desafio): fazer música (sequenciador em grade de 8/16 passos com
4 linhas: melodia, brilhinho, grave e nuvem), criador de sons (quadro X/Y + “curtinho/longo”, “eco”,
“balanço”), gravação livre (inclusive “gravar a brincadeira do bebê”), biblioteca (tocar, renomear,
apagar, escolher o que aparece no modo bebê) e configurações.

## Requisitos para compilar

- JDK 17 ou mais novo (testado com JDK 21).
- Android SDK com `platforms;android-37.0`, `build-tools;37.0.0`, `ndk;30.0.16248370` e `cmake;4.1.2`
  (o Android Studio instala sozinho; pela linha de comando use `sdkmanager`).
- Opcional: `cmake` e um compilador C++ no `PATH` do computador para rodar os testes do motor de áudio
  junto com `./gradlew test` (sem eles, essa etapa é pulada com aviso).

```bash
# local.properties (ou variável ANDROID_HOME)
echo "sdk.dir=/caminho/para/Android/Sdk" > local.properties

./gradlew assembleDebug        # APK de debug: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # APK otimizado (assinado com a chave de debug, para uso próprio)
./gradlew test                 # testes unitários, testes de UI (Robolectric) e testes C++ do motor
./gradlew ktlintCheck detekt   # estilo e análise estática
```

## Instalar via adb

1. No celular: **Configurações → Sobre o telefone → Informações do software**, toque 7 vezes em
   **Número de compilação** para liberar as **Opções do desenvolvedor**; lá, ative **Depuração USB**.
2. Conecte o cabo e autorize o computador no aviso do celular.
3. Instale:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk   # ou o app-debug.apk
```

Para trocar entre debug e release desinstale antes (`adb uninstall dev.danielfreitas.sonzinho`), pois as
assinaturas podem diferir.

## Como entrar na área dos pais

Segure o **cantinho com a estrela** (canto superior esquerdo) por **3 segundos com um dedo só**.
Vários dedos ao mesmo tempo (a palma do bebê) cancelam o gesto. Depois responda a continha
(somas de 11 a 18) ou o PIN, se você criou um em Configurações. Três erros seguidos bloqueiam
novas tentativas por 30 segundos; o desafio fecha sozinho após 25 s sem uso.

## Modo travado

Com **Modo travado** ligado (padrão), no modo bebê o app:

- fica em **tela cheia imersiva** (barras de status e navegação escondidas e reescondidas sempre que
  o sistema as mostra por um deslize da borda);
- **intercepta o voltar e o voltar preditivo** (Android 16+ exige `OnBackPressedCallback`; o app usa
  `BackHandler` do Compose, o que também desliga a animação “voltar para a tela inicial”);
- trava a **orientação** (sem girar pelo sensor) e mantém a tela acesa;
- ignora as teclas de volume (o teto de volume é definido pelos pais);
- chama `startLockTask()`: **fixação de tela** do sistema ou **bloqueio total** (Device Owner).

### Orientação padrão: paisagem

Paisagem foi escolhida porque, no S24 Ultra apoiado na mesa ou no colo, ela dá a maior área
horizontal para mãos e palmas, permite seis bichinhos e teclas gigantes lado a lado e afasta a
barra de gestos de onde o bebê bate. É configurável (paisagem, paisagem invertida, retrato).
Observação: no Android 16/17 o sistema ignora travas de orientação em telas grandes
(menor largura ≥ 600 dp: tablets e dobráveis abertos); o S24 Ultra é um celular e respeita a trava.

### Ativar a fixação de apps na One UI 9 (sem computador)

1. **Configurações → Segurança e privacidade → Mais configurações de segurança → Fixar apps**
   (em algumas versões “Fixar janelas”; use a busca das Configurações por “Fixar”).
2. Ative a opção e ligue **“Pedir PIN antes de desafixar”**.
3. Abra o Sonzinho; ao entrar no modo bebê o Android pergunta se deseja fixar o app. Confirme.
4. Para sair: **Área dos pais → Sair do Sonzinho**, ou o gesto do sistema (botões: segure *Voltar* +
   *Recentes*; gestos: deslize de baixo para cima e segure) seguido do PIN do aparelho.

A tela **Área dos pais → Travar a tela** mostra o estado atual e tem atalhos para isso.

### Opcional: bloqueio total com Device Owner

Com Device Owner, o app entra em *lock task* de verdade: Início, Recentes, notificações e o menu do
botão liga/desliga ficam bloqueados (`setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)`), sem pedir
confirmação.

```bash
adb shell dpm set-device-owner dev.danielfreitas.sonzinho/.data.lock.SonzinhoDeviceAdminReceiver
```

**Riscos e limitações — leia antes:**

- O Android só aceita o comando em um aparelho **sem contas** (Google, Samsung etc.). Na prática é
  para um **aparelho dedicado** ao bebê, ou recém-restaurado de fábrica antes de adicionar contas.
- Um app Device Owner tem poderes de administração do aparelho. O Sonzinho usa só a lista de lock task.
- Não é possível desinstalar o app enquanto ele for Device Owner.
- Se algo der errado, a saída garantida é restaurar o aparelho de fábrica.

**Como remover:** no app, **Configurações → Segurança → Remover Device Owner** (usa
`clearDeviceOwnerApp`). Depois disso o app volta a usar a fixação de tela comum e pode ser
desinstalado normalmente.

## Arquitetura

```
app/src/main/
├── cpp/                 motor de áudio (C++20)
│   ├── core/            independente de plataforma: vozes, envelopes, síntese, efeitos, sequenciador
│   ├── android/         saída Oboe (AAudio) e ponte JNI
│   └── tests/           testes do núcleo e renderizador offline (rodam no computador)
├── java/.../sonzinho/
│   ├── domain/          modelos, teoria musical, casos de uso (Kotlin puro, sem Android)
│   ├── data/            motor nativo, decodificação de amostras, DataStore, Room 3, lock task
│   ├── di/              módulos Hilt
│   └── ui/              Compose: modo bebê, área dos pais, tema
└── assets/sounds/       amostras Opus/WebM + manifest.json
```

- **Clean Architecture + MVVM**: telas observam `StateFlow` dos ViewModels; o domínio não conhece Android.
- **Hilt** para injeção, **Coroutines/Flow**, **Room 3** (músicas, sons e gravações), **DataStore** (configurações).

### Motor de áudio: por que Oboe

O critério era latência e estabilidade no S24 Ultra. **Oboe** (C++/NDK) usa AAudio em modo MMAP
exclusivo quando disponível, com buffer de 2 bursts e ajuste automático (`LatencyTuner`), e roda o
callback numa thread de tempo real sem coletor de lixo. `SoundPool`/`AudioTrack` em Kotlin têm
latência maior, polifonia e controle de envelope limitados, e pausas de GC no caminho do áudio.

No callback não há alocação, travas nem E/S:

- **Comandos** da UI entram por uma fila sem travas no consumidor (`CommandQueue`).
- **Amostras** são decodificadas uma vez na abertura (MediaCodec, Opus → PCM 16 bits na memória),
  com o instrumento escolhido carregado primeiro; enquanto isso, um sino sintetizado garante som.
- **Vozes:** 48 no total, até 24 audíveis; ao passar do limite a voz mais antiga (ou a mais baixa em
  liberação) sai com fade de 8 ms (*voice stealing* sem clique). Toques na mesma nota em menos de
  28 ms (palma inteira) viram um só ataque. Ganho automático por densidade de vozes.
- **Reamostragem** com interpolação cúbica de Hermite a partir da raiz **medida** de cada amostra.
- **Envelopes ADSR** com ataque mínimo de 1,5 ms e curvas exponenciais: sem cliques.
- **Síntese** com osciladores anti-aliasing (PolyBLEP), FM para sinos, filtros TPT/SVF,
  formantes para as vozes dos bichinhos, vibrato, tremolo e glides.
- **Efeitos:** reverb FDN 8×8 com amortecimento, eco pingue-pongue, compressor de joelho suave,
  prateleira suave de agudos e **limitador com antecipação** (teto −1 dBFS) que matematicamente não
  deixa passar picos; depois dele vem o teto de volume dos pais (padrão 50%).
- **Sequenciador** com precisão de amostra dentro do callback (o bloco é dividido no instante exato
  de cada nota); mudar o andamento preserva a fase.
- Áudio pausa em segundo plano e respeita o **foco de áudio** (ligações e outros apps silenciam o Sonzinho).

## Trocar ou adicionar sons

1. Coloque a biblioteca de origem (ex.: um clone da VCSL) em uma pasta.
2. Edite `tools/samples/selection.json`: cada instrumento tem `id` (pasta de saída), `folder`,
   `files`, `octaveHint` (oitava esperada em relação ao nome do arquivo), `maxSeconds` e `gainDb`.
3. Gere os arquivos (requer `ffmpeg` com libsoxr e libopus, `numpy` e `soundfile`):

```bash
python3 -I tools/samples/prepare_samples.py /caminho/da/VCSL app/src/main/assets/sounds
```

4. Um **novo** instrumento também precisa de um slot livre (0–15) em `SampledSpec`
   (`data/audio/SampleLoader.kt`) e de uma entrada em `Instrument` (`domain/model/Instrument.kt`).
5. Registre origem, autor e licença em `CREDITS.md` (use apenas CC0/domínio público, ou CC-BY com
   crédito na tela de créditos).

Para ouvir o resultado no computador: compile `app/src/main/cpp/tests` com CMake e use
`demo_render` (instruções no topo de `DemoRender.cpp`).

## Testes

`./gradlew test` executa:

- **Kotlin (JVM):** teoria musical, sequenciador (edição e agendamento), criador de sons, PIN,
  desafio, gravador, ViewModels (desbloqueio, configurações, sequenciador, criador, gravação,
  música sozinha), mapeamento do banco.
- **UI Compose (Robolectric):** desbloqueio parental (gesto + conta, palma, erros), toques nas
  brincadeiras (qualquer ponto soa, dez dedos, glissando, teclado ascendente), configurações e PIN.
- **C++ (host):** envelopes sem cliques, roubo de voz sem estalo, limite de vozes com dezenas de
  toques, limitador sem clipping, teto de volume, todos os timbres terminam e são seguros,
  sequenciador com precisão de amostra.
