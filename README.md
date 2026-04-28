<div align="center">

<img src="Doc/AppIcon-1024.png" alt="MiMiComparator" width="120">

# MiMiComparator

### Dual-pane directory & file comparator for macOS — Kotlin/JVM + JavaFX 26.

[![Kotlin 2.3](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](#build-and-run)
[![JDK 25](https://img.shields.io/badge/JDK-25-007396?logo=openjdk&logoColor=white)](#build-and-run)
[![JavaFX 26](https://img.shields.io/badge/JavaFX-26-0A66C2)](#about)
[![Gradle 9.4](https://img.shields.io/badge/Gradle-9.4-02303A?logo=gradle&logoColor=white)](#build-and-run)
[![macOS](https://img.shields.io/badge/macOS-Apple_Silicon%20%2F%20Intel-black?logo=apple&logoColor=white)](#about)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](#license)
[![Contributions Welcome](https://img.shields.io/badge/Contributions-Welcome-2ea44f)](#contributing)

[About](#about) •
[Features](#features) •
[Screenshot](#screenshot) •
[Build & Run](#build-and-run) •
[CLI](#cli) •
[Project Structure](#project-structure) •
[Troubleshooting](#troubleshooting) •
[License](#license)

</div>

---

> [!WARNING]
> Under active development. APIs, FXML structure, and UI details may change without notice.

## About

**MiMiComparator** is a Kotlin/JVM desktop application for comparing folders and text files side by side. It uses JavaFX 26 with FXML for the main layout and Kotlin code for the dynamic compare, home, toolbar, and tree behavior.

The app is currently focused on macOS and follows a compact native-style desktop workflow: two panels, a path bar, an icon-only toolbar, directory tree rows, synchronized scrolling, and a small status bar.

## Features

- Recursive directory comparison by relative path, size, and modified date
- Expandable tree model for directory results with synchronized left/right expansion
- Line-by-line text comparison for file mode
- Toggleable directory/file mode
- Glob-style filter field, for example `**`, `*.kt`, `*.txt`, or comma-separated patterns
- Synchronized vertical scrolling between left and right panels
- Icon-only toolbar with tooltips for compact macOS-style operation
- Home/session screen with access to auto-saved paths
- Persistent window geometry, split ratio, mode, sync-scroll state, and input paths
- CLI startup with optional automatic comparison
- Log4j2-based logging with rolling file output

## Screenshot

<p align="center">
  <img src="Doc/Preview0.png" alt="MiMiComparator screenshot" width="900">
</p>

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Application platform | **Kotlin/JVM desktop app** |
| Language | **Kotlin 2.3.21** |
| UI toolkit | **JavaFX 26** (FXML + programmatic) |
| UI style | macOS-oriented compact desktop controls |
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

## CLI

The application can start empty, restore the saved session, or compare paths immediately.

```zsh
# open normally
./gradlew run

# compare two paths
./gradlew run --args="--left /Users/me/A --right /Users/me/B"

# force directory mode
./gradlew run --args="--left /Users/me/A --right /Users/me/B --dirs"

# force file mode
./gradlew run --args="--left /Users/me/a.txt --right /Users/me/b.txt --files"
```

Positional arguments are also supported:

```zsh
./gradlew run --args="/path/to/left /path/to/right"
```

## Runtime State

User state is stored under:

```text
~/.mimi/comparator/
```

The saved state includes the last left/right paths, directory mode, synchronized scrolling, split ratio, and window placement. Logs are written through Log4j2 and rotate according to `app/src/main/resources/log4j2.xml`.

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

> Source files live under `org/senatov/` on disk while the Kotlin package is `org.senatov.mimicomparator`. The build uses Kotlin/JVM source discovery, so the physical folder name does not need to mirror the package name.

## UI Notes

- Toolbar buttons are icon-only; hover tooltips provide the command names.
- The status bar is reserved for current compare state and counts.
- Directory rows use compact macOS-style spacing with separate disclosure, icon, name, size, and modified-date fields.
- The event log is kept in code for diagnostics but hidden in the main UI.

## Troubleshooting

### Kotlin daemon permission errors

If Kotlin reports a permission error under `~/Library/Application Support/kotlin/daemon`, the project disables the external compiler daemon through:

```properties
kotlin.compiler.execution.strategy=in-process
```

This keeps compilation inside the Gradle process and avoids daemon marker files in the user Library folder.

### JavaFX native cache errors

If JavaFX cannot write to `~/.openjfx/cache`, clear the cache or run with a temporary cache directory:

```zsh
JAVA_TOOL_OPTIONS="-Djavafx.cachedir=/tmp/openjfxcache" ./gradlew run
```

### Packaging requirements

`packageMacApp` requires a full JDK with `jpackage` and `jmods`. A JRE-only installation is not enough.

## Contributing

Contributions welcome — fork, branch, PR. Code style: clean, compact, Kotlin-idiomatic.

## License

[GNU General Public License v3.0](LICENSE)

## Author

**Iakov Senatov** — [github.com/senatov](https://github.com/senatov)
