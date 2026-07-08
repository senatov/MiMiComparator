package org.senatov.compare

import org.senatov.helpers.log.LogTag
import org.senatov.model.DiffStatus
import org.senatov.model.FileMetadata
import org.senatov.model.tree.DirTreeModel
import org.senatov.model.tree.DirTreeNode
import org.senatov.model.tree.TreeEntryDetails
import org.senatov.model.tree.TreeLocation
import org.senatov.model.tree.TreeNodeIdentity
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.stream.Collectors

internal class DirectoryTreeBuilder(private val compareMode: CompareMode) {
    private val log = LoggerFactory.getLogger(DirectoryTreeBuilder::class.java)
    private val stats = TreeStats()

    fun build(directories: DirectoryPair): DirCompareResult {
        log.debug(LogTag.COMPARE, "build(directories={})", directories)
        val roots = buildPairedTree(directories, TreePosition.ROOT)
        log.info(
            LogTag.COMPARE,
            "tree done dirs={} files={} diffs={}",
            stats.directories,
            stats.files,
            stats.differences,
        )
        return DirCompareResult(
            leftModel = DirTreeModel(roots.left),
            rightModel = DirTreeModel(roots.right),
            diffCount = stats.differences,
        )
    }

    private fun buildPairedTree(directories: DirectoryPair, position: TreePosition): PairedTreeNodes {
        log.debug(LogTag.COMPARE, "buildPairedTree(directories={}, position={})", directories, position)
        val leftNames = listNames(directories.left)
        val rightNames = listNames(directories.right)
        val allNames = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
            addAll(leftNames)
            addAll(rightNames)
        }
        val nodes = PairedTreeNodes()
        for (name in allNames) {
            val childPosition = position.child(name)
            when {
                name in leftNames && name in rightNames -> nodes.add(
                    handleBothSides(directories, name, childPosition)
                )

                name in leftNames -> nodes.add(buildLeftOnly(directories.left, name, childPosition))
                else -> nodes.add(buildRightOnly(directories.right, name, childPosition))
            }
        }
        return nodes
    }

    private fun handleBothSides(
        directories: DirectoryPair,
        name: String,
        position: TreePosition,
    ): PairedTreeNodes {
        log.debug(
            LogTag.COMPARE,
            "handleBothSides(directories={}, name={}, position={})",
            directories,
            name,
            position,
        )
        val leftPath = directories.left.resolve(name)
        val rightPath = directories.right.resolve(name)
        val paths = DirectoryPair(leftPath, rightPath)
        val leftIsDirectory = Files.isDirectory(leftPath)
        val rightIsDirectory = Files.isDirectory(rightPath)
        return when {
            leftIsDirectory && rightIsDirectory -> buildMatchingDirectories(
                paths,
                TreeNodeSpec(name, position, DiffStatus.IDENTICAL),
            )

            !leftIsDirectory && !rightIsDirectory -> buildMatchingFiles(
                paths,
                TreeNodeSpec(name, position, DiffStatus.IDENTICAL),
            )

            else -> buildTypeMismatch(
                paths,
                TreeNodeSpec(name, position, DiffStatus.MODIFIED),
            )
        }
    }

    private fun buildMatchingDirectories(
        paths: DirectoryPair,
        spec: TreeNodeSpec,
    ): PairedTreeNodes {
        log.debug(LogTag.COMPARE, "buildMatchingDirectories(paths={}, spec={})", paths, spec)
        stats.directories++
        val leftNode = makeDirectoryNode(spec)
        val rightNode = makeDirectoryNode(spec)
        val children = buildPairedTree(paths, spec.position)
        leftNode.children.addAll(children.left)
        rightNode.children.addAll(children.right)
        return PairedTreeNodes.of(leftNode, rightNode)
    }

    private fun buildMatchingFiles(
        paths: DirectoryPair,
        spec: TreeNodeSpec,
    ): PairedTreeNodes {
        log.debug(LogTag.COMPARE, "buildMatchingFiles(paths={}, spec={})", paths, spec)
        stats.files++
        val status = compareFileAttributes(paths.left, paths.right)
        if (status != DiffStatus.IDENTICAL) stats.differences++
        val comparedSpec = spec.copy(status = status)
        return PairedTreeNodes.of(
            makeFileNode(comparedSpec, paths.left),
            makeFileNode(comparedSpec, paths.right),
        )
    }

    private fun buildTypeMismatch(
        paths: DirectoryPair,
        spec: TreeNodeSpec,
    ): PairedTreeNodes {
        log.debug(LogTag.COMPARE, "buildTypeMismatch(paths={}, spec={})", paths, spec)
        val leftIsDirectory = Files.isDirectory(paths.left)
        val rightIsDirectory = Files.isDirectory(paths.right)
        stats.differences++
        stats.count(leftIsDirectory)
        stats.count(rightIsDirectory)
        val leftNode = if (leftIsDirectory) makeDirectoryNode(spec) else makeFileNode(spec, paths.left)
        val rightNode = if (rightIsDirectory) makeDirectoryNode(spec) else makeFileNode(spec, paths.right)
        return PairedTreeNodes.of(leftNode, rightNode)
    }

    private fun buildSingleSideNode(directory: Path, name: String, position: TreePosition): DirTreeNode {
        log.debug(LogTag.COMPARE, "buildSingleSideNode(directory={}, name={}, position={})", directory, name, position)
        stats.differences++
        val path = directory.resolve(name)
        val isDirectory = Files.isDirectory(path)
        stats.count(isDirectory)
        val spec = TreeNodeSpec(name, position, DiffStatus.ADDED)
        return if (isDirectory) makeDirectoryNode(spec) else makeFileNode(spec, path)
    }

    private fun buildLeftOnly(directory: Path, name: String, position: TreePosition): PairedTreeNodes {
        log.debug(LogTag.COMPARE, "buildLeftOnly(directory={}, name={}, position={})", directory, name, position)
        val leftNode = buildSingleSideNode(directory, name, position)
        val placeholderSpec = TreeNodeSpec(name, position, DiffStatus.MISSING)
        return PairedTreeNodes.of(leftNode, makePlaceholder(placeholderSpec, leftNode.isDirectory))
    }

    private fun buildRightOnly(directory: Path, name: String, position: TreePosition): PairedTreeNodes {
        log.debug(LogTag.COMPARE, "buildRightOnly(directory={}, name={}, position={})", directory, name, position)
        val rightNode = buildSingleSideNode(directory, name, position)
        val placeholderSpec = TreeNodeSpec(name, position, DiffStatus.MISSING)
        return PairedTreeNodes.of(makePlaceholder(placeholderSpec, rightNode.isDirectory), rightNode)
    }

    private fun listNames(directory: Path): Set<String> {
        log.debug(LogTag.IO, "listNames(directory={})", directory)
        if (!Files.isDirectory(directory)) return emptySet()
        return try {
            Files.list(directory).use { stream ->
                stream.map { it.fileName.toString() }.collect(Collectors.toSet())
            }
        }
        catch (ex: IOException) {
            log.warn(LogTag.IO, "list failed {}: {}", directory, ex.message)
            emptySet()
        }
    }

    private fun compareFileAttributes(left: Path, right: Path): DiffStatus {
        log.debug(LogTag.COMPARE, "compareFileAttributes(left={}, right={})", left, right)
        return try {
            val leftAttributes = Files.readAttributes(left, BasicFileAttributes::class.java)
            val rightAttributes = Files.readAttributes(right, BasicFileAttributes::class.java)
            val identical = when (compareMode) {
                CompareMode.BINARY_CONTENT -> Files.mismatch(left, right) == -1L
                CompareMode.TEXT -> Files.readString(left) == Files.readString(right)
                CompareMode.SIZE -> leftAttributes.size() == rightAttributes.size()
                CompareMode.SIZE_AND_TIMESTAMP -> leftAttributes.size() == rightAttributes.size() &&
                        leftAttributes.lastModifiedTime() == rightAttributes.lastModifiedTime()
            }
            if (identical) {
                DiffStatus.IDENTICAL
            } else {
                DiffStatus.MODIFIED
            }
        }
        catch (ex: IOException) {
            log.debug(LogTag.IO, "attribute comparison failed left={} right={}: {}", left, right, ex.message)
            DiffStatus.MODIFIED
        }
    }

    private fun makeDirectoryNode(spec: TreeNodeSpec): DirTreeNode {
        log.debug(LogTag.COMPARE, "makeDirectoryNode(spec={})", spec)
        return DirTreeNode(
            identity = TreeNodeIdentity(name = spec.name, status = spec.status),
            details = treeEntryDetails(spec.position, isDirectory = true, metadata = FileMetadata.EMPTY),
        )
    }

    private fun makeFileNode(spec: TreeNodeSpec, filePath: Path): DirTreeNode {
        log.debug(LogTag.COMPARE, "makeFileNode(spec={}, filePath={})", spec, filePath)
        return try {
            val attributes = Files.readAttributes(filePath, BasicFileAttributes::class.java)
            DirTreeNode(
                identity = TreeNodeIdentity(name = spec.name, status = spec.status),
                details = treeEntryDetails(
                    position = spec.position,
                    isDirectory = false,
                    metadata = FileMetadata(attributes.size(), attributes.lastModifiedTime().toMillis()),
                ),
            )
        }
        catch (ex: IOException) {
            log.debug(LogTag.IO, "attribute read failed {}: {}", filePath, ex.message)
            DirTreeNode(
                identity = TreeNodeIdentity(name = spec.name, status = spec.status),
                details = treeEntryDetails(spec.position, isDirectory = false, metadata = FileMetadata.EMPTY),
            )
        }
    }

    private fun makePlaceholder(spec: TreeNodeSpec, isDirectory: Boolean): DirTreeNode {
        log.debug(LogTag.COMPARE, "makePlaceholder(spec={}, isDirectory={})", spec, isDirectory)
        return DirTreeNode(
            identity = TreeNodeIdentity(name = "‹missing›", status = DiffStatus.MISSING),
            details = treeEntryDetails(spec.position, isDirectory, FileMetadata.EMPTY),
        )
    }

    private fun treeEntryDetails(
        position: TreePosition,
        isDirectory: Boolean,
        metadata: FileMetadata,
    ): TreeEntryDetails {
        log.debug(
            LogTag.COMPARE,
            "treeEntryDetails(position={}, isDirectory={}, metadata={})",
            position,
            isDirectory,
            metadata,
        )
        return TreeEntryDetails(
            location = TreeLocation(position.pathPrefix, position.depth - 1),
            isDirectory = isDirectory,
            metadata = metadata,
        )
    }
}