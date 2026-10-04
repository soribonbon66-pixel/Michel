package com.example.dsp

import kotlin.math.tanh

/**
 * Analog Tube/Tape Harmonic Saturation.
 * Adds 2nd and 3rd harmonics for punch and analog body in urban masters.
 */
class HarmonicSaturator(
    private var drive: Float = 0.2f
) {
    fun setDrive(drive: Float) {
        this.drive = drive.coerceIn(0.0f, 1.0f)
    }

    fun processSample(input: Float): Float {
        if (drive <= 0.01f) return input
        // Boost into non-linear transfer function
        val gain = 1.0f + drive * 2.5f
        val x = input * gain
        // Soft tube saturation curve: tanh(x) with subtle 2nd harmonic asymmetry
        val saturated = tanh(x.toDouble() + 0.08 * x.toDouble() * x.toDouble()).toFloat()
        // Level compensation
        val makeup = 1.0f / (1.0f + drive * 0.8f)
        return saturated * makeup
    }
}
