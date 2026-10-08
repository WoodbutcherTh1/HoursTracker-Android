package com.hourstracker.app.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The five tab glyphs, drawn as 24-unit outlines to match the iOS symbols (house, clock, document, share, gear).
 * Hand-drawn here because the Material icon set that ships with the app has no outlined equivalents.
 */
object TabIcons {
    private fun outline(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home: ImageVector by lazy {
        outline("TabHome", "M3,10.5L12,3L21,10.5L21,19.5A1.5,1.5 0 0 1 19.5,21L15,21L15,14.5L9,14.5L9,21L4.5,21A1.5,1.5 0 0 1 3,19.5Z")
    }

    val History: ImageVector by lazy {
        outline("TabHistory", "M12,3A9,9 0 1 0 12,21A9,9 0 1 0 12,3Z", "M12,7.5L12,12L15.2,14")
    }

    val Payslips: ImageVector by lazy {
        outline(
            "TabPayslips",
            "M6,3L14,3L19,8L19,20A1,1 0 0 1 18,21L6,21A1,1 0 0 1 5,20L5,4A1,1 0 0 1 6,3Z",
            "M14,3L14,8L19,8",
            "M8.5,13L15.5,13",
            "M8.5,17L15.5,17",
        )
    }

    val Export: ImageVector by lazy {
        outline("TabExport", "M12,15L12,3", "M7.5,7.5L12,3L16.5,7.5", "M5,12L5,19A1,1 0 0 0 6,20L18,20A1,1 0 0 0 19,19L19,12")
    }

    val Settings: ImageVector by lazy {
        outline("TabSettings", "M19.20,10.27 L21.51,10.72 L21.51,13.28 L19.20,13.73 L18.31,15.87 L19.63,17.82 L17.82,19.63 L15.87,18.31 L13.73,19.20 L13.28,21.51 L10.72,21.51 L10.27,19.20 L8.13,18.31 L6.18,19.63 L4.37,17.82 L5.69,15.87 L4.80,13.73 L2.49,13.28 L2.49,10.72 L4.80,10.27 L5.69,8.13 L4.37,6.18 L6.18,4.37 L8.13,5.69 L10.27,4.80 L10.72,2.49 L13.28,2.49 L13.73,4.80 L15.87,5.69 L17.82,4.37 L19.63,6.18 L18.31,8.13 Z", "M12,9.2A2.8,2.8 0 1 0 12,14.8A2.8,2.8 0 1 0 12,9.2Z")
    }
}
