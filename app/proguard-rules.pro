# Métodos nativos chamados pelo motor de áudio (JNI): manter nomes.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class dev.danielfreitas.sonzinho.data.audio.NativeAudioBridge { *; }
