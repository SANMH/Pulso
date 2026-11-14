package com.example.pulso.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Region
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.gms.wearable.Wearable

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

    /** Pantalla negra de Pulso activa (se responde al reloj en cada orden). */
    @Volatile
    var blackScreenOn = false
        private set
    private lateinit var overlay: BlackScreenOverlay

    /** Al apagar el teléfono con el botón de encendido se quita la pantalla negra: siempre hay salida. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (blackScreenOn) {
                Log.i(TAG, "Pantalla apagada: se quita la pantalla negra")
                setBlackScreen(false)
                notifyWatch()
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        overlay = BlackScreenOverlay(this) { on ->
            Log.i(TAG, "Pantalla negra pedida desde el teléfono: $on")
            setBlackScreen(on)
            notifyWatch()
        }
        overlay.start()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        Log.i(TAG, "Servicio de control conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // Ignora el teclado, la UI del sistema y la propia capa negra para no perder la app real.
            if (pkg != "com.android.systemui" && pkg != packageName &&
                !pkg.contains("inputmethod") && !pkg.contains("honeyboard")
            ) {
                foregroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { unregisterReceiver(screenOffReceiver) }
        if (::overlay.isInitialized) overlay.stop()
        super.onDestroy()
    }

    /** Pantalla negra de Pulso: ver [BlackScreenOverlay]. */
    private fun setBlackScreen(on: Boolean) {
        blackScreenOn = on
        main.post {
            if (::overlay.isInitialized) overlay.setBlack(on)
            interceptTouchscreen(on)
            Log.i(TAG, if (on) "Pantalla negra activada" else "Pantalla negra desactivada")
        }
    }

    /**
     * Bloquea los bordes con la pantalla negra. Los gestos del sistema (atrás, inicio, cortina de
     * notificaciones, panel Edge) nacen en los bordes y ninguna capa los detiene; la exploración
     * táctil (la de TalkBack) sí, porque actúa antes. Se activa solo mientras dura la pantalla negra
     * y el centro se deja "de paso", así los toques ahí siguen llegando a la capa (Desbloquear).
     */
    private fun interceptTouchscreen(on: Boolean) {
        runCatching {
            val info = serviceInfo ?: return
            val flag = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            info.flags = if (on) info.flags or flag else info.flags and flag.inv()
            serviceInfo = info
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val (w, h) = screenSize()
                val edge = (EDGE_BLOCK_DP * resources.displayMetrics.density).toInt()
                setTouchExplorationPassthroughRegion(
                    android.view.Display.DEFAULT_DISPLAY,
                    Region(edge, edge, w - edge, h - edge)
                )
            }
        }.onFailure { Log.w(TAG, "No se pudieron bloquear los bordes", it) }
    }

    /** Toques en los bordes bloqueados: no hacen nada (no se explora ni se enfoca nada). */
    override fun onGesture(gestureEvent: android.accessibilityservice.AccessibilityGestureEvent): Boolean = true

    /** Avisa al reloj de un cambio hecho desde el teléfono, para que su botón de luna no mienta. */
    private fun notifyWatch() {
        val reply = "${PulsoProtocol.ACK_OK}|${PulsoProtocol.CMD_BLACK_SCREEN}|${foregroundPackage.orEmpty()}||" +
            if (blackScreenOn) "1" else "0"
        val messages = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { messages.sendMessage(it.id, PulsoProtocol.ACK_PATH, reply.toByteArray(Charsets.UTF_8)) }
        }
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
            PulsoProtocol.CMD_NEXT -> scroll(forward = true, delay)
            PulsoProtocol.CMD_PREV -> scroll(forward = false, delay)
            PulsoProtocol.CMD_SKIP_AD -> skipAd(delay)
            PulsoProtocol.CMD_BLACK_SCREEN -> {
                setBlackScreen(!blackScreenOn)
                PulsoProtocol.ACK_OK
            }
            PulsoProtocol.CMD_PING -> PulsoProtocol.ACK_OK
            else -> PulsoProtocol.ACK_ERROR
        }
    }

    /**
     * Pasa a la publicación siguiente o anterior. Primero lo intenta con acciones de accesibilidad
     * sobre la ventana de Facebook / Instagram: así funciona aunque otra app (p. ej. Black Screen)
     * tape la pantalla con una capa que se traga los toques. Si la app no ofrece cómo, desliza.
     */
    private fun scroll(forward: Boolean, delay: Long): String {
        main.postDelayed({
            val root = socialRoot()
            val done = root != null && (tapOwnArrow(root, forward) || scrollList(root, forward))
            if (!done) {
                if (isDebuggable()) logTree(root)
                dispatchThroughBlack(
                    screenSize().let { (w, h) ->
                        if (forward) swipe(w / 2f, h * 0.72f, w / 2f, h * 0.28f)
                        else swipe(w / 2f, h * 0.28f, w / 2f, h * 0.72f)
                    }
                )
            }
        }, delay)
        return PulsoProtocol.ACK_OK
    }

    /** Ventana de Facebook Lite o Instagram, aunque haya otra capa encima. */
    private fun socialRoot(pkg: String? = null): AccessibilityNodeInfo? {
        val wanted = pkg?.let { setOf(it) } ?: SOCIAL_PACKAGES
        val roots = try { windows.mapNotNull { it.root } } catch (e: Exception) { emptyList() }
        return roots.firstOrNull { it.packageName == foregroundPackage && it.packageName in wanted }
            ?: roots.firstOrNull { it.packageName in wanted }
            ?: rootInActiveWindow?.takeIf { it.packageName in wanted }
    }

    /** Botones ⌃ / ⌄ propios de la app (el visor de video de Facebook Lite los tiene). */
    private fun tapOwnArrow(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val pattern = if (forward) NEXT_LABEL else PREV_LABEL
        var target: AccessibilityNodeInfo? = null
        collect(root) { node ->
            if (target != null || !node.isVisibleToUser) return@collect
            val label = node.contentDescription?.toString()?.trim()?.lowercase() ?: return@collect
            if (pattern.matches(label)) target = clickableAncestor(node)
        }
        val ok = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (ok) Log.i(TAG, "Botón ${if (forward) "siguiente" else "anterior"} de la app pulsado")
        return ok
    }

    /** Desplaza la lista vertical más grande de la pantalla, sin tocarla. */
    private fun scrollList(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
        val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
        val wanted = if (forward) down else up
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        collect(root) { node ->
            if (!node.isScrollable || !node.isVisibleToUser) return@collect
            val r = Rect().also { node.getBoundsInScreen(it) }
            val vertical = wanted in node.actionList || r.height() >= r.width()
            val area = r.width() * r.height()
            if (vertical && area > bestArea) { best = node; bestArea = area }
        }
        val node = best ?: return false
        val ok = if (wanted in node.actionList) node.performAction(wanted.id)
        else node.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
        if (ok) Log.i(TAG, "Lista desplazada (${node.className})")
        return ok
    }

    private fun isDebuggable() = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Solo en debug: estructura de la pantalla (sin textos de publicaciones) para ajustar la búsqueda. */
    private fun logTree(root: AccessibilityNodeInfo?) {
        if (root == null) { Log.d(TAG, "Sin ventana de Facebook/Instagram accesible"); return }
        var count = 0
        collect(root) { node ->
            if (count++ > 300 || !(node.isClickable || node.isScrollable)) return@collect
            val r = Rect().also { node.getBoundsInScreen(it) }
            val desc = node.contentDescription?.toString()?.takeIf { it.length <= 30 }.orEmpty()
            Log.d(TAG, "árbol: ${node.className} id=${node.viewIdResourceName} desc='$desc' " +
                "click=${node.isClickable} scroll=${node.isScrollable} $r")
        }
    }

    /**
     * Pulsa el botón "Omitir / Skip / Saltar" del anuncio en pantalla. Si no hay ninguno
     * (publicación patrocinada, o el botón aún muestra la cuenta atrás), pasa a la siguiente.
     */
    private fun skipAd(delay: Long): String {
        val button = findSkipButton(socialRoot() ?: rootInActiveWindow)
        if (button != null) {
            val bounds = Rect().also { button.getBoundsInScreen(it) }
            main.postDelayed({
                if (!button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    dispatchThroughBlack(tap(bounds.exactCenterX(), bounds.exactCenterY()))
                }
            }, delay)
            Log.i(TAG, "Botón de omitir anuncio pulsado")
            return PulsoProtocol.ACK_OK
        }
        scroll(forward = true, delay)
        return PulsoProtocol.ACK_AD_NEXT
    }

    private fun findSkipButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        collect(root) { node ->
            if (found != null || !node.isVisibleToUser) return@collect
            val label = (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase()
            // Solo IDs de anuncio: "skip_forward_icon" del reproductor de Facebook Lite salta 10 s, no el anuncio.
            val idLooksLikeSkip = node.viewIdResourceName?.lowercase()?.let { SKIP_AD_ID.containsMatchIn(it) } == true
            if ((label != null && SKIP_LABEL.matches(label)) || (idLooksLikeSkip && node.isClickable)) {
                found = clickableAncestor(node) ?: node
            }
        }
        return found
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
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
        val alreadyOpen = socialRoot(pkg) != null && foregroundPackage == pkg
        if (!alreadyOpen) {
            val result = openApp(pkg)
            if (result != PulsoProtocol.ACK_OK) return result
        }
        tryTapVideoTab(pkg, attempt = 0, initialDelay = if (alreadyOpen) 0L else 1_200L)
        return PulsoProtocol.ACK_OK
    }

    private fun tryTapVideoTab(pkg: String, attempt: Int, initialDelay: Long) {
        main.postDelayed({
            val root = socialRoot(pkg)
            val tapped = root != null && when (pkg) {
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
        main.postDelayed({ dispatchThroughBlack(description) }, delay)
        return PulsoProtocol.ACK_OK
    }

    /**
     * Toca o desliza de verdad. Con la pantalla negra (que bloquea los toques) la deja pasar solo
     * mientras dura el gesto. Llamar desde el hilo principal.
     */
    private fun dispatchThroughBlack(description: GestureDescription) {
        val shielded = ::overlay.isInitialized && overlay.isBlackShown
        val restore = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (shielded) main.postDelayed({
                    overlay.setPassThrough(false)
                    interceptTouchscreen(blackScreenOn)
                }, 50)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) = onCompleted(gestureDescription)
        }
        if (shielded) {
            overlay.setPassThrough(true)
            interceptTouchscreen(false)
        }
        // Deja que el sistema aplique el cambio de la capa antes de tocar.
        main.postDelayed({
            if (!dispatchGesture(description, restore, null)) {
                Log.w(TAG, "El sistema rechazó el gesto")
                if (shielded) {
                    overlay.setPassThrough(false)
                    interceptTouchscreen(blackScreenOn)
                }
            }
        }, if (shielded) 80L else 0L)
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

        /** Ancho de la franja de cada borde que se bloquea con la pantalla negra. */
        private const val EDGE_BLOCK_DP = 56

        /** "Omitir", "Skip ad", "Saltar anuncio ›", etc. No acepta la cuenta atrás ("Omitir en 5"). */
        private val SKIP_LABEL =
            Regex("""^(skip|omitir|saltar)(\s+(ads?|anuncios?|publicidad))?\s*[›>»▶]?$""")

        /** IDs como "skip_ad_button" o "ad_skip"; no "skip_forward_icon". */
        private val SKIP_AD_ID = Regex("""(^|[/:_])(skip_?ads?|ads?_?skip)(_|$)""")

        private val SOCIAL_PACKAGES = setOf(PulsoProtocol.PKG_FACEBOOK, PulsoProtocol.PKG_INSTAGRAM)

        /** Flechas de la propia app: "Siguiente video", "Next", "Video anterior"… */
        private val NEXT_LABEL = Regex("""^((ver\s+)?(el\s+)?(siguiente|próximo)(\s+(video|vídeo|reel))?|(video|vídeo)\s+siguiente|next(\s+(video|reel))?)$""")
        private val PREV_LABEL = Regex("""^((ver\s+)?(el\s+)?anterior(\s+(video|vídeo|reel))?|(video|vídeo)\s+anterior|previous(\s+(video|reel))?)$""")

        @Volatile
        var instance: PulsoControlService? = null
            private set
    }
}
