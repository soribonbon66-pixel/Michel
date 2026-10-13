# Michel — mejoras de procesamiento DSP

Esta versión incorpora cambios en el motor de audio y en la exportación.

## Cambios

- **Limitador lookahead revisado:** detector de pico máximo por ventana mediante una cola monótona enlazada en estéreo, ataque inmediato con lookahead y liberación suavizada. La salida se limita al techo de muestra configurado.
- **Latencia del limitador compensada:** se vacía el búfer al terminar el render y se recorta la latencia inicial. Esto conserva la duración y evita perder el final de la canción.
- **Comprobación de pico verdadero:** después del render, Michel mide el pico verdadero 4x existente en el proyecto. Si supera el techo configurado, aplica la atenuación global mínima necesaria y vuelve a medir.
- **Compresor multibanda conectado:** las bandas baja, media y alta procesan realmente la señal, con detección enlazada entre canales, umbral, ratio, ataque, release, bypass y ganancia de salida por banda. Los cruces están fijados en 250 Hz y 4,5 kHz, de acuerdo con las etiquetas actuales de la interfaz.
- **Exportación WAV PCM de 24 bits:** se actualizó la escritura RIFF/WAV, el tamaño de bloque, la tasa de bits y el dither TPDF para 24 bits. Se conserva la frecuencia de muestreo original del audio.
- **Contador de picos revisado:** ya no compara el pico de entrada con el pico de salida de otro instante temporal, algo incorrecto con un limitador lookahead.
- **Pruebas de regresión añadidas:** recombinación transparente de las bandas con ajustes neutros y control de pico/latencia del limitador.

## Validación realizada

- Compilación aislada con `kotlinc` de los modelos y los componentes DSP modificados.
- Prueba de humo: con los ajustes multibanda neutros, la diferencia máxima de señal fue aproximadamente `3e-8`; un transitorio por encima de techo quedó limitado a aproximadamente `-0,2 dBFS` después de compensar la latencia.
- No se pudo ejecutar `./gradlew assembleDebug` en este entorno porque el wrapper no pudo descargar Gradle al no haber conectividad DNS con `services.gradle.org`. Por tanto, esta entrega no incluye un APK y la compilación completa de Android debe verificarse en Android Studio o en un entorno con Gradle disponible.

## Nota de uso

La compresión multibanda es configurable y no debe usarse para forzar compresión por defecto. Para un máster musical, compara siempre con el volumen igualado, escucha en varios sistemas y comprueba LUFS integrados, pico verdadero, distorsión y compatibilidad mono antes de distribuir.
