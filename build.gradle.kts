import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    // JGit — локальные операции с git
    implementation("org.eclipse.jgit:org.eclipse.jgit:${project.property("jgit.version")}")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:${project.property("jgit.version")}")

    // kotlinx.serialization — сериализация конфига в JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${project.property("kotlinx.serialization.version")}")

    // SLF4J + Logback — логирование
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:${project.property("logback.version")}")

    // JUnit 5 — тесты
    testImplementation("org.junit.jupiter:junit-jupiter:${project.property("junit.version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

application {
    mainClass.set("com.gitgui.ui.App")
}

// Добавляем JavaFX modules в run task
tasks.withType<JavaExec> {
    doFirst {
        val javafxModules = configurations.runtimeClasspath.get().filter { it.name.contains("javafx") }.map { it.absolutePath }.joinToString(":")
        jvmArgs(
            "--module-path", javafxModules,
            "--add-modules", "javafx.controls,javafx.fxml,javafx.graphics"
        )
    }
}

// Задача для сборки .deb через jpackage
val jpackageDeb = tasks.register<JavaExec>("jpackageDeb") {
    dependsOn("jar")
    val jpackageHome = providers.exec {
        commandLine("readlink", "-f", System.getProperty("java.home"))
    }.standardOutput.asText.get().trim()

    val mainJar = tasks.jar.get().archiveFile.get().asFile.name
    val jarDir = tasks.jar.get().archiveFile.get().asFile.parent

    doLast {
        val jpackagePath = "$jpackageHome/bin/jpackage"
        val destDir = layout.buildDirectory.get().asFile.path + "/packages"
        exec {
            commandLine(
                jpackagePath, "app-image",
                "--name", project.property("app.packageName").toString(),
                "--version", project.property("app.version").toString(),
                "--input", jarDir,
                "--main-jar", mainJar,
                "--main-class", "com.gitgui.AppKt",
                "--type", "deb",
                "--dest", destDir,
                "--vendor", project.property("app.maintainer").toString()
            )
        }
    }
}
