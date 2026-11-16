package com.example.pulso.mobile

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.min

/**
 * Anillo que se llena mientras se mantiene el dedo en la pantalla negra. Al completarse llama a
 * [onComplete]; si se suelta antes, se vacía y desaparece con [cancel].
 */
class HoldToUnlockView(
    context: Context,
    private val holdMillis: Long,
    private val onComplete: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val stroke = 7f * density

    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6161616.toInt() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = 0xFF3A3A3A.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = ACCENT
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 15f * resources.displayMetrics.scaledDensity
    }
    private val count = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 13f * resources.displayMetrics.scaledDensity
    }

    private val lockClosed: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_pulso_lock)
    private val lockOpen: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_pulso_lock_open)

    private var progress = 0f
    private var done = false
    private var animator: ValueAnimator? = null
    private val ring = RectF()

    /** Empieza a llenar el anillo (con una pequeña aparición). */
    fun start() {
        alpha = 0f
        scaleX = 0.8f
        scaleY = 0.8f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = holdMillis
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    done = true
                    invalidate()
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).withEndAction { onComplete() }.start()
                }
            })
            start()
        }
    }

    /** Se soltó antes de tiempo: el anillo se vacía y [whenGone] lo quita de la pantalla. */
    fun cancel(whenGone: () -> Unit) {
        if (done) return
        animator?.cancel()
        ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        animate().alpha(0f).setDuration(220).withEndAction(whenGone).start()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val radius = min(width, width) / 2f - stroke
        val cy = radius + stroke
        canvas.drawCircle(cx, cy, radius + stroke / 2, disc)
        ring.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(ring, 0f, 360f, false, track)
        canvas.drawArc(ring, -90f, 360f * progress, false, arc)

        val icon = if (done) lockOpen else lockClosed
        val half = (radius * 0.42f).toInt()
        icon?.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon?.setTint(if (done) ACCENT else Color.WHITE)
        icon?.draw(canvas)

        val below = cy + radius + stroke + 24f * density
        canvas.drawText(if (done) "Desbloqueado" else "Mantén presionado", cx, below, label)
        if (!done) {
            val left = ceil((1f - progress) * holdMillis / 1000f).toInt().coerceAtLeast(1)
            canvas.drawText("$left s", cx, below + 20f * density, count)
        }
    }

    companion object {
        private const val ACCENT = 0xFF22D3EE.toInt()
    }
}
