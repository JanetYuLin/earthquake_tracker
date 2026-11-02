package io.github.janetyulin.grounded.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards WCAG AA (4.5:1) for text-on-color role pairs if the palette is regenerated. */
class ColorContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertReadable(name: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("$name contrast was %.2f".format(ratio), ratio >= 4.5)
    }

    @Test
    fun `light scheme text roles meet AA`() {
        assertReadable("onPrimary", onPrimaryLight, primaryLight)
        assertReadable("onPrimaryContainer", onPrimaryContainerLight, primaryContainerLight)
        assertReadable("onSecondary", onSecondaryLight, secondaryLight)
        assertReadable("onSecondaryContainer", onSecondaryContainerLight, secondaryContainerLight)
        assertReadable("onTertiary", onTertiaryLight, tertiaryLight)
        assertReadable("onTertiaryContainer", onTertiaryContainerLight, tertiaryContainerLight)
        assertReadable("onSurface", onSurfaceLight, surfaceLight)
        assertReadable("onSurfaceVariant", onSurfaceVariantLight, surfaceVariantLight)
        assertReadable("onError", onErrorLight, errorLight)
    }

    @Test
    fun `dark scheme text roles meet AA`() {
        assertReadable("onPrimary", onPrimaryDark, primaryDark)
        assertReadable("onPrimaryContainer", onPrimaryContainerDark, primaryContainerDark)
        assertReadable("onSecondary", onSecondaryDark, secondaryDark)
        assertReadable("onSecondaryContainer", onSecondaryContainerDark, secondaryContainerDark)
        assertReadable("onTertiary", onTertiaryDark, tertiaryDark)
        assertReadable("onTertiaryContainer", onTertiaryContainerDark, tertiaryContainerDark)
        assertReadable("onSurface", onSurfaceDark, surfaceDark)
        assertReadable("onSurfaceVariant", onSurfaceVariantDark, surfaceVariantDark)
        assertReadable("onError", onErrorDark, errorDark)
    }

    @Test
    fun `severity badge text meets AA`() {
        assertReadable("minor", onSeverityMinor, severityMinor)
        assertReadable("light", onSeverityLight, severityLight)
        assertReadable("moderate", onSeverityModerate, severityModerate)
        assertReadable("strong", onSeverityStrong, severityStrong)
        assertReadable("major", onSeverityMajor, severityMajor)
    }
}
