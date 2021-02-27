plugins {
    kotlin("multiplatform") version "1.4.30"
}

repositories {
    maven("https://dl.bintray.com/dominaezzz/kotlin-native")
    maven("https://dl.bintray.com/korlibs/korlibs")
    jcenter()
}

val os = org.gradle.internal.os.OperatingSystem.current()!!

val imguiVersion = "0.1.7"
val kglVersion = "0.1.10"
val kormaVersion = "2.0.7"

kotlin {
    //mingwX64("native")
    linuxX64("native") {
        binaries {
            executable()
        }

        compilations.getByName("main") {
            val myInterop by cinterops.creating {
                defFile(project.file("src/nativeInterop/cinterop/libpng.def"))
            }
        }
    }
    //if (os.isMacOsX) macosX64("native")

    val nativeMain by sourceSets.getting {
        dependencies {
            implementation("com.kotlin-imgui:imgui:$imguiVersion")
            implementation("com.kotlin-imgui:imgui-glfw:$imguiVersion")
            implementation("com.kotlin-imgui:imgui-opengl:$imguiVersion")
            implementation("com.kgl:kgl-glfw:$kglVersion")
            implementation("com.kgl:kgl-glfw-static:$kglVersion")
            implementation("com.soywiz.korlibs.korma:korma:$kormaVersion")
            implementation("com.soywiz.korlibs.korma:korma-shape:$kormaVersion")
        }
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.dsl.KotlinCompile::class) {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.ExperimentalUnsignedTypes"
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.ExperimentalStdlibApi"
    }
}
