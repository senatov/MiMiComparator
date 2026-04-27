/*
 * DirectoryComparator — recursive dir compare.
 * builds paired DirTreeModels for left/right panels.
 * compares by name, size, last-modified date.
 * TC-style: left-only, right-only, same, different.
 * Iakov Senatov, 2026
 */
package org.senatov.compare

import org.senatov.model.CompareLineItem.DiffStatus
import org.senatov.helpers.log.LogTag
import org.senatov.model.tree.DirTreeModel
import org.senatov.model.tree.DirTreeNode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TreeSet


object DirectoryComparator {

    private val log = LoggerFactory.getLogger(DirectoryComparator::class.java)
    private val DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())


    fun compareTree(leftDir: Path, rightDir: Path): DirCompareResult {
        log.info(LogTag.COMPARE, "tree start L={} R={}", leftDir, rightDir)
        val leftRoots = mutableListOf<DirTreeNode>()
        val rightRoots = mutableListOf<DirTreeNode>()
        val stats = TreeStats()
        buildPairedTree(leftDir, rightDir, "", 0, leftRoots, rightRoots, stats)
        log.info(LogTag.COMPARE, "tree done dirs={} files={} diffs={}", stats.dirs, stats.files, stats.diffs)
        return DirCompareResult(DirTreeModel(leftRoots), DirTreeModel(rightRoots), stats.diffs)
    }


    private fun buildPairedTree(
        leftDir: Path, rightDir: Path, pathPrefix: String, depth: Int,
        leftNodes: MutableList<DirTreeNode>, rightNodes: MutableList<DirTreeNode>,
        stats: TreeStats
    ) {
        val leftNames = safeListNames(leftDir)
        val rightNames = safeListNames(rightDir)
        val allNames = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
            addAll(leftNames); addAll(rightNames)
        }
        for (name in allNames) {
            val inLeft = name in leftNames
            val inRight = name in rightNames
            val relPath = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"
            when {
                inLeft && inRight -> handleBothSides(
                    leftDir, rightDir, name, relPath, depth, leftNodes, rightNodes, stats
                )
                inLeft -> {
                    stats.diffs++
                    val lp = leftDir.resolve(name)
                    val isDir = Files.isDirectory(lp)
                    stats.count(isDir)
                    leftNodes.add(
                        if (isDir) makeDirNode(name, relPath, depth, DiffStatus.ADDED)
                        else makeFileNode(name, relPath, lp, depth, DiffStatus.ADDED)
                    )
                    rightNodes.add(makePlaceholder(name, relPath, depth, isDir))
                }
                else -> {
                    stats.diffs++
                    val rp = rightDir.resolve(name)
                    val isDir = Files.isDirectory(rp)
                    stats.count(isDir)
                    leftNodes.add(makePlaceholder(name, relPath, depth, isDir))
                    rightNodes.add(
                        if (isDir) makeDirNode(name, relPath, depth, DiffStatus.ADDED)
                        else makeFileNode(name, relPath, rp, depth, DiffStatus.ADDED)
                    )
                }
            }
        }
    }


    private fun handleBothSides(
        leftDir: Path, rightDir: Path, name: String, relPath: String, depth: Int,
        leftNodes: MutableList<DirTreeNode>, rightNodes: MutableList<DirTreeNode>,
        stats: TreeStats
    ) {
        val lp = leftDir.resolve(name)
        val rp = rightDir.resolve(name)
        val lIsDir = Files.isDirectory(lp)
        val rIsDir = Files.isDirectory(rp)
        when {
            lIsDir && rIsDir -> {
                stats.dirs++
                val ln = makeDirNode(name, relPath, depth, DiffStatus.IDENTICAL)
                val rn = makeDirNode(name, relPath, depth, DiffStatus.IDENTICAL)
                buildPairedTree(lp, rp, relPath, depth + 1, ln.children, rn.children, stats)
                leftNodes.add(ln); rightNodes.add(rn)
            }
            !lIsDir && !rIsDir -> {
                stats.files++
                val st = compareFileAttrs(lp, rp)
                if (st != DiffStatus.IDENTICAL) stats.diffs++
                leftNodes.add(makeFileNode(name, relPath, lp, depth, st))
                rightNodes.add(makeFileNode(name, relPath, rp, depth, st))
            }
            else -> {
                stats.diffs++
                stats.count(lIsDir)
                stats.count(rIsDir)
                leftNodes.add(if (lIsDir) makeDirNode(name, relPath, depth, DiffStatus.MODIFIED)
                    else makeFileNode(name, relPath, lp, depth, DiffStatus.MODIFIED))
                rightNodes.add(if (rIsDir) makeDirNode(name, relPath, depth, DiffStatus.MODIFIED)
                    else makeFileNode(name, relPath, rp, depth, DiffStatus.MODIFIED))
            }
        }
    }


    private fun safeListNames(dir: Path?): Set<String> {
        if (dir == null || !Files.isDirectory(dir)) return emptySet()
        return try {
            Files.list(dir).use { stream ->
                stream.map { it.fileName.toString() }.collect(java.util.stream.Collectors.toSet())
            }
        } catch (ex: IOException) {
            log.warn(LogTag.IO, "list failed {}: {}", dir, ex.message)
            emptySet()
        }
    }


    private fun compareFileAttrs(left: Path, right: Path): DiffStatus {
        return try {
            val la = Files.readAttributes(left, BasicFileAttributes::class.java)
            val ra = Files.readAttributes(right, BasicFileAttributes::class.java)
            if (la.size() == ra.size() && la.lastModifiedTime() == ra.lastModifiedTime())
                DiffStatus.IDENTICAL else DiffStatus.MODIFIED
        } catch (ex: IOException) {
            log.debug(LogTag.IO, "attr compare failed L={} R={}: {}", left, right, ex.message)
            DiffStatus.MODIFIED
        }
    }


    private fun makeDirNode(name: String, relPath: String, depth: Int, status: DiffStatus) =
        DirTreeNode(name, relPath, isDirectory = true, size = 0, lastModifiedMs = 0, status = status, depth = depth)


    private fun makeFileNode(name: String, relPath: String, filePath: Path, depth: Int, status: DiffStatus): DirTreeNode {
        return try {
            val attr = Files.readAttributes(filePath, BasicFileAttributes::class.java)
            DirTreeNode(name, relPath, false, attr.size(), attr.lastModifiedTime().toMillis(), status, depth)
        } catch (ex: IOException) {
            log.debug(LogTag.IO, "attr read failed {}: {}", filePath, ex.message)
            DirTreeNode(name, relPath, false, 0, 0, status, depth)
        }
    }


    private data class TreeStats(var dirs: Int = 0, var files: Int = 0, var diffs: Int = 0) {
        fun count(isDir: Boolean) {
            if (isDir) dirs++ else files++
        }
    }


    private fun makePlaceholder(name: String, relPath: String, depth: Int, isDir: Boolean) =
        DirTreeNode("‹missing›", relPath, isDir, 0, 0, DiffStatus.MISSING, depth)


    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }


    fun formatDate(millis: Long): String =
        if (millis <= 0) "" else DATE_FMT.format(Instant.ofEpochMilli(millis))
}
