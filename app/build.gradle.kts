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
