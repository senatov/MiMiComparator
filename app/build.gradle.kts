import org.gradle.jvm.tasks.Jar

/*
 * Build script for the MiMiComparator desktop application.
 * Kotlin + JavaFX edition.
 *
 * Created by Iakov Senatov.
 * Copyright © 2026 Iakov Senatov. All rights reserved.
 */

plugins {
    application
    kotlin("jvm") version "2.4.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

javafx {
    version = "26"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(kotlin("reflect"))
    // AtlantaFX — macOS-style Cupertino theme for JavaFX
    implementation("io.github.mkpaz:atlantafx-base:2.1.0")
    // logging: Log4j2 (API + core + SLF4J bridge)
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.26.1"))
    implementation("org.apache.logging.log4j:log4j-api")
    implementation("org.apache.logging.log4j:log4j-core")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl")
    // jackson — JSON persistence
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.1"))
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // test
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "org.senatov.App"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

val jpackageInputDir = layout.buildDirectory.dir("jpackage/input")
val appImageOutputDir = layout.buildDirectory.dir("jpackage/output")

val prepareJpackageInput = tasks.register<Copy>("prepareJpackageInput") {
    dependsOn(tasks.named("jar"))
    into(jpackageInputDir)
    from(tasks.named<Jar>("jar"))
    from(configurations.runtimeClasspath)
}

tasks.register<Exec>("packageMacApp") {
    group = "distribution"
    description = "Builds a macOS app image with jpackage and the MiMiComparator.icns icon."
    dependsOn(prepareJpackageInput)
    val inputDir = jpackageInputDir.get().asFile
    val outputDir = appImageOutputDir.get().asFile
    val jarFileName = tasks.named<Jar>("jar").get().archiveFileName.get()
    val iconFile = project.file("src/main/resources/icons/MiMiComparator.icns")
    // Packaging needs a full JDK with jpackage/jmods. The Java 26 runtime used by Gradle
    // is independent from the Java 25 bytecode target configured above.
    val packagingLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(26)
    }
    val javaHome = packagingLauncher.get().metadata.installationPath.asFile
    val jpackageExecutable = javaHome.resolve("bin/jpackage")
    val javafxModuleJars = configurations.runtimeClasspath.get().files
        .filter { it.name.startsWith("javafx-") && it.extension == "jar" }
        .sortedBy { it.name }
    val modulePathEntries = buildList {
        add(javaHome.resolve("jmods").absolutePath)
        addAll(javafxModuleJars.map { it.absolutePath })
    }
    val modulePath = modulePathEntries.joinToString(":")
    doFirst {
        outputDir.mkdirs()
        outputDir.resolve("MiMiComparator.app").deleteRecursively()
        if (!iconFile.exists()) throw GradleException("Missing app icon: ${iconFile.absolutePath}")
        if (!jpackageExecutable.exists()) throw GradleException("Missing jpackage: ${jpackageExecutable.absolutePath}")
        val jmodsDir = javaHome.resolve("jmods")
        if (!jmodsDir.exists()) throw GradleException("Missing JDK jmods: ${jmodsDir.absolutePath}")
        if (javafxModuleJars.isEmpty()) throw GradleException("Missing JavaFX module jars in runtimeClasspath")
        println("packageMacApp module path: ${modulePathEntries.joinToString(" | ")}")
    }
    commandLine(
        jpackageExecutable.absolutePath,
        "--type", "app-image",
        "--name", "MiMiComparator",
        "--dest", outputDir.absolutePath,
        "--input", inputDir.absolutePath,
        "--main-jar", jarFileName,
        "--main-class", application.mainClass.get(),
        "--icon", iconFile.absolutePath,
        "--module-path", modulePath,
        "--add-modules", "javafx.controls,javafx.fxml,javafx.graphics"
    )
}
