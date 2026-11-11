import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
