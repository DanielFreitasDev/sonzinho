cmake_minimum_required(VERSION 3.22.1)
project(sonzinho_audio CXX)

find_package(oboe REQUIRED CONFIG)

add_library(sonzinho_audio SHARED
    core/Engine.cpp
    core/Voice.cpp
    core/BuiltinPatches.cpp
    android/AudioOutput.cpp
    android/JniBridge.cpp
)

target_include_directories(sonzinho_audio PRIVATE core)
target_compile_options(sonzinho_audio PRIVATE -Wall -Wextra -Werror)
target_link_libraries(sonzinho_audio PRIVATE oboe::oboe log android)
# Páginas de 16 KB (exigência do Android 15+); o NDK r28+ já alinha por padrão.
target_link_options(sonzinho_audio PRIVATE "-Wl,-z,max-page-size=16384")
