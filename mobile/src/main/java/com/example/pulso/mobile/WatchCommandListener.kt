package com.example.pulso.mobile

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Recibe las órdenes del reloj y le responde con:
 * `resultado|orden|app en primer plano|volumen multimedia en %`.
 */
class WatchCommandListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PulsoProtocol.CMD_PATH) return
        val command = String(event.data, Charsets.UTF_8)
        val service = PulsoControlService.instance

        val result = when (command) {
            // El volumen no necesita el servicio de accesibilidad.
            PulsoProtocol.CMD_VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            PulsoProtocol.CMD_VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            else -> service?.execute(command) ?: PulsoProtocol.ACK_NO_SERVICE
        }
        val foreground = service?.foregroundPackage.orEmpty()
        val volume = mediaVolumePercent()
        Log.i(TAG, "Orden '$command' -> $result ($foreground, vol $volume%)")

        val reply = "$result|$command|$foreground|$volume".toByteArray(Charsets.UTF_8)
        Wearable.getMessageClient(this)
            .sendMessage(event.sourceNodeId, PulsoProtocol.ACK_PATH, reply)
            .addOnFailureListener { Log.w(TAG, "No se pudo responder al reloj", it) }
    }

    private fun adjustVolume(direction: Int): String = try {
        audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        PulsoProtocol.ACK_OK
    } catch (e: SecurityException) {
        Log.w(TAG, "El sistema no dejó cambiar el volumen", e)
        PulsoProtocol.ACK_ERROR
    }

    private fun mediaVolumePercent(): Int {
        val audio = audio()
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val TAG = "PulsoListener"
    }
}
