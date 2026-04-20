/*
 * CliArgs — CLI argument parser 4 MiMiComparator.
 * MiMiComparator <left> <right>
 * MiMiComparator --left <path> --right <path>
 * auto-detects file vs dir mode.
 * Iakov Senatov, 2026
 */
package org.senatov.cli

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path


class CliArgs private constructor(
    val leftPath: Path?,
    val rightPath: Path?,
    val autoCompare: Boolean,
    val isDirMode: Boolean,
    private val dirModeExplicit: Boolean
) {

    fun left(): Path? = leftPath
    fun right(): Path? = rightPath
    fun hasExplicitDirMode(): Boolean = dirModeExplicit


    companion object {
        private val log = LoggerFactory.getLogger(CliArgs::class.java)

        fun parse(rawArgs: List<String>?): CliArgs {
            log.info("parsing CLI args: {}", rawArgs)
            if (rawArgs.isNullOrEmpty()) {
                log.info("no CLI args — GUI chooser mode")
                return CliArgs(null, null, false, false, false)
            }
            var left: Path? = null
            var right: Path? = null
            var forcedDirMode: Boolean? = null
            var i = 0
            while (i < rawArgs.size) {
                val arg = rawArgs[i]
                when {
                    arg == "--left" && i + 1 < rawArgs.size -> { left = resolvePath(rawArgs[++i]) }
                    arg == "--right" && i + 1 < rawArgs.size -> { right = resolvePath(rawArgs[++i]) }
                    arg == "--dirs" -> forcedDirMode = true
                    arg == "--files" -> forcedDirMode = false
                    !arg.startsWith("-") && left == null -> left = resolvePath(arg)
                    !arg.startsWith("-") && right == null -> right = resolvePath(arg)
                    else -> log.warn("ignoring unknown CLI arg: {}", arg)
                }
                i++
            }
            val dirMode = forcedDirMode ?: detectDirMode(left, right)
            val auto = left != null && right != null
            log.info("parsed CLI: left={} right={} auto={} dirMode={} explicit={}",
                left, right, auto, dirMode, forcedDirMode != null)
            return CliArgs(left, right, auto, dirMode, forcedDirMode != null)
        }


        private fun resolvePath(raw: String): Path? {
            val path = Path.of(raw).toAbsolutePath().normalize()
            if (!Files.exists(path)) {
                log.warn("CLI path does not exist: {}", path)
                return null
            }
            return path
        }

        private fun detectDirMode(left: Path?, right: Path?): Boolean {
            if (left != null && Files.isDirectory(left)) return true
            if (right != null && Files.isDirectory(right)) return true
            return false
        }
    }
}
