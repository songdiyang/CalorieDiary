package com.calorie.diary

import com.calorie.diary.data.update.UpdateInfo
import com.calorie.diary.data.update.UpdateState
import com.calorie.diary.data.update.formatUpdateSize
import com.calorie.diary.data.update.parseUpdateManifest
import com.calorie.diary.data.update.sha256Hex
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val sample = """
        {
          "version": "0.24",
          "versionCode": 37,
          "channel": "debug",
          "releasedAt": "2026-11-24",
          "size": "32.5 MB",
          "sizeBytes": 34055610,
          "downloadUrl": "https://caloriediary.aetherstudio.cn/downloads/CalorieDiary-debug.apk",
          "sha256": "8552aab786da9284cd167182e0f8b75832dc264ef41ad0eb653ced85ead10ee6",
          "releaseNotes": ["修复问题", "优化体验"]
        }
    """.trimIndent()

    @Test
    fun parsesCurrentServerManifest() {
        val info = parseUpdateManifest(sample)!!
        assertEquals("0.24", info.versionName)
        assertEquals(37, info.versionCode)
        assertEquals("32.5 MB", info.sizeLabel)
        assertEquals(34055610L, info.sizeBytes)
        assertEquals(2, info.releaseNotes.size)
        assertEquals("2026-11-24", info.releasedAt)
        assertEquals(
            "https://caloriediary.aetherstudio.cn/downloads/CalorieDiary-debug.apk",
            info.downloadUrl
        )
    }

    @Test
    fun comparesVersionCodeOnly() {
        val info = UpdateInfo("0.24", 37, 0, "", "https://example.com/a.apk", null, emptyList(), null)
        assertTrue(info.isNewerThan(36))
        assertFalse(info.isNewerThan(37))
        assertFalse(info.isNewerThan(38))
    }

    @Test
    fun rejectsInsecureOrIncompleteManifest() {
        assertNull(
            parseUpdateManifest(sample.replace("https://", "http://"))
        )
        assertNull(
            parseUpdateManifest(sample.replace("\"versionCode\": 37,", ""))
        )
        assertNull(parseUpdateManifest("not json"))
    }

    @Test
    fun downloadPercentHandlesKnownAndUnknownTotal() {
        val info = UpdateInfo("0.24", 37, 0, "", "https://example.com/a.apk", null, emptyList(), null)
        assertEquals(50, UpdateState.Downloading(info, 50L, 100L).percent)
        assertEquals(0, UpdateState.Downloading(info, 0L, 100L).percent)
        assertNull(UpdateState.Downloading(info, 10L, 0L).percent)
    }

    @Test
    fun formatsDownloadSize() {
        assertEquals("32.5 MB", formatUpdateSize(34055610L))
        assertEquals("512 KB", formatUpdateSize(512L * 1024L))
        assertEquals("0 MB", formatUpdateSize(0L))
    }

    @Test
    fun computesSha256OfFile() {
        val file = File.createTempFile("update-test", ".bin")
        try {
            file.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                sha256Hex(file)
            )
        } finally {
            file.delete()
        }
    }
}