package com.malina.seanimetvport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build-verification tests for Task Group 1 (Component Relocation).
 *
 * These tests assert the post-relocation file state by reading source files
 * directly. They are expected to fail before implementation and pass after:
 *
 * 1. components/ActionButton.kt must declare package com.malina.seanimetvport.components
 * 2. SeriesDetailScreen.kt must not import ActionButton from feature.home
 */
class ComponentRelocationTest {

    /**
     * shouldDeclareComponentsPackage_inActionButtonFile
     *
     * After step 1.2, components/ActionButton.kt must be created with
     * `package com.malina.seanimetvport.components`. Fails before the file exists.
     */
    @Test
    fun shouldDeclareComponentsPackage_inActionButtonFile() {
        val file = resolveSourceFile("components/ActionButton.kt")
        assertTrue(
            "components/ActionButton.kt must contain 'package com.malina.seanimetvport.components'",
            file.readText().contains("package com.malina.seanimetvport.components")
        )
    }

    /**
     * shouldNotImportActionButtonFromFeatureHome_inSeriesDetailScreen
     *
     * After step 1.6, SeriesDetailScreen.kt must not contain the string
     * 'feature.home.ActionButton' — the import must point to components instead.
     */
    @Test
    fun shouldNotImportActionButtonFromFeatureHome_inSeriesDetailScreen() {
        val file = resolveSourceFile("feature/series/SeriesDetailScreen.kt")
        assertFalse(
            "SeriesDetailScreen.kt must not contain 'feature.home.ActionButton'",
            file.readText().contains("feature.home.ActionButton")
        )
    }

    /**
     * Resolves a source file path relative to the seanimetv package root.
     * In Android Gradle unit tests, System.getProperty("user.dir") is the module
     * directory (app/), so the main sources live under src/main/java/com/malina/seanimetvport/.
     */
    private fun resolveSourceFile(relativePath: String): File {
        val base = File(System.getProperty("user.dir"))
        val fromModule = File(base, "src/main/java/com/malina/seanimetvport/$relativePath")
        if (fromModule.exists()) return fromModule
        // Fallback when user.dir is the project root (e.g. some IDE configurations)
        val fromProject = File(base, "app/src/main/java/com/malina/seanimetvport/$relativePath")
        if (fromProject.exists()) return fromProject
        error(
            "Could not locate source file: $relativePath\n" +
                "  Tried: $fromModule\n" +
                "       : $fromProject"
        )
    }
}
