<div align="center">

<img src="Doc/mimicomparator.png" alt="MiMiComparator" width="120">

# MiMiComparator

### Directory and file comparison and synchronization tool for Java 21+ / JavaFX, designed for standalone use and integration into MiMiNavigator.

[![Java 21+](https://img.shields.io/badge/Java-21%2B-007396?logo=openjdk&logoColor=white)](#build-and-run-with-gradle)
[![JavaFX 21+](https://img.shields.io/badge/JavaFX-21%2B-0A66C2)](#about)
[![Gradle Wrapper](https://img.shields.io/badge/Gradle-Wrapper-02303A?logo=gradle&logoColor=white)](#build-and-run-with-gradle)
[![macOS](https://img.shields.io/badge/macOS-Apple_Silicon%20%2F%20Intel-black?logo=apple&logoColor=white)](#about)
[![Contributions Welcome](https://img.shields.io/badge/Contributions-Welcome-2ea44f)](#contributing)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](#license)

[About](#about) •
[Screenshot](#screenshot) •
[Build and Run](#build-and-run-with-gradle) •
[License](#license) •
[Author](#author)

</div>

---

> [!WARNING]
> Under active development. APIs, FXML structure, and UI details may change without notice.

## About

**MiMiComparator** is a desktop application for:

- comparing directories
- comparing files
- synchronizing content between locations
- serving as a reusable comparison/synchronization component for **MiMiNavigator**

The project is written in **Java 21+** and **JavaFX** and is intended to remain open for contribution and redistribution.

## Screenshot

<p align="center">
  <img src="Doc/Preview0.png" alt="MiMiComparator screenshot" width="900">
</p>

## Build and Run with Gradle

Use the Gradle wrapper from the project root.

### Run the application

```bash
./gradlew run