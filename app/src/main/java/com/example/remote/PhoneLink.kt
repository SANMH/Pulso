package com.example.remote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Mensajes entre el reloj y el teléfono. Debe coincidir con el módulo :mobile. */
object PulsoProtocol {
    const val CMD_PATH = "/pulso/cmd"
    const val ACK_PATH = "/pulso/ack"

    const val CMD_NEXT = "next"
    const val CMD_PREV = "prev"
    const val CMD_BACK = "back"
    const val CMD_OPEN_FACEBOOK = "open_fb"
    const val CMD_OPEN_FACEBOOK_VIDEO = "open_fb_video"
    const val CMD_OPEN_INSTAGRAM = "open_ig"
    const val CMD_OPEN_INSTAGRAM_VIDEO = "open_ig_video"
    const val CMD_VOLUME_UP = "vol_up"
    const val CMD_VOLUME_DOWN = "vol_down"
    const val CMD_SKIP_AD = "skip_ad"
    /** Activa o desactiva la pantalla negra del teléfono. */
    const val CMD_BLACK_SCREEN = "black"
    const val CMD_PING = "ping"

    const val ACK_OK = "ok"
    /** No había botón de omitir a la vista: se pasó a la siguiente publicación. */
    const val ACK_AD_NEXT = "ad_next"
    const val ACK_NO_SERVICE = "no_service"
    const val ACK_NO_APP = "no_app"

    const val PKG_FACEBOOK = "com.facebook.lite"
    const val PKG_INSTAGRAM = "com.instagram.android"
}

enum class LinkHealth { UNKNOWN, OK, WARNING, ERROR }

data class LinkState(
    val health: LinkHealth = LinkHealth.UNKNOWN,
    val message: String = "Conectando…",
    val foregroundApp: String? = null,
    /** Volumen multimedia del teléfono (0-100) tras la última orden de volumen. */
    val volumePercent: Int? = null,
    /** Cambia en cada ajuste de volumen para que la UI muestre el indicador. */
    val volumeEventId: Long = 0L,
    /** El teléfono tiene activa la pantalla negra de Pulso. */
    val blackScreen: Boolean = false
)

/** Envía órdenes al teléfono por la Wearable Data Layer y escucha sus respuestas. */
class PhoneLink(context: Context, private val scope: CoroutineScope) : MessageClient.OnMessageReceivedListener {

    private val appContext = context.applicationContext
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    private val _state = MutableStateFlow(LinkState())
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private var timeoutJob: Job? = null

    fun start() {
        messageClient.addListener(this)
        send(PulsoProtocol.CMD_PING, haptic = false)
    }

    fun stop() {
        messageClient.removeListener(this)
        timeoutJob?.cancel()
    }

    fun send(command: String, haptic: Boolean = true) {
        if (haptic) vibrate()
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    _state.value = _state.value.copy(health = LinkHealth.ERROR, message = "Teléfono no conectado")
                    return@launch
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, PulsoProtocol.CMD_PATH, command.toByteArray(Charsets.UTF_8)).await()
                }
                timeoutJob?.cancel()
                timeoutJob = scope.launch {
                    delay(3_000)
                    _state.value = _state.value.copy(
                        health = LinkHealth.ERROR,
                        message = "El teléfono no responde. ¿Instalaste Pulso en él?"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(health = LinkHealth.ERROR, message = "Error: ${e.message ?: "envío fallido"}")
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PulsoProtocol.ACK_PATH) return
        timeoutJob?.cancel()
        val parts = String(event.data, Charsets.UTF_8).split('|')
        val result = parts.getOrNull(0).orEmpty()
        val command = parts.getOrNull(1).orEmpty()
        val foreground = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        val volume = parts.getOrNull(3)?.toIntOrNull()
        val isVolume = command == PulsoProtocol.CMD_VOLUME_UP || command == PulsoProtocol.CMD_VOLUME_DOWN
        val appName = when (foreground) {
            PulsoProtocol.PKG_FACEBOOK -> "Facebook"
            PulsoProtocol.PKG_INSTAGRAM -> "Instagram"
            null -> null
            else -> "otra app"
        }
        // Teléfonos con una versión anterior de Pulso no envían este campo: se conserva el último valor.
        val black = parts.getOrNull(4)?.let { it == "1" } ?: _state.value.blackScreen
        val skippedAd = result == PulsoProtocol.ACK_OK && command == PulsoProtocol.CMD_SKIP_AD
        val toggledBlack = result == PulsoProtocol.ACK_OK && command == PulsoProtocol.CMD_BLACK_SCREEN
        _state.value = when {
            toggledBlack -> LinkState(
                LinkHealth.OK,
                if (black) "Pantalla negra" else "Pantalla normal",
                foregroundApp = appName
            )
            skippedAd -> LinkState(LinkHealth.OK, "Anuncio omitido", foregroundApp = appName)
            result == PulsoProtocol.ACK_AD_NEXT ->
                LinkState(LinkHealth.OK, "Sin botón: siguiente", foregroundApp = appName)
            result == PulsoProtocol.ACK_OK -> LinkState(
                health = if (appName == "Facebook" || appName == "Instagram") LinkHealth.OK else LinkHealth.WARNING,
                message = when {
                    isVolume && volume != null -> "Volumen $volume%"
                    appName == "Facebook" || appName == "Instagram" -> "En $appName"
                    appName == null -> "Teléfono listo"
                    else -> "Abre Facebook o Instagram"
                },
                foregroundApp = appName,
                volumePercent = volume,
                volumeEventId = if (isVolume) System.nanoTime() else _state.value.volumeEventId
            )
            result == PulsoProtocol.ACK_NO_SERVICE -> LinkState(LinkHealth.ERROR, "Activa Pulso en Accesibilidad del teléfono")
            result == PulsoProtocol.ACK_NO_APP -> LinkState(LinkHealth.ERROR, "Esa app no está instalada en el teléfono")
            else -> LinkState(LinkHealth.ERROR, "El teléfono no pudo hacerlo")
        }.copy(blackScreen = black)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }
}
