package com.example.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CoralHeart
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WatchBlack
import com.example.ui.theme.WatchBorder
import com.example.ui.theme.WatchSurface
import com.example.ui.theme.WatchSurfaceElevated
import kotlin.math.abs

private val FacebookBlue = Color(0xFF1877F2)
private val InstagramPink = Color(0xFFE1306C)
private val InstagramGradient = Brush.linearGradient(
    listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)),
    start = Offset(0f, Float.POSITIVE_INFINITY),
    end = Offset(Float.POSITIVE_INFINITY, 0f)
)
private val SkipAdAmber = Color(0xFFFBBF24)
private val ButtonSize = 42.dp

/**
 * Mando a distancia. Izquierda: Facebook Lite y su sección de video. Derecha: Instagram y Reels.
 * Centro: anterior / siguiente (también con el bisel o deslizando).
 * Abajo: volumen, atrás y omitir anuncio.
 */
@Composable
fun RemoteControlScreen(link: PhoneLink) {
    val state by link.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val swipeThresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
    val rotary = remember { RotaryAccumulator() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBlack)
            .onRotaryScrollEvent { event ->
                rotary.consume(event.verticalScrollPixels, event.uptimeMillis)?.let { forward ->
                    link.send(if (forward) PulsoProtocol.CMD_NEXT else PulsoProtocol.CMD_PREV)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (abs(total) > swipeThresholdPx) {
                            // Deslizar hacia arriba en el reloj = siguiente (igual que en el teléfono).
                            link.send(if (total < 0) PulsoProtocol.CMD_NEXT else PulsoProtocol.CMD_PREV)
                        }
                    },
                    onVerticalDrag = { _, dy -> total += dy }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StatusPill(state)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FacebookButton(highlighted = state.foregroundApp == "Facebook") {
                        link.send(PulsoProtocol.CMD_OPEN_FACEBOOK)
                    }
                    VideoShortcut(Icons.Rounded.OndemandVideo, FacebookBlue, "Videos de Facebook") {
                        link.send(PulsoProtocol.CMD_OPEN_FACEBOOK_VIDEO)
                    }
                }
                Spacer(Modifier.width(12.dp))
                NavCapsule(
                    onPrev = { link.send(PulsoProtocol.CMD_PREV) },
                    onNext = { link.send(PulsoProtocol.CMD_NEXT) }
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InstagramButton(highlighted = state.foregroundApp == "Instagram") {
                        link.send(PulsoProtocol.CMD_OPEN_INSTAGRAM)
                    }
                    VideoShortcut(Icons.Rounded.SlowMotionVideo, InstagramPink, "Reels de Instagram") {
                        link.send(PulsoProtocol.CMD_OPEN_INSTAGRAM_VIDEO)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Cuatro botones de 36 dp: el máximo que cabe en esta fila sin que el borde redondo los recorte.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(Icons.AutoMirrored.Rounded.VolumeDown, "Bajar volumen") { link.send(PulsoProtocol.CMD_VOLUME_DOWN) }
                SmallAction(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") { link.send(PulsoProtocol.CMD_BACK) }
                SmallAction(Icons.Rounded.SkipNext, "Omitir anuncio", tint = SkipAdAmber) { link.send(PulsoProtocol.CMD_SKIP_AD) }
                SmallAction(Icons.AutoMirrored.Rounded.VolumeUp, "Subir volumen") { link.send(PulsoProtocol.CMD_VOLUME_UP) }
            }
        }
        VolumeArc(state.volumePercent, state.volumeEventId)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, description: String, tint: Color = TextPrimary, onClick: () -> Unit) {
    PressableCircle(
        description = description,
        fill = Modifier.background(WatchSurfaceElevated).border(1.dp, WatchBorder, CircleShape),
        shape = CircleShape,
        size = 36.dp,
        onClick = onClick
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Arco en el borde derecho de la pantalla redonda que muestra el volumen unos segundos. */
@Composable
private fun VolumeArc(percent: Int?, eventId: Long) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(eventId) {
        if (eventId == 0L || percent == null) return@LaunchedEffect
        visible = true
        delay(1_800)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, animationSpec = tween(250), label = "volArc")
    val level by animateFloatAsState((percent ?: 0) / 100f, animationSpec = tween(200), label = "volLevel")
    if (alpha == 0f) return
    Canvas(Modifier.fillMaxSize().padding(5.dp).graphicsLayer { this.alpha = alpha }) {
        val stroke = 5.dp.toPx()
        val sweep = 80f
        val start = 40f // de abajo (40°) hacia arriba (-40°), lado derecho
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        drawArc(WatchBorder, start, -sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (level > 0f) {
            drawArc(CyanPulse, start, -sweep * level, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun StatusPill(state: LinkState) {
    val dot = when (state.health) {
        LinkHealth.OK -> Color(0xFF34D399)
        LinkHealth.WARNING -> Color(0xFFFBBF24)
        LinkHealth.ERROR -> CoralHeart
        LinkHealth.UNKNOWN -> TextSecondary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(WatchSurface)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = state.message,
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 120.dp)
        )
    }
}

/** Botón circular con leve encogimiento al pulsar. */
@Composable
private fun PressableCircle(
    description: String,
    fill: Modifier,
    highlighted: Boolean = false,
    shape: Shape = CircleShape,
    size: Dp = ButtonSize,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(scale)
            .then(if (highlighted) Modifier.border(2.dp, TextPrimary, shape) else Modifier)
            .padding(if (highlighted) 3.dp else 0.dp)
            .clip(shape)
            .then(fill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description }
    ) { content() }
}

@Composable
private fun FacebookButton(highlighted: Boolean, onClick: () -> Unit) {
    PressableCircle(
        description = "Abrir Facebook Lite",
        fill = Modifier.background(FacebookBlue, CircleShape),
        highlighted = highlighted,
        onClick = onClick
    ) {
        Text("f", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
    }
}

@Composable
private fun InstagramButton(highlighted: Boolean, onClick: () -> Unit) {
    PressableCircle(
        description = "Abrir Instagram",
        fill = Modifier.background(InstagramGradient, CircleShape),
        highlighted = highlighted,
        shape = CircleShape,
        size = ButtonSize,
        onClick = onClick
    ) {
        // Glifo de cámara: cuadrado redondeado, lente y flash.
        Canvas(Modifier.size(18.dp)) {
            val stroke = Stroke(width = size.minDimension * 0.11f)
            val inset = stroke.width / 2
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke.width, size.height - stroke.width),
                cornerRadius = CornerRadius(size.minDimension * 0.3f),
                style = stroke
            )
            drawCircle(Color.White, radius = size.minDimension * 0.22f, style = stroke)
            drawCircle(Color.White, radius = size.minDimension * 0.06f, center = Offset(size.width * 0.76f, size.height * 0.24f))
        }
    }
}

@Composable
private fun VideoShortcut(icon: ImageVector, tint: Color, description: String, onClick: () -> Unit) {
    PressableCircle(
        description = description,
        fill = Modifier.background(WatchSurfaceElevated).border(1.dp, WatchBorder, CircleShape),
        shape = CircleShape,
        size = ButtonSize,
        onClick = onClick
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Cápsula vertical: mitad superior = anterior, mitad inferior = siguiente. */
@Composable
private fun NavCapsule(onPrev: () -> Unit, onNext: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier = Modifier
            .width(60.dp)
            .height(ButtonSize * 2 + 10.dp)
            .clip(shape)
            .background(WatchSurfaceElevated)
            .border(1.dp, WatchBorder, shape)
    ) {
        NavHalf(Icons.Rounded.KeyboardArrowUp, "Anterior", TextPrimary, onPrev)
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(WatchBorder))
        NavHalf(Icons.Rounded.KeyboardArrowDown, "Siguiente", CyanPulse, onNext)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NavHalf(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(if (pressed) WatchBorder else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/** Convierte el giro del bisel en pasos discretos, sin disparar varios por un solo clic. */
private class RotaryAccumulator {
    private var accumulated = 0f
    private var lastFiredAt = 0L

    fun consume(delta: Float, now: Long): Boolean? {
        accumulated += delta
        if (abs(accumulated) < THRESHOLD || now - lastFiredAt < COOLDOWN_MS) return null
        val forward = accumulated > 0
        accumulated = 0f
        lastFiredAt = now
        return forward
    }

    companion object {
        private const val THRESHOLD = 40f
        private const val COOLDOWN_MS = 450L
    }
}
