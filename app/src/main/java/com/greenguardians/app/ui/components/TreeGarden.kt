package com.greenguardians.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenguardians.app.model.GuardianTree
import kotlin.math.sin

/** Original vector garden. The viewport stays bounded even for a kilometre-high tree. */
@Composable
fun TreeGarden(tree: GuardianTree, description: String) {
    val transition = rememberInfiniteTransition(label = "garden breeze")
    val breeze by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "leaf sway")
    Canvas(Modifier.fillMaxWidth().height(320.dp).semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val cx = w * .48f
        val ground = h * .87f
        val wilt = tree.health != "healthy"
        val dry = tree.health == "withered"
        val leafDark = if (dry) Color(0xFF826C39) else if (wilt) Color(0xFF6C8740) else Color(0xFF247C49)
        val leafMid = if (dry) Color(0xFFB69750) else if (wilt) Color(0xFF99AC58) else Color(0xFF48A65A)
        val leafLight = if (dry) Color(0xFFD1B875) else if (wilt) Color(0xFFC0C66A) else Color(0xFF8CC95E)
        fun oval(color: Color, x: Float, y: Float, rx: Float, ry: Float) = drawOval(color, Offset(x - rx, y - ry), Size(rx * 2, ry * 2))
        clipRect {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFBFE5DB), Color(0xFFF4EDCA))))
            drawCircle(Color(0xFFFFF8D7).copy(alpha = .45f), w * .13f, Offset(w * .82f, h * .16f))
            drawCircle(Color(0xFFFFDF8C), w * .08f, Offset(w * .82f, h * .16f))
            for (i in 0..2) {
                val x = w * (.12f + i * .34f)
                oval(Color.White.copy(alpha = .5f), x, h * (.16f + i % 2 * .09f), w * .10f, h * .025f)
            }
            if (!tree.topOnly) {
                oval(Color(0xFF9BBF79), w * .2f, h * .74f, w * .6f, h * .19f)
                oval(Color(0xFF78A566), w * .85f, h * .78f, w * .6f, h * .19f)
                // A low garden fence and soft lawn frame the planting bed.
                for (i in 0..11) {
                    val x = i * w / 10
                    drawLine(Color(0xFFDFCE9D), Offset(x, h * .65f), Offset(x, h * .80f), w * .014f, StrokeCap.Round)
                }
                drawLine(Color(0xFFC0B07F), Offset(0f, h * .72f), Offset(w, h * .72f), h * .012f)
                drawRect(Brush.verticalGradient(listOf(Color(0xFF9ABD70), Color(0xFF567F48)), startY = h * .77f), Offset(0f, h * .77f))
                oval(Color(0xFF4B693C).copy(alpha = .5f), cx, ground + h * .035f, w * .29f, h * .055f)
                oval(Color(0xFFB89160), cx, ground + h * .012f, w * .26f, h * .06f)
                oval(Color(0xFF79583D), cx, ground, w * .22f, h * .038f)
                for (i in 0..7) {
                    val x = w * (.08f + i * .125f)
                    val y = h * (.93f + (i % 2) * .03f)
                    oval(Color(0xFFC8CC9D), x, y, w * .015f, h * .008f)
                    if (i < 2 || i > 5) {
                        drawLine(Color(0xFF3D773F), Offset(x, y), Offset(x, y - h * .04f), w * .006f)
                        drawCircle(if (i % 2 == 0) Color(0xFFF7D77B) else Color(0xFFE6A489), w * .012f, Offset(x, y - h * .045f))
                    }
                }
            }
            rotate(breeze * if (wilt) .4f else .8f, Offset(cx, ground)) {
                when (tree.stage) {
                    1 -> {
                        oval(Color(0xFF54392E), cx, ground - h * .018f, w * .034f, h * .045f)
                        oval(Color(0xFFAA7951), cx - w * .006f, ground - h * .026f, w * .023f, h * .032f)
                        drawLine(Color(0xFFE3C78A), Offset(cx - w * .012f, ground - h * .047f), Offset(cx + w * .005f, ground), w * .005f)
                    }
                    2, 3 -> {
                        val tall = if (tree.stage == 2) .14f else .27f
                        val top = ground - h * tall
                        drawLine(Color(0xFF53824A), Offset(cx, ground), Offset(cx, top), w * .012f, StrokeCap.Round)
                        val pairs = if (tree.stage == 2) 1 else 2
                        for (i in 0 until pairs) {
                            val y = top + i * h * .10f
                            for (side in listOf(-1f, 1f)) {
                                rotate(side * if (wilt) 35f else -22f, Offset(cx, y)) {
                                    oval(leafMid, cx + side * w * .055f, y, w * .065f, h * .023f)
                                    drawLine(leafLight, Offset(cx, y), Offset(cx + side * w * .095f, y), w * .003f)
                                }
                            }
                        }
                    }
                    else -> {
                        val stage = tree.stage
                        val top = h * when (stage) { 4 -> .56f; 5 -> .43f; 6 -> .32f; else -> .22f }
                        val crownY = if (tree.topOnly || stage >= 6) h * .36f else top + h * .06f
                        val crownW = w * when (stage) { 4 -> .16f; 5 -> .23f; 6 -> .24f; else -> .28f }
                        val trunkBase = if (tree.topOnly) h * 1.1f else ground
                        val thickness = w * when (stage) { 4 -> .018f; 5 -> .028f; 6 -> .038f; else -> .052f }
                        val trunk = Path().apply {
                            moveTo(cx - thickness, trunkBase)
                            cubicTo(cx - thickness * .7f, h * .68f, cx - thickness * .8f, crownY, cx, crownY - h * .08f)
                            cubicTo(cx + thickness * .7f, crownY, cx + thickness * .6f, h * .68f, cx + thickness, trunkBase)
                            close()
                        }
                        drawPath(trunk, Color(0xFF785239))
                        drawLine(Color(0xFFB1814A), Offset(cx - thickness * .35f, trunkBase - h * .04f), Offset(cx - thickness * .25f, crownY), thickness * .35f, StrokeCap.Round)
                        for (side in listOf(-1f, 1f)) {
                            drawLine(Color(0xFF785239), Offset(cx, crownY + h * .23f), Offset(cx + side * crownW * .7f, crownY + h * .02f), thickness * .9f, StrokeCap.Round)
                        }
                        // Equal pixel radii keep the late-stage crowns round on wide screens.
                        if (stage >= 6) {
                            val radius = minOf(crownW, h * .30f)
                            oval(leafDark, cx, crownY + radius * .10f, radius, radius)
                            for (i in 0..6) {
                                val angle = i * (2f * Math.PI.toFloat() / 7f)
                                val x = cx + kotlin.math.cos(angle) * radius * .52f
                                val y = crownY + sin(angle) * radius * .52f + if (wilt) radius * .10f else 0f
                                oval(if (i % 2 == 0) leafMid else leafLight, x, y, radius * .54f, radius * .54f)
                            }
                            oval(leafMid, cx, crownY, radius * .72f, radius * .72f)
                            oval(leafLight.copy(alpha = .65f), cx - radius * .30f, crownY - radius * .43f, radius * .27f, radius * .22f)
                        } else {
                            oval(leafDark, cx, crownY + h * .07f, crownW, h * .15f)
                            for (i in 0..6) {
                                val x = cx + (i - 3) * crownW / 4
                                val y = crownY + sin(i * 1.8f) * h * .045f + if (wilt) h * .035f else 0f
                                oval(if (i % 2 == 0) leafMid else leafLight, x, y, crownW * .44f, h * (.085f + stage * .004f))
                            }
                            oval(leafMid, cx, crownY - h * .06f, crownW * .59f, h * .11f)
                            oval(leafLight.copy(alpha = .65f), cx - crownW * .19f, crownY - h * .10f, crownW * .29f, h * .045f)
                        }

                    }
                }
            }
            if (wilt && tree.stage > 1) {
                for (i in 0..2) oval(leafLight, cx + w * (.12f + i * .04f), h * (.64f + i * .06f), w * .016f, h * .006f)
            }
            if (tree.topOnly) {
                // The trunk continues beneath the viewport; no giant off-screen image is allocated.
                drawLine(Color.White.copy(alpha = .65f), Offset(w * .90f, h * .18f), Offset(w * .90f, h * .86f), w * .004f)
                for (i in 0..4) drawLine(Color.White.copy(alpha = .8f), Offset(w * .88f, h * (.18f + i * .17f)), Offset(w * .92f, h * (.18f + i * .17f)), w * .004f)
                for (i in 0..1) {
                    val y = h * (.91f + i * .035f)
                    drawLine(Color(0xFFF4EDCA), Offset(cx - w * .07f, y), Offset(cx, y + h * .018f), w * .012f)
                    drawLine(Color(0xFFF4EDCA), Offset(cx, y + h * .018f), Offset(cx + w * .07f, y), w * .012f)
                }
            }
        }
    }
}
