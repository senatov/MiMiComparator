import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.jvm.tasks.Jar
/*
 * Build script for the MiMiComparator desktop application.
 *
 * Created by Iakov Senatov.
 * Copyright © 2026 Iakov Senatov. All rights reserved.
 */

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.7"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    // AtlantaFX — macOS-style Cupertino theme for JavaFX
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")
    // logging: Log4j2 (API + core + SLF4J bridge)
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.3")
    // lombok — @Slf4j, @Data, @Builder etc
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    // apache commons — StringUtils, CollectionUtils, FileUtils etc
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.apache.commons:commons-collections4:4.5.0-M3")
    implementation("commons-io:commons-io:2.18.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.3")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    // test
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "org.senatov.App"
}

val jpackageInputDir = layout.buildDirectory.dir("jpackage/input")
val appImageOutputDir = layout.buildDirectory.dir("jpackage/output")


val prepareJpackageInput by tasks.registering(Copy::class) {
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
    val javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile
    val jpackageExecutable = javaHome.resolve("bin/jpackage")
    val javafxModuleJars = configurations.runtimeClasspath.get().files
        .filter {
            it.name.startsWith("javafx-") && it.extension == "jar"
        }
        .sortedBy { it.name }
    val modulePathEntries = buildList {
        add(javaHome.resolve("jmods").absolutePath)
        addAll(javafxModuleJars.map { it.absolutePath })
    }
    val modulePath = modulePathEntries.joinToString(":")

    doFirst {
        outputDir.mkdirs()
        if (!iconFile.exists()) {
            throw GradleException("Missing app icon: ${iconFile.absolutePath}")
        }
        if (!jpackageExecutable.exists()) {
            throw GradleException("Missing jpackage executable: ${jpackageExecutable.absolutePath}")
        }
        val jmodsDir = javaHome.resolve("jmods")
        if (!jmodsDir.exists()) {
            throw GradleException("Missing JDK jmods directory: ${jmodsDir.absolutePath}")
        }
        if (javafxModuleJars.isEmpty()) {
            throw GradleException("Missing JavaFX runtime module jars in runtimeClasspath")
        }
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