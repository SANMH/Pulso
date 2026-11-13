package com.example.pulso.mobile

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** Recibe las órdenes del reloj y le responde con el resultado y la app en primer plano. */
class WatchCommandListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PulsoProtocol.CMD_PATH) return
        val command = String(event.data, Charsets.UTF_8)
        val service = PulsoControlService.instance

        val result = service?.execute(command) ?: PulsoProtocol.ACK_NO_SERVICE
        val foreground = service?.foregroundPackage.orEmpty()
        Log.i(TAG, "Orden '$command' -> $result ($foreground)")

        val reply = "$result|$command|$foreground".toByteArray(Charsets.UTF_8)
        Wearable.getMessageClient(this)
            .sendMessage(event.sourceNodeId, PulsoProtocol.ACK_PATH, reply)
            .addOnFailureListener { Log.w(TAG, "No se pudo responder al reloj", it) }
    }

    companion object {
        private const val TAG = "PulsoListener"
    }
}
