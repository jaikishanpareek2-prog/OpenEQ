package app.openeq

import android.media.audiofx.Visualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

object Viz {
    const val N = 24
    val bars = FloatArray(N)
    var tick by mutableIntStateOf(0)
    private var v: Visualizer? = null

    fun level(): Float = (bars[0] + bars[1] + bars[2] + bars[3]) / 4f

    @Synchronized
    fun start(session: Int) {
        stop()
        try {
            val vz = Visualizer(session)
            vz.setEnabled(false)
            vz.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
            vz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) feed(fft)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            vz.setEnabled(true)
            v = vz
        } catch (_: Throwable) {
            v = null
        }
    }

    @Synchronized
    fun stop() {
        try { v?.setEnabled(false); v?.release() } catch (_: Throwable) {}
        v = null
    }

    private fun edge(b: Int, n: Int): Int = (n - 1).toDouble().pow(b.toDouble() / N).toInt().coerceAtLeast(1)

    private fun feed(fft: ByteArray) {
        val n = fft.size / 2
        for (b in 0 until N) {
            val e0 = edge(b, n)
            val e1 = maxOf(edge(b + 1, n), e0 + 1)
            var m = 0f
            var k = e0
            while (k < e1 && k < n) {
                val mag = hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
                if (mag > m) m = mag
                k++
            }
            val t = (ln(1f + m) / ln(129f)).coerceIn(0f, 1f)
            if (t > bars[b]) bars[b] = t
        }
        tick++
    }

    fun fade() {
        for (i in 0 until N) bars[i] = bars[i] * 0.9f
        tick++
    }
}
