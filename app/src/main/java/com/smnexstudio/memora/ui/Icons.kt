package com.smnexstudio.memora.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24-unit grid, stroked like the mockups. */
enum class Ic(vararg val paths: String) {
    SEARCH("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0", "M20 20l-3.5-3.5"),
    HOME("M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"),
    CALENDAR("M4 5h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M3 10h18M8 3v4M16 3v4"),
    PLUS("M12 5v14M5 12h14"),
    PEOPLE("M13 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M2 21c0-4 3-6 7-6s7 2 7 6M17 11a3 3 0 1 0 0-6M22 21c0-3-2-5-5-5"),
    SETTINGS("M4 7h10M18 7h2M4 17h4M12 17h8", "M18 7a2 2 0 1 1-4 0a2 2 0 1 1 4 0", "M12 17a2 2 0 1 1-4 0a2 2 0 1 1 4 0"),
    BACK("M19 12H5M11 6l-6 6 6 6"),
    CLOSE("M6 6l12 12M18 6L6 18"),
    RIGHT("M9 6l6 6-6 6"),
    LEFT("M15 6l-6 6 6 6"),
    MAIL("M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M3 7l9 6 9-6"),
    PHONE("M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z"),
    BELL("M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9", "M13.7 21a2 2 0 0 1-3.4 0"),
    REFRESH("M21 12a9 9 0 1 1-3-6.7L21 8", "M21 3v5h-5"),
    CHECK("M20 6L9 17l-5-5"),
    CHAT("M21 11.5a8.4 8.4 0 0 1-12.4 7.4L3 21l2.1-5.4A8.4 8.4 0 1 1 21 11.5z"),
    SMS("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"),
    POSTCARD("M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M13 5v14M16 9h2M16 12h2"),
    LINES("M4 7h16M4 12h16M4 17h10"),
    ALERT("M12 7v6M12 17h.01"),
    LOCK("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4"),
    CAKE("M4 21h16M5 21v-8h14v8M12 13V9M12 6a1.5 1.5 0 0 1-1.5-1.5C10.5 3.5 12 2 12 2s1.5 1.5 1.5 2.5A1.5 1.5 0 0 1 12 6z"),
    RINGS("M14 14a5 5 0 1 1-10 0a5 5 0 1 1 10 0", "M20 14a5 5 0 1 1-10 0a5 5 0 1 1 10 0"),
    HOUSE("M3 11l9-7 9 7v9H3z"),
    FINGERPRINT(
        "M12 11v3c0 2.5-.6 4.7-1.7 6.5",
        "M8.6 7.6A5 5 0 0 1 17 11v2.5c0 1.6-.2 3.1-.6 4.5",
        "M7 11c0 1.5 0 3.8-1 5.5",
        "M5 7.5A8 8 0 0 1 20 11v1.5",
        "M14.5 21c.4-1 .8-2.4 1-4",
    ),
}

private val cache = HashMap<Ic, ImageVector>()

private fun vector(icon: Ic, stroke: Float): ImageVector = cache.getOrPut(icon) {
    ImageVector.Builder(icon.name, 24.dp, 24.dp, 24f, 24f).apply {
        icon.paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** Decorative unless [label] is given. */
@Composable
fun LineIcon(icon: Ic, color: Color, modifier: Modifier = Modifier, size: Int = 22, label: String? = null) {
    Icon(vector(icon, 2f), contentDescription = label, tint = color, modifier = modifier.size(size.dp))
}
