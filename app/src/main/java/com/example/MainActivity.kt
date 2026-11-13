package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.remote.PhoneLink
import com.example.remote.RemoteControlScreen
import com.example.ui.theme.PulsoTheme

/**
 * Pulso en el reloj: mando a distancia para Facebook e Instagram en el teléfono.
 * (El lector de Bluesky/Mastodon/Reddit de ui/PulsoApp.kt queda en el código pero ya no se muestra.)
 */
class MainActivity : ComponentActivity() {
    private lateinit var phoneLink: PhoneLink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        phoneLink = PhoneLink(this, lifecycleScope)
        setContent {
            PulsoTheme {
                RemoteControlScreen(phoneLink)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        phoneLink.start()
    }

    override fun onPause() {
        phoneLink.stop()
        super.onPause()
    }
}
