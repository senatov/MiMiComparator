<div align="center">

<img src="Doc/mimicomparator.png" alt="MiMiComparator" width="120">

# MiMiComparator

### Dual-pane directory & file comparator — Kotlin + JavaFX 26, Total Commander style.

[![Kotlin 2.3](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](#build-and-run)
[![JDK 25](https://img.shields.io/badge/JDK-25-007396?logo=openjdk&logoColor=white)](#build-and-run)
[![JavaFX 26](https://img.shields.io/badge/JavaFX-26-0A66C2)](#about)
[![Gradle 9.4](https://img.shields.io/badge/Gradle-9.4-02303A?logo=gradle&logoColor=white)](#build-and-run)
[![macOS](https://img.shields.io/badge/macOS-Apple_Silicon%20%2F%20Intel-black?logo=apple&logoColor=white)](#about)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](#license)
[![Contributions Welcome](https://img.shields.io/badge/Contributions-Welcome-2ea44f)](#contributing)

[About](#about) •
[Screenshot](#screenshot) •
[Build & Run](#build-and-run) •
[Tech Stack](#tech-stack) •
[License](#license) •
[Author](#author)

</div>

---

> [!WARNING]
> Under active development. APIs, FXML structure, and UI details may change without notice.

## About

**MiMiComparator** is a dual-pane desktop comparator for directories and files, built in the spirit of Total Commander. It serves both as a standalone tool and as a reusable comparison component for [MiMiNavigator](https://github.com/senatov/MiMiNavigator).

Features:
- Recursive directory tree comparison (name, size, date)
- Line-by-line file diff with IntelliJ-style coloring
- TC-style zebra striping, disclosure triangles, expand/collapse all
- Glob filter field for narrowing results
- Synchronized scroll between panels
- CLI support: `MiMiComparator <left> <right>` or `--left/--right` flags
- Persistent window state & input paths via `~/.mimi/comparator/`
- AtlantaFX Cupertino theme (native macOS look)

## Screenshot

<p align="center">
  <img src="Doc/Preview0.png" alt="MiMiComparator screenshot" width="900">
</p>

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | **Kotlin 2.3.20** |
| UI toolkit | **JavaFX 26** (FXML + programmatic) |
| Theme | AtlantaFX Cupertino Light |
| JSON persistence | Jackson + jackson-module-kotlin |
| Logging | Log4j2 + SLF4J bridge |
| Utilities | Apache Commons (Lang3, Collections4, IO) |
| Build | Gradle 9.4 (Kotlin DSL) |
| JDK | 25 (Amazon Corretto / OpenJDK) |
| Packaging | `jpackage` → macOS `.app` bundle |

## Build and Run

Requires **JDK 25+**. Use the Gradle wrapper from the project root.

```zsh
# run the app
./gradlew run

# build JAR + distributions
./gradlew build

# package macOS .app bundle
./gradlew packageMacApp

# run with CLI args
./gradlew run --args="--left /path/to/dir1 --right /path/to/dir2"
```

## Project Structure

```
app/src/main/kotlin/org/senatov/
├── App.kt                          # entry point, stage, theme, fonts
├── MainController.kt               # FXML controller, compare logic, UI wiring
├── cli/
│   └── CliArgs.kt                  # CLI argument parser
├── compare/
│   ├── CompareResult.kt            # file compare result (data class)
│   ├── DirCompareResult.kt         # dir compare result (data class)
│   ├── DirectoryComparator.kt      # recursive dir tree compare
│   └── FileContentComparator.kt    # line-by-line text diff
├── helpers/log/
│   └── LogHelper.kt                # stack-trace method name helper
├── model/
│   ├── CompareLineItem.kt          # single row in compare list
│   └── tree/
│       ├── DirTreeModel.kt         # tree state, expand/collapse, flatten
│       └── DirTreeNode.kt          # single node in dir tree
└── ui/
    ├── cell/
    │   └── DiffCellFactory.kt      # ListCell factory (dir/file modes)
    └── config/
        ├── ComparatorState.kt      # persistent UI state (data class)
        └── ComparatorStateService.kt # JSON load/save via Jackson
```

## Contributing

Contributions welcome — fork, branch, PR. Code style: clean, compact, Kotlin-idiomatic.

## License

[GNU General Public License v3.0](LICENSE)

## Author

**Iakov Senatov** — [github.com/senatov](https://github.com/senatov)
