package com.michel.masteringpro.dsp

import com.michel.masteringpro.model.MasteringSettings

/** Four RBJ EQ points: low shelf, 300 Hz body bell, 3.5 kHz presence bell and high shelf. */
class Eq(sampleRate: Int, settings: MasteringSettings) {
    private val low = BiquadFilter(BiquadFilter.Type.LOW_SHELF, 80f, settings.lowEqDb, 0.707f, sampleRate)
    private val body = BiquadFilter(BiquadFilter.Type.PEAKING, 300f, settings.bodyEqDb, 1.2f, sampleRate)
    private val presence = BiquadFilter(BiquadFilter.Type.PEAKING, 3_500f, settings.presenceEqDb, 1.2f, sampleRate)
    private val air = BiquadFilter(BiquadFilter.Type.HIGH_SHELF, 12_000f, settings.airEqDb, 0.707f, sampleRate)

    var outputLeft: Float = 0f
        private set
    var outputRight: Float = 0f
        private set

    fun processFrame(left: Float, right: Float, channels: Int) {
        var l = low.processSample(left, 0)
        l = body.processSample(l, 0)
        l = presence.processSample(l, 0)
        l = air.processSample(l, 0)
        outputLeft = l

        if (channels > 1) {
            var r = low.processSample(right, 1)
            r = body.processSample(r, 1)
            r = presence.processSample(r, 1)
            r = air.processSample(r, 1)
            outputRight = r
        } else {
            outputRight = l
        }
    }
}
