package com.otto.launcher.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BoundariesTest {
    private val appDir = File(System.getProperty("user.dir").orEmpty()).let {
        if (File(it, "src/main/java").isDirectory) it else File(it, "app")
    }
    private val sourceRoot = File(appDir, "src/main/java/com/otto/launcher")
    private val productionFiles get() = sourceRoot.walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun `Konsist discovers production architecture`() {
        assertTrue(Konsist.scopeFromProduction().files.isNotEmpty())
    }

    @Test fun `domain is pure Kotlin`() {
        val forbidden = listOf("import android.", "import androidx.room.", "import androidx.compose.", "import okhttp3.")
        productionFiles.filter { "/domain/" in it.invariantSeparatorsPath }.forEach { file ->
            forbidden.forEach { prefix -> assertTrue("${file.name} imports $prefix", prefix !in file.readText()) }
        }
    }

    @Test fun `Groq endpoints are confined to core llm`() {
        productionFiles.filterNot { "/core/llm/" in it.invariantSeparatorsPath }.forEach { file ->
            assertTrue("Groq URL escaped into ${file.name}", "api.groq.com" !in file.readText())
        }
    }

    @Test fun `root package is frozen`() {
        val allowed = setOf(
            "OttoApp.kt", "MainActivity.kt", "OttoDeviceAdminReceiver.kt", "OttoPolicyEventsReceiver.kt",
            "OttoDnsVpnService.kt", "OttoPackageInstallReceiver.kt", "OttoDiagnostics.kt", "ProcessingOverlay.kt"
        )
        val rootFiles = sourceRoot.listFiles().orEmpty().filter { it.extension == "kt" }.map { it.name }.toSet()
        assertEquals(allowed, rootFiles)
    }

    @Test fun `production files stay within recorded size limits`() {
        val grandfathered = mapOf("LauncherScreen.kt" to 1739)
        productionFiles.forEach { file ->
            val lines = file.readLines().size
            val limit = grandfathered[file.name] ?: 800
            assertTrue("${file.name} grew to $lines lines (limit $limit)", lines <= limit)
        }
    }

    @Test fun `composable review screens do not import data`() {
        listOf("FoodReviewScreen.kt", "InboxReviewScreen.kt", "TranscriptViewerScreen.kt").forEach { name ->
            val file = File(sourceRoot, "ui/review/$name")
            assertTrue("$name imports data directly", !Regex("^import .*\\.data\\.", RegexOption.MULTILINE).containsMatchIn(file.readText()))
        }
    }

    @Test fun `MainActivity is only wiring`() {
        val activity = File(sourceRoot, "MainActivity.kt")
        assertTrue(activity.readLines().size <= 900)
        assertEquals(1, Regex("^class ", RegexOption.MULTILINE).findAll(activity.readText()).count())
    }
}
