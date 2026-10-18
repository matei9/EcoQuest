package com.greenguardians.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.greenguardians.app.model.Category

/** A real button with keyboard/TalkBack semantics and a short physical press travel. */
@Composable
fun AdventureButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateDpAsState(if (pressed && enabled) 3.dp else 0.dp, label = "button press")
    val colors = MaterialTheme.colorScheme
    Box(modifier.padding(bottom = 4.dp)) {
        Box(Modifier.matchParentSize().offset(y = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) colors.primary.copy(alpha = .42f) else colors.onSurface.copy(alpha = .08f)))
        Button(onClick, Modifier.fillMaxWidth().offset(y = travel).heightIn(min = 52.dp), enabled = enabled,
            shape = RoundedCornerShape(16.dp), interactionSource = interaction,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), content = content)
    }
}

/** Small original vector illustrations, consistent across quest cards and navigation. */
@Composable
fun EcoGlyph(kind: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(28.dp)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(w * .075f, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(w*x,h*y), Offset(w*x2,h*y2), w*.075f, StrokeCap.Round)
        fun path(vararg points: Pair<Float,Float>, close: Boolean = false): Path = Path().apply {
            points.forEachIndexed { i,p -> if(i==0) moveTo(w*p.first,h*p.second) else lineTo(w*p.first,h*p.second) }; if(close) close()
        }
        when (kind) {
            "home" -> { drawPath(path(.14f to .46f,.5f to .16f,.86f to .46f),tint,style=stroke); drawPath(path(.24f to .42f,.24f to .84f,.76f to .84f,.76f to .42f),tint,style=stroke); line(.45f,.84f,.45f,.6f) }
            "tree" -> { line(.5f,.9f,.5f,.46f); drawCircle(tint,w*.27f,Offset(w*.5f,h*.36f)); drawCircle(tint,w*.21f,Offset(w*.3f,h*.5f)); drawCircle(tint,w*.21f,Offset(w*.7f,h*.5f)) }
            "trophy" -> { drawPath(path(.28f to .2f,.72f to .2f,.66f to .57f,.5f to .68f,.34f to .57f,close=true),tint,style=stroke);line(.5f,.68f,.5f,.86f);line(.33f,.86f,.67f,.86f);drawArc(tint,0f,180f,false,Offset(w*.08f,h*.18f),Size(w*.28f,h*.36f),style=stroke);drawArc(tint,0f,180f,false,Offset(w*.64f,h*.18f),Size(w*.28f,h*.36f),style=stroke) }
            "profile" -> { drawCircle(tint,w*.16f,Offset(w*.5f,h*.29f),style=stroke);drawArc(tint,180f,180f,false,Offset(w*.19f,h*.56f),Size(w*.62f,h*.56f),style=stroke) }
            "ai" -> { drawRoundRect(tint,Offset(w*.17f,h*.26f),Size(w*.66f,h*.52f),androidx.compose.ui.geometry.CornerRadius(w*.12f),style=stroke);line(.5f,.1f,.5f,.26f);drawCircle(tint,w*.05f,Offset(w*.37f,h*.48f));drawCircle(tint,w*.05f,Offset(w*.63f,h*.48f));line(.39f,.65f,.61f,.65f) }
            "transport" -> { drawCircle(tint,w*.075f,Offset(w*.55f,h*.16f));line(.48f,.34f,.38f,.58f);line(.38f,.58f,.18f,.85f);line(.38f,.58f,.62f,.7f);line(.62f,.7f,.72f,.91f);line(.48f,.34f,.69f,.49f);line(.48f,.34f,.25f,.44f) }
            "sgr" -> { drawRoundRect(tint,Offset(w*.22f,h*.14f),Size(w*.56f,h*.72f),androidx.compose.ui.geometry.CornerRadius(w*.04f),style=stroke);line(.35f,.36f,.65f,.36f);line(.35f,.51f,.65f,.51f);line(.35f,.66f,.53f,.66f) }
            "energy" -> drawPath(path(.56f to .08f,.23f to .55f,.46f to .55f,.38f to .92f,.79f to .39f,.55f to .39f,close=true),tint)
            "community" -> { drawPath(path(.15f to .39f,.25f to .15f,.75f to .15f,.85f to .39f,close=true),tint,style=stroke);line(.24f,.42f,.24f,.86f);line(.76f,.42f,.76f,.86f);line(.24f,.86f,.76f,.86f);line(.48f,.59f,.48f,.86f) }
            "progress" -> {drawCircle(tint,w*.36f,Offset(w*.5f,h*.5f),style=stroke);drawPath(path(.3f to .5f,.45f to .65f,.71f to .34f),tint,style=stroke)}
            else -> { val leaf=Path().apply {moveTo(w*.2f,h*.8f);cubicTo(w*.05f,h*.3f,w*.5f,h*.1f,w*.84f,h*.15f);cubicTo(w*.9f,h*.6f,w*.6f,h*.9f,w*.2f,h*.8f)};drawPath(leaf,tint);drawLine(Color.White.copy(alpha=.65f),Offset(w*.25f,h*.74f),Offset(w*.68f,h*.32f),w*.04f,StrokeCap.Round) }
        }
    }
}

fun Category.glyph() = when(this) {
    Category.TRANSPORT -> "transport"; Category.SGR -> "sgr"; Category.ENERGY -> "energy"
    Category.COMMUNITY -> "community"; Category.PROGRESS -> "progress"; else -> "leaf"
}
