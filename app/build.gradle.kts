import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room3)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.danielfreitas.sonzinho"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "dev.danielfreitas.sonzinho"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64-v8a: Galaxy S24 Ultra; x86_64: emulador.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
                cppFlags += listOf("-std=c++20", "-O3", "-ffast-math", "-fno-exceptions", "-fno-rtti")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assinatura de debug para permitir `adb install` sem keystore; troque por uma própria para distribuir.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        prefab = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                // Robolectric (SDK 36) acessa internos de FileDescriptor do JDK 17+.
                it.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java")
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room3.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.oboe)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    detektPlugins(libs.compose.rules.detekt)
}

// Testes do núcleo de áudio em C++ (alocação de vozes, envelopes, limitador, sequenciador),
// compilados para a máquina host e executados junto com `./gradlew test`.
val nativeHostTestDir = layout.buildDirectory.dir("native-host-test")
val nativeSourceDir = layout.projectDirectory.dir("src/main/cpp")

val configureNativeHostTest by tasks.registering(Exec::class) {
    onlyIf("cmake e um compilador C++ do host estão no PATH") {
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        listOf("cmake", "c++").all { tool -> path.any { File(it, tool).canExecute() } }
    }
    inputs.dir(nativeSourceDir)
    outputs.dir(nativeHostTestDir)
    commandLine(
        "cmake",
        "-S",
        nativeSourceDir.dir("tests").asFile.absolutePath,
        "-B",
        nativeHostTestDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
    )
}

val nativeHostTest by tasks.registering(Exec::class) {
    description = "Compila e executa os testes do motor de áudio no host."
    group = "verification"
    dependsOn(configureNativeHostTest)
    onlyIf("cmake e um compilador C++ do host estão no PATH") {
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        listOf("cmake", "c++").all { tool -> path.any { File(it, tool).canExecute() } }
    }
    inputs.dir(nativeSourceDir)
    outputs.upToDateWhen { false }
    commandLine(
        "sh",
        "-c",
        "cmake --build '${nativeHostTestDir.get().asFile.absolutePath}' -j 4 && " +
            "'${nativeHostTestDir.get().asFile.absolutePath}/audio_core_tests'",
    )
}

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(nativeHostTest)
}
