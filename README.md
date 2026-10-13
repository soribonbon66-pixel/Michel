# Michel — Mastering PRO

App Android de mastering offline construida en Kotlin, Jetpack Compose y Material 3. El flujo es: **cargar audio → analizar → editar sugerencias → aplicar → comparar y exportar**.

## Compilar

Requisitos: Android Studio/JDK 17 y Android SDK Platform 34.

```bash
chmod +x gradlew
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. La acción de GitHub Actions `Build & Package Debug APK` se ejecuta en cada push y publica el archivo como artefacto descargable **Michel-PRO-APK**.

Para subir una copia descomprimida a un repositorio nuevo:

```bash
git init
git add .
git commit -m "Michel Mastering PRO"
git branch -M main
git remote add origin <URL-del-repositorio>
git push -u origin main
```

## Uso

1. Pulsa **CARGAR AUDIO** y elige un archivo compatible con el decoder del dispositivo (por ejemplo MP3, WAV, AAC/M4A o FLAC).
2. Michel copia el archivo seleccionado a `cacheDir`, lo decodifica con `MediaExtractor` + `MediaCodec` y muestra el análisis inicial.
3. Ajusta ganancia, EQ, compresor y limitador, o usa **Reset a sugerido**.
4. Pulsa **APLICAR MASTERING**. El render aplica ganancia → EQ RBJ → compresor → limitador → normalización a -14 LUFS, con un trim final de True Peak para seguridad.
5. Compara las mediciones, waveform y espectro; usa los botones A/B; el WAV se guarda en `Music/Michel` mediante MediaStore (Android 10+) y se puede compartir.

La app no declara permiso de Internet ni llama a servicios remotos. Los permisos de medios del manifest son para compatibilidad; el selector del sistema concede acceso al documento elegido.

## Procesamiento y análisis

- El decoder normaliza a PCM float mono/stereo a 44.1 o 48 kHz (remuestreo lineal cuando hace falta).
- El medidor de loudness usa filtros K-weighting RBJ, bloques de 400 ms con paso de 100 ms y gating absoluto -70 / relativo -10 LU; LRA se estima con ventanas gated de 3 s.
- True Peak se estima con sobremuestreo interpolado 4×. Clipping cuenta muestras superiores a -0.1 dBFS.
- El espectro es el promedio de ventanas Hann FFT-4096. Las alertas de balance espectral, BPM y tonalidad son estimaciones heurísticas y deben confirmarse escuchando la mezcla.
- El master se escribe como WAV IEEE-float 32-bit. El procesamiento trabaja en memoria; para pistas muy largas se recomienda disponer de espacio libre suficiente.

## Estructura

```text
app/src/main/java/com/michel/masteringpro/
  audio/       carga, análisis, sugerencias, procesador, WAV y escucha A/B
  dsp/         EQ RBJ, compresor, limitador, EBU R128, FFT y True Peak
  model/       modelos de audio, análisis, ajustes y estado de UI
  ui/          MichelApp, AnalysisScreen, ResultScreen y componentes Compose
  viewmodel/   estado MVVM con StateFlow
```
