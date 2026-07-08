package org.senatov.compare

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.senatov.model.DiffStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.test.assertEquals

class DirectoryComparatorTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `builds paired trees for identical nested directories`() {
        val left = Files.createDirectory(tempDirectory.resolve("left"))
        val right = Files.createDirectory(tempDirectory.resolve("right"))
        val leftNested = Files.createDirectory(left.resolve("nested"))
        val rightNested = Files.createDirectory(right.resolve("nested"))
        val leftFile = Files.writeString(leftNested.resolve("same.txt"), "same")
        val rightFile = Files.writeString(rightNested.resolve("same.txt"), "same")
        val timestamp = FileTime.fromMillis(1_700_000_000_000)
        Files.setLastModifiedTime(leftFile, timestamp)
        Files.setLastModifiedTime(rightFile, timestamp)

        val result = DirectoryComparator.compareTree(left, right)

        assertEquals(0, result.diffCount)
        val leftRoot = result.leftModel.roots.single()
        val rightRoot = result.rightModel.roots.single()
        assertEquals("nested", leftRoot.name)
        assertEquals("nested", rightRoot.name)
        assertEquals(0, leftRoot.depth)
        assertEquals("nested/same.txt", leftRoot.children.single().relativePath)
        assertEquals(DiffStatus.IDENTICAL, leftRoot.children.single().status)
    }

    @Test
    fun `creates a placeholder for a left-only file`() {
        val left = Files.createDirectory(tempDirectory.resolve("left-only-left"))
        val right = Files.createDirectory(tempDirectory.resolve("left-only-right"))
        Files.writeString(left.resolve("only.txt"), "left")

        val result = DirectoryComparator.compareTree(left, right)

        assertEquals(1, result.diffCount)
        assertEquals(DiffStatus.ADDED, result.leftModel.roots.single().status)
        assertEquals(DiffStatus.MISSING, result.rightModel.roots.single().status)
    }

    @Test
    fun `binary content detects changes that size mode ignores`() {
        val left = Files.createDirectory(tempDirectory.resolve("binary-left"))
        val right = Files.createDirectory(tempDirectory.resolve("binary-right"))
        Files.writeString(left.resolve("same-size.bin"), "ABCD")
        Files.writeString(right.resolve("same-size.bin"), "WXYZ")

        val sizeResult = DirectoryComparator.compareTree(left, right, CompareMode.SIZE)
        val binaryResult = DirectoryComparator.compareTree(left, right, CompareMode.BINARY_CONTENT)

        assertEquals(0, sizeResult.diffCount)
        assertEquals(1, binaryResult.diffCount)
        assertEquals(DiffStatus.MODIFIED, binaryResult.leftModel.roots.single().status)
        assertEquals(DiffStatus.MODIFIED, binaryResult.rightModel.roots.single().status)
    }
}
