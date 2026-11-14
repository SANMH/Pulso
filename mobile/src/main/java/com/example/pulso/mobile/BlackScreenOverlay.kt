package com.example.pulso.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/**
 * Pantalla negra de Pulso y su botón flotante en el teléfono. Se usa solo desde el hilo principal.
 *
 * - Botón con luna (siempre visible, se puede arrastrar en vertical): un toque pone la pantalla negra.
 * - Con la pantalla negra, cualquier toque muestra "Desbloquear" en el centro durante unos segundos;
 *   tocarlo quita la pantalla negra. Un roce accidental solo hace aparecer el botón.
 * - La capa negra bloquea los toques del usuario, pero Facebook / Instagram siguen accesibles
 *   para el servicio, así que el reloj los controla igual. En Android 14+ el servicio además
 *   intercepta la pantalla táctil entera (bordes incluidos) y pasa los toques a [handleTouch].
 */
class BlackScreenOverlay(
    private val context: Context,
    /** El usuario pidió desde el teléfono activar (true) o quitar (false) la pantalla negra. */
    private val onUserRequest: (Boolean) -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("pulso_overlay", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var black: View? = null
    private var blackParams: WindowManager.LayoutParams? = null
    private var button: ImageView? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var unlock: TextView? = null
    private var isBlack = false

    // Seguimiento de un toque para distinguirlo de un deslizamiento.
    private var downX = 0f
    private var downY = 0f
    private var downMoved = false

    private val hideUnlock = Runnable { removeUnlock() }

    val isBlackShown: Boolean get() = black != null

    fun start() {
        if (button == null) addButton()
    }

    fun stop() {
        removeUnlock()
        removeBlack()
        removeButton()
    }

    fun setBlack(on: Boolean) {
        isBlack = on
        if (on) {
            addBlack()
            // Las capas del mismo tipo se apilan por orden de llegada: el botón debe ir encima.
            removeButton()
            addButton()
        } else {
            removeUnlock()
            removeBlack()
            styleButton()
        }
    }

    /**
     * Toques con la pantalla negra (de la propia capa o interceptados por el servicio en toda
     * la pantalla). Coordenadas absolutas de pantalla.
     */
    fun handleTouch(e: MotionEvent) {
        if (!isBlack) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                downMoved = false
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(e.rawX - downX) > touchSlop || abs(e.rawY - downY) > touchSlop) downMoved = true
            MotionEvent.ACTION_UP -> if (!downMoved) onTap(e.rawX, e.rawY)
        }
    }

    private fun onTap(x: Float, y: Float) {
        val target = unlock
        Log.d(TAG, "Toque en ($x, $y), desbloquear visible=${target != null}")
        if (target != null && target.screenRect().contains(x.toInt(), y.toInt())) {
            target.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            onUserRequest(false)
        } else {
            showUnlock()
        }
    }

    private fun showUnlock() {
        handler.removeCallbacks(hideUnlock)
        if (unlock == null) {
            val view = TextView(context).apply {
                text = "Desbloquear"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                setPadding(dp(36), dp(18), dp(36), dp(18))
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pulso_lock_open, 0, 0, 0)
                compoundDrawablePadding = dp(12)
                background = GradientDrawable().apply {
                    cornerRadius = dp(32).toFloat()
                    setColor(0xFF2A2A2A.toInt())
                    setStroke(dp(1), 0xFF555555.toInt())
                }
                contentDescription = "Desbloquear pantalla"
            }
            // Sin servicio interceptando (Android < 14) el botón recibe el toque directamente.
            view.setOnClickListener { onUserRequest(false) }
            val params = baseParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                0
            ).apply { gravity = Gravity.CENTER }
            if (add(view, params)) unlock = view
        }
        handler.postDelayed(hideUnlock, UNLOCK_VISIBLE_MS)
    }

    private fun removeUnlock() {
        handler.removeCallbacks(hideUnlock)
        unlock?.let { remove(it) }
        unlock = null
    }

    /**
     * Deja pasar los toques mientras Pulso desliza la pantalla (último recurso cuando la app no
     * ofrece acciones de accesibilidad). Tus dedos siguen bloqueados el resto del tiempo.
     */
    fun setPassThrough(pass: Boolean) {
        val view = black ?: return
        val params = blackParams ?: return
        params.flags = if (pass) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBlack() {
        if (black != null) return
        // Se traga todos los toques. Al ser una capa de accesibilidad, Android NO oculta por ello a
        // Facebook / Instagram del servicio, así que el reloj sigue pudiendo pasar videos.
        val view = View(context).apply {
            setBackgroundColor(Color.BLACK)
            setOnTouchListener { _, e -> handleTouch(e); true }
        }
        val params = baseParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            0
        )
        if (add(view, params)) {
            black = view
            blackParams = params
        }
    }

    private fun removeBlack() {
        black?.let { remove(it) }
        black = null
        blackParams = null
    }

    private fun addButton() {
        val size = dp(44)
        val view = ImageView(context).apply {
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x99303030.toInt())
            }
        }
        val params = baseParams(size, size, 0).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(4)
            y = prefs.getInt(KEY_Y, context.resources.displayMetrics.heightPixels / 2)
        }
        attachTouch(view, params)
        if (add(view, params)) {
            button = view
            buttonParams = params
            styleButton()
        }
    }

    private fun removeButton() {
        button?.let { remove(it) }
        button = null
        buttonParams = null
    }

    private fun styleButton() {
        val view = button ?: return
        if (isBlack) {
            view.setImageResource(R.drawable.ic_pulso_lock)
            view.alpha = 0.35f
            view.contentDescription = "Toca para mostrar Desbloquear"
        } else {
            view.setImageResource(R.drawable.ic_pulso_moon)
            view.alpha = 0.85f
            view.contentDescription = "Pantalla negra de Pulso"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(view: ImageView, params: WindowManager.LayoutParams) {
        var startY = 0
        var moved = false
        var y0 = 0f
        view.setOnTouchListener { v, e ->
            if (isBlack) {
                handleTouch(e)
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    y0 = e.rawY
                    startY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - y0
                    if (abs(dy) > touchSlop) moved = true
                    if (moved) {
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP ->
                    if (moved) prefs.edit().putInt(KEY_Y, params.y).apply()
                    else {
                        v.performClick()
                        onUserRequest(true)
                    }
            }
            true
        }
    }

    private fun baseParams(width: Int, height: Int, extraFlags: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            extraFlags,
        PixelFormat.TRANSLUCENT
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun add(view: View, params: WindowManager.LayoutParams): Boolean = try {
        wm.addView(view, params)
        true
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo mostrar la capa", e)
        false
    }

    private fun remove(view: View) {
        runCatching { wm.removeView(view) }
    }

    private fun View.screenRect(): Rect {
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        // Margen extra para que sea fácil acertar con el dedo.
        val pad = dp(12)
        return Rect(loc[0] - pad, loc[1] - pad, loc[0] + width + pad, loc[1] + height + pad)
    }

    private fun dp(value: Int) = (value * density).toInt()

    companion object {
        private const val TAG = "PulsoOverlay"
        private const val KEY_Y = "button_y"
        private const val UNLOCK_VISIBLE_MS = 3_000L
    }
}
