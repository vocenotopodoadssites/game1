package com.example.alienfall.ui.components

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.alienfall.engine.GameEngine
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.HudSnapshot
import com.example.alienfall.model.SkinVariant
import com.example.alienfall.renderer.AlienfallRenderer
import com.example.ui.theme.ArmorSlate
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PlasmaOrange
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SteelBorder
import kotlin.math.hypot

@Composable
fun GameGLSurfaceView(
    engine: GameEngine,
    onFrameHudUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val renderer = remember(engine) {
        AlienfallRenderer(context, engine, showcaseMode = false).apply {
            this.onFrameHudUpdate = onFrameHudUpdate
        }
    }
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, glView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> glView?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> glView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView?.onPause()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glView = this
            }
        },
        update = {
            renderer.onFrameHudUpdate = onFrameHudUpdate
        }
    )
}

@Composable
fun HeroShowcase3DView(
    hero: HeroClass,
    skin: SkinVariant,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val renderer = remember {
        AlienfallRenderer(context, engine = null, showcaseMode = true)
    }
    renderer.showcaseHero = hero
    renderer.showcaseSkin = skin

    var dragYaw by remember { mutableFloatStateOf(0.45f) }
    renderer.showcaseDragYaw = dragYaw
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, glView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> glView?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> glView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView?.onPause()
        }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                dragYaw += dragAmount.x * 0.012f
                renderer.showcaseDragYaw = dragYaw
            }
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glView = this
                }
            }
        )
    }
}

@Composable
fun VirtualJoystick(
    accentColor: Color,
    tag: String,
    size: Dp = 124.dp,
    onMove: (dx: Float, dy: Float, active: Boolean) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .testTag(tag)
            .clip(CircleShape)
            .background(ObsidianVoid.copy(alpha = 0.45f))
            .border(1.5.dp, accentColor.copy(alpha = if (active) 0.85f else 0.38f), CircleShape)
            .pointerInput(Unit) {
                val maxR = this.size.width * 0.36f
                detectDragGestures(
                    onDragStart = { start ->
                        active = true
                        val center = Offset(this.size.width / 2f, this.size.height / 2f)
                        val delta = start - center
                        val dist = hypot(delta.x, delta.y).coerceAtLeast(1f)
                        val clamped = if (dist > maxR) delta * (maxR / dist) else delta
                        knobOffset = clamped
                        onMove((clamped.x / maxR).coerceIn(-1f, 1f), (clamped.y / maxR).coerceIn(-1f, 1f), true)
                    },
                    onDragEnd = {
                        active = false
                        knobOffset = Offset.Zero
                        onMove(0f, 0f, false)
                    },
                    onDragCancel = {
                        active = false
                        knobOffset = Offset.Zero
                        onMove(0f, 0f, false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = knobOffset + dragAmount
                        val dist = hypot(next.x, next.y).coerceAtLeast(1f)
                        val clamped = if (dist > maxR) next * (maxR / dist) else next
                        knobOffset = clamped
                        onMove((clamped.x / maxR).coerceIn(-1f, 1f), (clamped.y / maxR).coerceIn(-1f, 1f), true)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(
                color = accentColor.copy(alpha = 0.14f),
                radius = this.size.minDimension * 0.34f,
                center = center,
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = accentColor.copy(alpha = if (active) 0.9f else 0.55f),
                radius = this.size.minDimension * 0.19f,
                center = center + knobOffset
            )
        }
    }
}

@Composable
fun TacticalMinimap(
    hud: HudSnapshot,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ArmorSlate.copy(alpha = 0.78f))
            .border(1.2.dp, SteelBorder, RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val half = size.width / 2f
            val range = 34f
            val scale = half / range

            // Radar rings
            drawCircle(CyberCyan.copy(alpha = 0.16f), half * 0.45f, Offset(half, half), style = Stroke(1.2f))
            drawCircle(CyberCyan.copy(alpha = 0.10f), half * 0.85f, Offset(half, half), style = Stroke(1.2f))

            fun toMini(dx: Float, dz: Float): Offset {
                // Align to isometric view
                val sx = (dx - dz) * 0.707f
                val sy = (dx + dz) * 0.707f
                return Offset(
                    (half + sx * scale).coerceIn(4f, size.width - 4f),
                    (half + sy * scale).coerceIn(4f, size.height - 4f)
                )
            }

            for ((ox, oz) in hud.minimapObstacles) {
                drawCircle(Color(0xFF475569), 2.5f, toMini(ox, oz))
            }
            for ((px, pz, discovered) in hud.minimapPois) {
                drawCircle(
                    color = if (discovered) Color.Gray else SolarGold,
                    radius = 4.2f,
                    center = toMini(px, pz)
                )
            }
            for ((ex, ez) in hud.minimapEnemies) {
                drawCircle(CrimsonCore.copy(alpha = 0.88f), 2.6f, toMini(ex, ez))
            }
            for ((bx, bz) in hud.minimapBosses) {
                val p = toMini(bx, bz)
                drawCircle(PlasmaOrange, 5.5f, p)
                drawCircle(Color.White, 2.2f, p)
            }
            // Player at center
            drawCircle(CyberCyan, 4.4f, Offset(half, half))
        }
    }
}
