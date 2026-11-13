package com.example.pulso.mobile

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.Wearable

/** Pantalla de configuración en el teléfono: estado del servicio y del reloj. */
class MainActivity : Activity() {

    private lateinit var serviceStatus: TextView
    private lateinit var watchStatus: TextView
    private lateinit var enableButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Pulso"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#22D3EE"))
        }
        val subtitle = TextView(this).apply {
            text = "Controla Facebook Lite e Instagram desde tu reloj: gira el bisel o desliza para pasar " +
                "publicaciones y videos, o salta directo a su sección de video."
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, pad / 3, 0, pad)
        }
        serviceStatus = statusText()
        watchStatus = statusText()
        enableButton = Button(this).apply {
            text = "Activar Pulso en Accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val help = TextView(this).apply {
            text = "En Accesibilidad → Aplicaciones instaladas → Pulso: control desde el reloj → Activar."
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, pad / 3, 0, 0)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
            addView(title); addView(subtitle); addView(serviceStatus); addView(watchStatus)
            addView(enableButton); addView(help)
        })
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        serviceStatus.text = if (enabled) "✓ Control activado" else "✗ Control desactivado"
        serviceStatus.setTextColor(Color.parseColor(if (enabled) "#34D399" else "#FB7185"))
        enableButton.text = if (enabled) "Abrir ajustes de Accesibilidad" else "Activar Pulso en Accesibilidad"

        watchStatus.text = "Buscando reloj…"
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                watchStatus.text = if (nodes.isEmpty()) "✗ Ningún reloj conectado"
                else "✓ Reloj conectado: " + nodes.joinToString { it.displayName }
                watchStatus.setTextColor(Color.parseColor(if (nodes.isEmpty()) "#FB7185" else "#34D399"))
            }
            .addOnFailureListener { watchStatus.text = "✗ No se pudo consultar el reloj" }
    }

    private fun statusText() = TextView(this).apply {
        textSize = 17f
        setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, PulsoControlService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
