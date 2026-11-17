package com.example.pulso.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs

/**
 * Pantalla negra de Pulso y su botón flotante en el teléfono. Se usa solo desde el hilo principal.
 *
 * - Botón con luna (siempre visible, se puede arrastrar en vertical): un toque pone la pantalla negra.
 * - Con la pantalla negra, mantener un dedo quieto 3 s en cualquier parte la quita; un anillo
 *   animado muestra el avance. Con el sensor de proximidad tapado (bolsillo) se ignora todo.
 * - La capa negra bloquea los toques del usuario, pero Facebook / Instagram siguen accesibles
 *   para el servicio, así que el reloj los controla igual.
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
    /** Anillo de "mantén presionado" mientras el dedo está apoyado. */
    private var unlock: HoldToUnlockView? = null
    private var isBlack = false

    // Dónde se apoyó el dedo: si se aleja más de [holdSlop] ya no cuenta como mantener.
    private var downX = 0f
    private var downY = 0f
    private val holdSlop = 48 * density

    // Sensor de proximidad: tapado (bolsillo, bolso) = se ignora cualquier toque.
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximity: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var covered = false
    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val sensor = proximity ?: return
            covered = event.values[0] < sensor.maximumRange.coerceAtMost(5f)
            if (covered) cancelHold()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun watchProximity(on: Boolean) {
        val sensor = proximity ?: return
        if (on) sensors.registerListener(proximityListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        else {
            sensors.unregisterListener(proximityListener)
            covered = false
        }
    }

    val isBlackShown: Boolean get() = black != null

    fun start() {
        if (button == null) addButton()
    }

    fun stop() {
        watchProximity(false)
        removeUnlock()
        removeBlack()
        removeButton()
    }

    fun setBlack(on: Boolean) {
        isBlack = on
        watchProximity(on)
        if (on) {
            addBlack()
            // Las capas del mismo tipo se apilan por orden de llegada: el botón debe ir encima.
            removeButton()
            addButton()
        } else {
            dismissUnlock()
            removeBlack()
            styleButton()
        }
    }

    /**
     * Toques con la pantalla negra. Para salir hay que mantener un dedo quieto [HOLD_MS]: aparece
     * un anillo que se llena; soltar, mover el dedo o apoyar un segundo dedo lo cancela.
     */
    fun handleTouch(e: MotionEvent) {
        if (!isBlack) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // En el bolsillo (sensor tapado) los roces no pueden ni empezar a desbloquear.
                if (covered) {
                    Log.d(TAG, "Toque ignorado: sensor de proximidad tapado")
                    return
                }
                downX = e.rawX
                downY = e.rawY
                startHold(e.rawX, e.rawY)
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(e.rawX - downX) > holdSlop || abs(e.rawY - downY) > holdSlop) cancelHold()
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> cancelHold()
        }
    }

    private fun startHold(x: Float, y: Float) {
        removeUnlock()
        val width = dp(150)
        val height = dp(205)
        val view = HoldToUnlockView(context, HOLD_MS) { onUserRequest(false) }
        val metrics = context.resources.displayMetrics
        // El anillo va por encima del dedo para que no lo tape.
        val params = baseParams(width, height, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - width / 2f).toInt().coerceIn(0, metrics.widthPixels - width)
            this.y = (y - height - dp(40)).toInt().coerceIn(0, metrics.heightPixels - height)
        }
        if (add(view, params)) {
            unlock = view
            view.start()
        }
    }

    private fun cancelHold() {
        val view = unlock ?: return
        // Ya se completó: soltar el dedo no lo cancela; lo quita setBlack(false) al desbloquear.
        if (view.isDone) return
        unlock = null
        view.cancel { remove(view) }
    }

    private fun removeUnlock() {
        unlock?.let { remove(it) }
        unlock = null
    }

    /** Al desbloquear: el anillo completo se desvanece en lugar de quedarse en pantalla. */
    private fun dismissUnlock() {
        val view = unlock ?: return
        unlock = null
        if (view.isDone) view.dismiss { remove(view) } else remove(view)
        // Red de seguridad: si la animación no llega a terminar, se quita igual.
        handler.postDelayed({ remove(view) }, 1_000)
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
            view.contentDescription = "Mantén presionada la pantalla 3 segundos para desbloquear"
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

    private fun dp(value: Int) = (value * density).toInt()

    companion object {
        private const val TAG = "PulsoOverlay"
        private const val KEY_Y = "button_y"
        /** Tiempo que hay que mantener el dedo para salir de la pantalla negra. */
        private const val HOLD_MS = 3_000L
    }
}
