package com.example.pulso.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Ejecuta en el teléfono lo que pide el reloj: deslizar para pasar publicaciones o videos,
 * atrás, abrir Facebook Lite / Instagram y saltar a su sección de video.
 * Solo inspecciona la pantalla para localizar la pestaña de video; no lee publicaciones.
 */
class PulsoControlService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var foregroundPackage: String? = null
        private set

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "Servicio de control conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // Ignora el teclado y la propia UI del sistema para no perder la app real.
            if (pkg != "com.android.systemui" && !pkg.contains("inputmethod") && !pkg.contains("honeyboard")) {
                foregroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Devuelve el código de respuesta para el reloj. */
    fun execute(command: String): String {
        val wokeUp = wakeScreenIfNeeded()
        // Si la pantalla estaba apagada, deja que se encienda antes de tocarla.
        val delay = if (wokeUp) 500L else 0L
        return when (command) {
            PulsoProtocol.CMD_OPEN_FACEBOOK -> openApp(PulsoProtocol.PKG_FACEBOOK)
            PulsoProtocol.CMD_OPEN_INSTAGRAM -> openApp(PulsoProtocol.PKG_INSTAGRAM)
            PulsoProtocol.CMD_OPEN_FACEBOOK_VIDEO -> openVideoSection(PulsoProtocol.PKG_FACEBOOK)
            PulsoProtocol.CMD_OPEN_INSTAGRAM_VIDEO -> openVideoSection(PulsoProtocol.PKG_INSTAGRAM)
            PulsoProtocol.CMD_BACK -> {
                main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, delay)
                PulsoProtocol.ACK_OK
            }
            PulsoProtocol.CMD_NEXT -> gesture(delay) { w, h -> swipe(w / 2f, h * 0.72f, w / 2f, h * 0.28f) }
            PulsoProtocol.CMD_PREV -> gesture(delay) { w, h -> swipe(w / 2f, h * 0.28f, w / 2f, h * 0.72f) }
            PulsoProtocol.CMD_PING -> PulsoProtocol.ACK_OK
            else -> PulsoProtocol.ACK_ERROR
        }
    }

    private fun openApp(pkg: String): String {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return PulsoProtocol.ACK_NO_APP
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            startActivity(intent)
            foregroundPackage = pkg
            PulsoProtocol.ACK_OK
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir $pkg", e)
            PulsoProtocol.ACK_ERROR
        }
    }

    /** Abre la app (si hace falta) y toca su pestaña de video cuando aparezca. */
    private fun openVideoSection(pkg: String): String {
        val alreadyOpen = rootInActiveWindow?.packageName == pkg
        if (!alreadyOpen) {
            val result = openApp(pkg)
            if (result != PulsoProtocol.ACK_OK) return result
        }
        tryTapVideoTab(pkg, attempt = 0, initialDelay = if (alreadyOpen) 0L else 1_200L)
        return PulsoProtocol.ACK_OK
    }

    private fun tryTapVideoTab(pkg: String, attempt: Int, initialDelay: Long) {
        main.postDelayed({
            val root = rootInActiveWindow
            val tapped = root != null && root.packageName == pkg && when (pkg) {
                PulsoProtocol.PKG_INSTAGRAM -> tapInstagramReels(root)
                PulsoProtocol.PKG_FACEBOOK -> tapFacebookLiteVideo(root)
                else -> false
            }
            when {
                tapped -> Log.i(TAG, "Pestaña de video abierta en $pkg")
                attempt < 4 -> tryTapVideoTab(pkg, attempt + 1, 700L)
                else -> {
                    Log.w(TAG, "No encontré la pestaña de video de $pkg; toco su posición habitual")
                    tapFallback(pkg)
                }
            }
        }, initialDelay)
    }

    private fun tapInstagramReels(root: AccessibilityNodeInfo): Boolean {
        val node = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Reels").firstOrNull { it.isClickable }
            ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Facebook Lite dibuja su propia interfaz sin nombres accesibles. Su barra superior tiene
     * 5-6 pestañas del mismo ancho (Inicio, Amigos, Messenger, Video, Notificaciones, Marketplace):
     * la de video es la cuarta.
     */
    private fun tapFacebookLiteVideo(root: AccessibilityNodeInfo): Boolean {
        val (w, h) = screenSize()
        val tabs = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        collect(root) { node ->
            if (!node.isClickable) return@collect
            val r = Rect().also { node.getBoundsInScreen(it) }
            val looksLikeTab = r.top < h * 0.25f && r.width() in (w / 8)..(w / 4) && r.height() in 60..300
            if (looksLikeTab) tabs += node to r
        }
        // Las pestañas comparten fila: quédate con la fila más poblada.
        val row = tabs.groupBy { it.second.top / 20 }.values.maxByOrNull { it.size } ?: return false
        if (row.size < 5) return false
        val video = row.sortedBy { it.second.left }[3]
        return video.first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun tapFallback(pkg: String) {
        gesture(0) { w, h ->
            when (pkg) {
                PulsoProtocol.PKG_FACEBOOK -> tap(w * 0.583f, h * 0.126f)
                else -> tap(w * 0.3f, h * 0.953f)
            }
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, visit: (AccessibilityNodeInfo) -> Unit) {
        if (node == null) return
        visit(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), visit)
    }

    private fun gesture(delay: Long, build: (Float, Float) -> GestureDescription): String {
        val (w, h) = screenSize()
        val description = build(w.toFloat(), h.toFloat())
        main.postDelayed({
            val accepted = dispatchGesture(description, null, null)
            if (!accepted) Log.w(TAG, "El sistema rechazó el gesto")
        }, delay)
        return PulsoProtocol.ACK_OK
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): GestureDescription {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()
    }

    private fun tap(x: Float, y: Float): GestureDescription {
        val path = Path().apply { moveTo(x, y) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenIfNeeded(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) return false
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "pulso:wake"
        ).acquire(3_000)
        return true
    }

    companion object {
        private const val TAG = "PulsoControl"

        @Volatile
        var instance: PulsoControlService? = null
            private set
    }
}
