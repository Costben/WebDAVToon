package erl.webdavtoon.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Plain funnel used for every filter entry point.
 *
 * The bundled Miuix `Filter` icon draws a funnel next to three list bars; this one is a
 * single outlined funnel so the entry point reads as "filter" without the sort-bars noise.
 * Stroked to match the weight of the other Miuix toolbar icons.
 */
val FunnelIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Funnel",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.8f, 4.9f)
            lineTo(20.2f, 4.9f)
            lineTo(13.4f, 12.7f)
            lineTo(13.4f, 20.1f)
            lineTo(10.6f, 17.5f)
            lineTo(10.6f, 12.7f)
            close()
        }
    }.build()
}
