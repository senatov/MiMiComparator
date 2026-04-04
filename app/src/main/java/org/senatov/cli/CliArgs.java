/*
 * CliArgs.java — CLI argument parser for MiMiComparator.
 * Supports: MiMiComparator <left> <right>
 *           MiMiComparator --left <path> --right <path>
 * Auto-detects file vs dir mode.
 * Iakov Senatov, 2026
 */
package org.senatov.cli;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;


@Slf4j
@Getter
public final class CliArgs {

    private final Path leftPath;
    private final Path rightPath;
    private final boolean autoCompare;
    private final boolean dirMode;


    private CliArgs(Path leftPath, Path rightPath) {
        this.leftPath = leftPath;
        this.rightPath = rightPath;
        this.autoCompare = leftPath != null && rightPath != null;
        this.dirMode = detectDirMode(leftPath, rightPath);
    }


    public static CliArgs parse(List<String> rawArgs) {
        log.info("parsing CLI args: {}", rawArgs);

        if (rawArgs == null || rawArgs.isEmpty()) {
            log.info("no CLI args — GUI chooser mode");
            return new CliArgs(null, null);
        }

        Path left = null;
        Path right = null;

        for (int i = 0; i < rawArgs.size(); i++) {
            String arg = rawArgs.get(i);

            if ("--left".equals(arg) && i + 1 < rawArgs.size()) {
                left = resolvePath(rawArgs.get(++i));
            } else if ("--right".equals(arg) && i + 1 < rawArgs.size()) {
                right = resolvePath(rawArgs.get(++i));
            } else if (!arg.startsWith("-") && left == null) {
                left = resolvePath(arg);
            } else if (!arg.startsWith("-") && right == null) {
                right = resolvePath(arg);
            } else {
                log.warn("ignoring unknown CLI arg: {}", arg);
            }
        }

        log.info("parsed CLI: left={} right={} autoCompare={}",
                left, right, left != null && right != null);
        return new CliArgs(left, right);
    }


    public Optional<Path> left() {
        return Optional.ofNullable(leftPath);
    }


    public Optional<Path> right() {
        return Optional.ofNullable(rightPath);
    }


    private static Path resolvePath(String raw) {
        Path path = Path.of(raw).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            log.warn("CLI path does not exist: {}", path);
            return null;
        }

        return path;
    }


    private static boolean detectDirMode(Path left, Path right) {
        if (left != null && Files.isDirectory(left)) {
            return true;
        }

        if (right != null && Files.isDirectory(right)) {
            return true;
        }

        return false;
    }
}
