package ir.dorehami.games.dejban.ui

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DejbanPackExtractorTest {

    @Test
    fun `extracts initial pack and replaces stale versioned or older hash packs on update`() {
        val packDir = Files.createTempDirectory("dejban-pack-test").toFile()
        try {
            // Simulate a leftover legacy dezhban-v1.pck from an older APK install.
            val legacy = File(packDir, "dezhban-v1.pck").apply {
                writeBytes("stale-v1-pack".encodeToByteArray())
            }
            assertTrue(legacy.isFile)

            val v1Bytes = "new-pack-payload-one".encodeToByteArray()
            val first = DejbanPackExtractor.extractIfNeeded(packDir) {
                ByteArrayInputStream(v1Bytes)
            }
            assertFalse(legacy.exists(), "Legacy dezhban-v1.pck should be cleaned up")
            assertTrue(first.isFile)
            assertContentEquals(v1Bytes, first.readBytes())

            // Re-running with identical content reuses the same file without extra files left over.
            val reused = DejbanPackExtractor.extractIfNeeded(packDir) {
                ByteArrayInputStream(v1Bytes)
            }
            assertEquals(first, reused)
            assertEquals(listOf(first.name), packDir.list()?.toList().orEmpty())

            // Updating the APK pack bytes (even with the same versionCode) extracts the new pack
            // and removes the previous extracted file.
            val v2Bytes = "new-pack-payload-two-with-tower-upgrade-fix".encodeToByteArray()
            val second = DejbanPackExtractor.extractIfNeeded(packDir) {
                ByteArrayInputStream(v2Bytes)
            }
            assertNotEquals(first, second)
            assertFalse(first.exists(), "Old hash pack should be removed on update")
            assertTrue(second.isFile)
            assertContentEquals(v2Bytes, second.readBytes())
        } finally {
            packDir.deleteRecursively()
        }
    }
}
