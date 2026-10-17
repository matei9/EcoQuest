package com.greenguardians.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenguardians.app.model.League
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Dark = Color(0xFF0B0F14)

private fun polyPath(c: Offset, r: Float, n: Int): Path {
    val p = Path()
    for (i in 0 until n) {
        val a = Math.toRadians(-90.0 + i * 360.0 / n)
        val x = c.x + r * cos(a).toFloat()
        val y = c.y + r * sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun starPath(c: Offset, ro: Float, ri: Float, n: Int): Path {
    val p = Path()
    for (i in 0 until 2 * n) {
        val r = if (i % 2 == 0) ro else ri
        val a = Math.toRadians(-90.0 + i * 180.0 / n)
        val x = c.x + r * cos(a).toFloat()
        val y = c.y + r * sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** Leaf shape between two points; two curves bulging by [bend]. */
private fun leafPath(from: Offset, to: Offset, bend: Float): Path {
    val mx = (from.x + to.x) / 2
    val my = (from.y + to.y) / 2
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx, dy).coerceAtLeast(0.001f)
    val nx = -dy / len * bend
    val ny = dx / len * bend
    return Path().apply {
        moveTo(from.x, from.y)
        cubicTo(mx + nx, my + ny, mx + nx, my + ny, to.x, to.y)
        cubicTo(mx - nx, my - ny, mx - nx, my - ny, from.x, from.y)
        close()
    }
}

private fun DrawScope.sprout(c: Offset, r: Float, col: Color) {
    drawLine(col, Offset(c.x, c.y + r * 0.6f), Offset(c.x, c.y), strokeWidth = r * 0.09f)
    val b = Offset(c.x, c.y + r * 0.05f)
    drawPath(leafPath(b, Offset(c.x - r * 0.5f, c.y - r * 0.45f), r * 0.2f), col)
    drawPath(leafPath(b, Offset(c.x + r * 0.5f, c.y - r * 0.45f), r * 0.2f), col)
}

private fun DrawScope.single(c: Offset, r: Float, col: Color) {
    drawLine(col, Offset(c.x - r * 0.3f, c.y + r * 0.5f), Offset(c.x + r * 0.1f, c.y - r * 0.1f), strokeWidth = r * 0.07f)
    drawPath(leafPath(Offset(c.x - r * 0.25f, c.y + r * 0.45f), Offset(c.x + r * 0.45f, c.y - r * 0.5f), r * 0.28f), col)
}

private fun DrawScope.globe(c: Offset, r: Float, col: Color) {
    val st = Stroke(r * 0.07f)
    drawCircle(col.copy(alpha = 0.18f), r, c)
    drawCircle(col, r, c, style = st)
    drawOval(col, Offset(c.x - r * 0.45f, c.y - r), Size(r * 0.9f, r * 2), style = st)
    drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = r * 0.07f)
    drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = r * 0.07f)
}

@Composable
fun LeagueIcon(league: League, iconSize: Dp = 64.dp, glow: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        val c = Offset(s / 2, s / 2)
        val r = s * 0.44f
        val col = league.color
        val thin = Stroke(s * 0.035f)
        val thick = Stroke(s * 0.05f)
        if (glow) {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.5f), Color.Transparent), c, s / 2), s / 2, c)
        }
        when (league) {
            League.BRONZE -> {
                drawCircle(col, r, c, style = thick)
                drawCircle(col, r * 0.82f, c, style = thin)
                sprout(c, r * 0.8f, col)
            }
            League.SILVER -> {
                val p = Path().apply {
                    moveTo(c.x - r * 0.85f, c.y - r * 0.85f)
                    lineTo(c.x + r * 0.85f, c.y - r * 0.85f)
                    lineTo(c.x + r * 0.85f, c.y + r * 0.1f)
                    cubicTo(c.x + r * 0.85f, c.y + r * 0.65f, c.x + r * 0.3f, c.y + r * 0.9f, c.x, c.y + r)
                    cubicTo(c.x - r * 0.3f, c.y + r * 0.9f, c.x - r * 0.85f, c.y + r * 0.65f, c.x - r * 0.85f, c.y + r * 0.1f)
                    close()
                }
                drawPath(p, col.copy(alpha = 0.15f))
                drawPath(p, col, style = thick)
                single(c, r * 0.8f, col)
            }
            League.GOLD -> {
                val p = starPath(c, r * 1.05f, r * 0.45f, 5)
                drawPath(p, col.copy(alpha = 0.2f))
                drawPath(p, col, style = thick)
                drawCircle(col, r * 0.2f, c)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(col, c + d * (r * 0.3f), c + d * (r * 0.42f), strokeWidth = r * 0.06f)
                }
            }
            League.PLATINUM -> {
                drawPath(polyPath(c, r, 6), col, style = thick)
                drawPath(polyPath(c, r * 0.82f, 6), col, style = thin)
                for (k in 0..2) {
                    val top = c.y - r * 0.5f + k * r * 0.25f
                    val w = r * (0.22f + 0.13f * k)
                    drawPath(
                        Path().apply {
                            moveTo(c.x, top)
                            lineTo(c.x - w, top + r * 0.38f)
                            lineTo(c.x + w, top + r * 0.38f)
                            close()
                        },
                        col,
                    )
                }
                drawRect(col, Offset(c.x - r * 0.05f, c.y + r * 0.35f), Size(r * 0.1f, r * 0.2f))
            }
            League.DIAMOND -> {
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    lineTo(c.x + r * 0.8f, c.y)
                    lineTo(c.x, c.y + r)
                    lineTo(c.x - r * 0.8f, c.y)
                    close()
                }
                drawPath(p, col.copy(alpha = 0.18f))
                drawPath(p, col, style = thick)
                val drop = Path().apply {
                    moveTo(c.x, c.y - r * 0.45f)
                    cubicTo(c.x + r * 0.4f, c.y, c.x + r * 0.3f, c.y + r * 0.35f, c.x, c.y + r * 0.35f)
                    cubicTo(c.x - r * 0.3f, c.y + r * 0.35f, c.x - r * 0.4f, c.y, c.x, c.y - r * 0.45f)
                    close()
                }
                drawPath(drop, col)
                drawPath(leafPath(Offset(c.x, c.y + r * 0.3f), Offset(c.x + r * 0.2f, c.y - r * 0.05f), r * 0.1f), Dark)
            }
            League.MASTER -> {
                for (sx in listOf(-1f, 1f)) {
                    val wing = Path().apply {
                        moveTo(c.x + sx * r * 0.4f, c.y)
                        cubicTo(c.x + sx * r * 1.0f, c.y - r * 0.9f, c.x + sx * r * 1.15f, c.y - r * 0.1f, c.x + sx * r * 1.0f, c.y + r * 0.3f)
                        cubicTo(c.x + sx * r * 0.85f, c.y + r * 0.1f, c.x + sx * r * 0.7f, c.y + r * 0.3f, c.x + sx * r * 0.4f, c.y + r * 0.4f)
                        close()
                    }
                    drawPath(wing, col.copy(alpha = 0.25f))
                    drawPath(wing, col, style = thin)
                }
                globe(c, r * 0.5f, col)
            }
            League.GRANDMASTER -> {
                val crown = Path().apply {
                    moveTo(c.x - r * 0.9f, c.y + r * 0.6f)
                    lineTo(c.x - r * 0.9f, c.y - r * 0.55f)
                    lineTo(c.x - r * 0.45f, c.y - r * 0.05f)
                    lineTo(c.x, c.y - r * 0.8f)
                    lineTo(c.x + r * 0.45f, c.y - r * 0.05f)
                    lineTo(c.x + r * 0.9f, c.y - r * 0.55f)
                    lineTo(c.x + r * 0.9f, c.y + r * 0.6f)
                    close()
                }
                drawPath(crown, col.copy(alpha = 0.2f))
                drawPath(crown, col, style = thick)
                sprout(Offset(c.x, c.y + r * 0.1f), r * 0.55f, col)
            }
            League.ECO_LEGEND -> {
                val p = starPath(c, r * 1.08f, r * 0.82f, 12)
                drawPath(p, col.copy(alpha = 0.25f))
                drawPath(p, col, style = thin)
                drawCircle(Color(0xFF2563EB), r * 0.5f, c)
                drawOval(Color(0xFF22C55E), Offset(c.x - r * 0.35f, c.y - r * 0.3f), Size(r * 0.35f, r * 0.3f))
                drawOval(Color(0xFF22C55E), Offset(c.x + r * 0.05f, c.y + r * 0.0f), Size(r * 0.3f, r * 0.35f))
                drawCircle(col, r * 0.5f, c, style = thin)
            }
        }
    }
}
