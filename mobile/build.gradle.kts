plugins {
  alias(libs.plugins.android.application)
}

android {
  // Mismo applicationId y misma firma que el módulo del reloj: la Wearable Data Layer
  // solo entrega mensajes entre apps con el mismo paquete y el mismo certificado.
  namespace = "com.example.pulso.mobile"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.pulsowatch.xqrmkv"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  signingConfigs {
    // La misma llave de subida que el reloj (:app): sin ella el teléfono no recibe sus órdenes.
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
}
