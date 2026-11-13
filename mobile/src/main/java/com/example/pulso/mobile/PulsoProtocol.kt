package com.example.pulso.mobile

/** Mensajes entre el reloj y el teléfono. Debe coincidir con el módulo :app (reloj). */
object PulsoProtocol {
    const val CMD_PATH = "/pulso/cmd"
    const val ACK_PATH = "/pulso/ack"

    const val CMD_NEXT = "next"
    const val CMD_PREV = "prev"
    const val CMD_BACK = "back"
    const val CMD_OPEN_FACEBOOK = "open_fb"
    const val CMD_OPEN_FACEBOOK_VIDEO = "open_fb_video"
    const val CMD_OPEN_INSTAGRAM = "open_ig"
    const val CMD_OPEN_INSTAGRAM_VIDEO = "open_ig_video"
    const val CMD_PING = "ping"

    const val ACK_OK = "ok"
    const val ACK_NO_SERVICE = "no_service"
    const val ACK_NO_APP = "no_app"
    const val ACK_ERROR = "error"

    const val PKG_FACEBOOK = "com.facebook.lite"
    const val PKG_INSTAGRAM = "com.instagram.android"
}
