cmake_minimum_required(VERSION 3.22)
project(sonzinho_audio_core_tests CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../core)

add_executable(audio_core_tests
    AudioCoreTests.cpp
    ${CORE_DIR}/Engine.cpp
    ${CORE_DIR}/Voice.cpp
    ${CORE_DIR}/BuiltinPatches.cpp
)
target_include_directories(audio_core_tests PRIVATE ${CORE_DIR})
target_compile_options(audio_core_tests PRIVATE -O2 -Wall -Wextra -Werror)
find_package(Threads REQUIRED)
target_link_libraries(audio_core_tests PRIVATE Threads::Threads)

# Ferramenta de avaliação de áudio (não é executada pelos testes).
add_executable(demo_render
    DemoRender.cpp
    ${CORE_DIR}/Engine.cpp
    ${CORE_DIR}/Voice.cpp
    ${CORE_DIR}/BuiltinPatches.cpp
)
target_include_directories(demo_render PRIVATE ${CORE_DIR})
target_compile_options(demo_render PRIVATE -O2)
