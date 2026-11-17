# Pulso

Controla **Facebook Lite** e **Instagram** del teléfono desde tu reloj con **Wear OS**. Con Pulso puedes:

- pasar publicaciones y videos;
- ir directo a los Reels;
- subir o bajar el volumen;
- omitir anuncios;
- apagar la pantalla del teléfono sin dejar de oír el video.

Además, el reloj tiene un widget con botones rápidos.

**[Descargar e instrucciones de instalación →](https://sanmh.github.io/Pulso/)**

Para los apresurados, estos son los archivos de la última versión:

| App | Descarga |
|---|---|
| Teléfono (Android 8+) | [`pulso-telefono.apk`](https://github.com/SANMH/Pulso/releases/latest/download/pulso-telefono.apk) |
| Reloj (Wear OS) | [`pulso-reloj.apk`](https://github.com/SANMH/Pulso/releases/latest/download/pulso-reloj.apk) |

Necesitas las dos. Después de instalar la del teléfono, activa **Pulso** en *Accesibilidad*. La del reloj se instala por depuración inalámbrica. La página explica todo paso a paso.

## Cómo funciona

```
Reloj (:app)  ──  Wearable Data Layer  /pulso/cmd  ──▶  Teléfono (:mobile)
  Compose + Tile                                           AccessibilityService
              ◀──  /pulso/ack (resultado, app, volumen)  ──
```

- **`app/`**: la app del reloj (Jetpack Compose) y el widget (`remote/PulsoTileService.kt`).
- **`mobile/`**: la app del teléfono. Su servicio de accesibilidad toca y desplaza Facebook Lite e Instagram, y muestra la pantalla negra.
- Las dos apps comparten `applicationId` y **deben estar firmadas con la misma llave**. Si no, Wear OS no entrega los mensajes entre ellas.

## Compilar

Necesitas Android Studio (o su JDK) y el SDK de Android:

```bash
./gradlew :app:assembleDebug :mobile:assembleDebug
```

Para firmar, coloca un `debug.keystore` en la raíz o usa la configuración `release` con `my-upload-key.jks` y las variables `STORE_PASSWORD` y `KEY_PASSWORD`. Ninguno de esos archivos se sube al repositorio.

## Privacidad

Pulso no tiene cuentas, analíticas ni anuncios, y no se conecta a ningún servidor. Las órdenes viajan solo entre tu reloj y tu teléfono. Nunca da "me gusta", ni comenta, ni publica.

## Aviso legal

Proyecto independiente y no oficial. **No está afiliado, patrocinado ni aprobado por Meta Platforms, Inc., Google LLC ni Samsung Electronics.**

- **Marcas:** Facebook, Facebook Lite e Instagram son marcas de Meta Platforms, Inc. Android y Wear OS son marcas de Google LLC.
- **Sin garantía:** se ofrece "tal cual", sin garantías. Los términos de las plataformas pueden limitar el uso de herramientas automatizadas; el usuario es responsable de cumplirlos.

Encontrarás más detalles en la [página del proyecto](https://sanmh.github.io/Pulso/#legal).

## Licencia

[MIT](LICENSE) © 2026 SANMH
