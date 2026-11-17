package com.example.remote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.MainActivity
import com.example.R
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Widget deslizable de Pulso (Tile): botones rápidos sin abrir la app.
 *
 * Cada botón es un [ModifiersBuilders.Clickable] con [ActionBuilders.LoadAction]: al tocarlo, el
 * sistema vuelve a pedir el widget con el id del botón en `lastClickableId`, y aquí se manda la
 * orden al teléfono. Tocar "Pulso" abre la app completa.
 */
class PulsoTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val clicked = requestParams.currentState.lastClickableId
        if (clicked in COMMANDS) sendToPhone(clicked)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout()))
            .build()
        return immediate(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        ICONS.forEach { (id, res) ->
            resources.addIdToImageMapping(
                id,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(res).build()
                    )
                    .build()
            )
        }
        return immediate(resources.build())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun sendToPhone(command: String) {
        vibrate()
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@PulsoTileService).connectedNodes.await()
                if (nodes.isEmpty()) Log.w(TAG, "Teléfono no conectado: '$command' no se envió")
                val messages = Wearable.getMessageClient(this@PulsoTileService)
                nodes.forEach {
                    messages.sendMessage(it.id, PulsoProtocol.CMD_PATH, command.toByteArray(Charsets.UTF_8)).await()
                }
                Log.i(TAG, "Orden '$command' enviada desde el widget")
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enviar '$command'", e)
            }
        }
    }

    // ---- Diseño ----

    private fun layout(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(title())
            .addContent(gap(8))
            .addContent(
                row(
                    button(PulsoProtocol.CMD_OPEN_FACEBOOK_VIDEO, ICON_FB_VIDEO, "Videos de Facebook", FACEBOOK_BLUE),
                    button(PulsoProtocol.CMD_BLACK_SCREEN, ICON_MOON, "Pantalla negra", CHIP, ACCENT),
                    button(PulsoProtocol.CMD_OPEN_INSTAGRAM_VIDEO, ICON_IG_REELS, "Reels de Instagram", INSTAGRAM_PINK)
                )
            )
            .addContent(gap(6))
            .addContent(
                row(
                    button(PulsoProtocol.CMD_PREV, ICON_UP, "Anterior", CHIP, width = 64),
                    button(PulsoProtocol.CMD_NEXT, ICON_DOWN, "Siguiente", CHIP, ACCENT, width = 64)
                )
            )
            .addContent(gap(6))
            .addContent(
                row(
                    button(PulsoProtocol.CMD_VOLUME_DOWN, ICON_VOL_DOWN, "Bajar volumen", CHIP),
                    button(PulsoProtocol.CMD_VOLUME_UP, ICON_VOL_UP, "Subir volumen", CHIP)
                )
            )
            .build()

    /** "Pulso" en la parte de arriba: abre la app completa. */
    private fun title(): LayoutElementBuilders.LayoutElement {
        val open = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()
        return LayoutElementBuilders.Text.Builder()
            .setText("Pulso")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(16f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .setColor(argb(ACCENT))
                    .build()
            )
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId("open_app").setOnClick(open).build())
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(6f)).build())
                    .build()
            )
            .build()
    }

    private fun row(vararg items: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement {
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        items.forEachIndexed { i, item ->
            if (i > 0) row.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(8f)).build())
            row.addContent(item)
        }
        return row.build()
    }

    private fun gap(height: Int) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(height.toFloat())).build()

    /** Botón redondo (o cápsula si [width] > alto) con un ícono; al tocarlo manda [command]. */
    private fun button(
        command: String,
        icon: String,
        description: String,
        background: Int,
        iconColor: Int = WHITE,
        width: Int = SIZE
    ): LayoutElementBuilders.LayoutElement {
        val image = LayoutElementBuilders.Image.Builder()
            .setResourceId(icon)
            .setWidth(dp(24f))
            .setHeight(dp(24f))
            .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(iconColor)).build())
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(width.toFloat()))
            .setHeight(dp(SIZE.toFloat()))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(image)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(background))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(SIZE / 2f)).build())
                            .build()
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(command)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build()
                    )
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build())
                    .build()
            )
            .build()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun <T> immediate(value: T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { it.set(value); "pulso-tile" }

    companion object {
        private const val TAG = "PulsoTile"
        private const val RESOURCES_VERSION = "1"
        private const val SIZE = 46

        private const val ICON_UP = "up"
        private const val ICON_DOWN = "down"
        private const val ICON_MOON = "moon"
        private const val ICON_VOL_DOWN = "vol_down"
        private const val ICON_VOL_UP = "vol_up"
        private const val ICON_FB_VIDEO = "fb_video"
        private const val ICON_IG_REELS = "ig_reels"

        private val ICONS = mapOf(
            ICON_UP to R.drawable.ic_tile_up,
            ICON_DOWN to R.drawable.ic_tile_down,
            ICON_MOON to R.drawable.ic_tile_moon,
            ICON_VOL_DOWN to R.drawable.ic_tile_vol_down,
            ICON_VOL_UP to R.drawable.ic_tile_vol_up,
            ICON_FB_VIDEO to R.drawable.ic_tile_fb_video,
            ICON_IG_REELS to R.drawable.ic_tile_ig_reels
        )

        /** Órdenes que puede mandar el widget (el id de cada botón es la orden). */
        private val COMMANDS = setOf(
            PulsoProtocol.CMD_PREV,
            PulsoProtocol.CMD_NEXT,
            PulsoProtocol.CMD_BLACK_SCREEN,
            PulsoProtocol.CMD_OPEN_FACEBOOK_VIDEO,
            PulsoProtocol.CMD_OPEN_INSTAGRAM_VIDEO,
            PulsoProtocol.CMD_VOLUME_DOWN,
            PulsoProtocol.CMD_VOLUME_UP
        )

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val ACCENT = 0xFF22D3EE.toInt()
        private const val CHIP = 0xFF1E293B.toInt()
        private const val FACEBOOK_BLUE = 0xFF1877F2.toInt()
        private const val INSTAGRAM_PINK = 0xFFE1306C.toInt()
    }
}
