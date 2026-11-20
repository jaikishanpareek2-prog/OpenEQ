package app.openeq

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// type: 0 peak, 1 low shelf, 2 high shelf
data class PBand(val type: Int, val f: Float, val g: Float, val q: Float)

private class Coef(val b0: Double, val b1: Double, val b2: Double, val a0: Double, val a1: Double, val a2: Double)

private fun coefOf(b: PBand): Coef {
    val a = 10.0.pow(b.g / 40.0)
    val w0 = 2.0 * PI * b.f / 48000.0
    val cw = cos(w0)
    val alpha = sin(w0) / (2.0 * b.q)
    return when (b.type) {
        1 -> {
            val s = 2.0 * sqrt(a) * alpha
            Coef(
                a * ((a + 1) - (a - 1) * cw + s), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - s),
                (a + 1) + (a - 1) * cw + s, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - s
            )
        }
        2 -> {
            val s = 2.0 * sqrt(a) * alpha
            Coef(
                a * ((a + 1) + (a - 1) * cw + s), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - s),
                (a + 1) - (a - 1) * cw + s, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - s
            )
        }
        else -> Coef(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
    }
}

private fun magDb(c: Coef, f: Double): Double {
    val w = 2.0 * PI * f / 48000.0
    val c1 = cos(w)
    val s1 = sin(w)
    val c2 = cos(2 * w)
    val s2 = sin(2 * w)
    val nr = c.b0 + c.b1 * c1 + c.b2 * c2
    val ni = -(c.b1 * s1 + c.b2 * s2)
    val dr = c.a0 + c.a1 * c1 + c.a2 * c2
    val di = -(c.a1 * s1 + c.a2 * s2)
    return 10.0 * log10((nr * nr + ni * ni) / (dr * dr + di * di + 1e-20) + 1e-20)
}

private fun interp(f: Float, xs: FloatArray, ys: FloatArray): Float {
    if (f <= xs[0]) return ys[0]
    if (f >= xs[xs.size - 1]) return ys[ys.size - 1]
    for (i in 1 until xs.size) {
        if (f <= xs[i]) {
            val t = ln(f / xs[i - 1]) / ln(xs[i] / xs[i - 1])
            return ys[i - 1] + (ys[i] - ys[i - 1]) * t
        }
    }
    return ys[ys.size - 1]
}

object EqEngine {
    val LABELS = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    private val EDGES = floatArrayOf(44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 22000f)
    private val WB = floatArrayOf(1f, 1f, 0.8f, 0.4f, 0.1f, 0f, 0f, 0f, 0f, 0f)
    private val WT = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.1f, 0.4f, 0.8f, 1f, 1f)
    private val CENTERS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val XB = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f)
    private val YB = floatArrayOf(1f, 1f, 0.8f, 0.4f, 0.1f, 0f)
    private val XT = floatArrayOf(500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val YT = floatArrayOf(0f, 0.1f, 0.4f, 0.8f, 1f, 1f)

    val BUILTIN: Map<String, List<Float>> = linkedMapOf(
        "Flat" to List(10) { 0f },
        "Bass Boost" to listOf(5f, 4f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Vocal" to listOf(-2f, -1f, 0f, 1f, 2f, 3f, 2f, 1f, 0f, -1f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f, 5f),
        "In ear" to listOf(0f, 2f, 0f, -2f, -1f, 0f, 0f, -2f, -1f, 1f),
        "Phone speaker" to listOf(0f, 0f, 0f, 2f, 1f, 0f, 1f, -2f, -1f, 0f)
    )

    var enabled by mutableStateOf(false)
    var preamp by mutableFloatStateOf(0f)
    var preset by mutableStateOf("Flat")
    var bass by mutableFloatStateOf(0f)
    var treble by mutableFloatStateOf(0f)
    var limiterOn by mutableStateOf(false)
    var compOn by mutableStateOf(false)
    var cAttack by mutableFloatStateOf(20f)
    var cRelease by mutableFloatStateOf(200f)
    var cRatio by mutableFloatStateOf(4f)
    var cThresh by mutableFloatStateOf(-20f)
    var cGain by mutableFloatStateOf(0f)
    var balance by mutableFloatStateOf(0f)
    val gains = mutableStateListOf<Float>().apply { repeat(10) { add(0f) } }
    val userPresets = mutableStateMapOf<String, List<Float>>()

    // parametric
    val peqPresets = mutableStateMapOf<String, List<PBand>>()
    val peq = mutableStateListOf<PBand>()
    var peqOn by mutableStateOf(false)
    var peqSel by mutableIntStateOf(0)
    var peqUnsupported by mutableStateOf(false)

    private val effects = HashMap<Int, DynamicsProcessing>()
    private val effBands = HashMap<Int, Int>()
    private val lastG = HashMap<Int, FloatArray>()
    @Volatile private var peqN = 64
    private var ready = false
    private lateinit var ctx: Context
    private val ui = Handler(Looper.getMainLooper())
    private var pending = false

    fun eff(i: Int): Float =
        (gains[i] + bass / 100f * 10f * WB[i] + treble / 100f * 10f * WT[i]).coerceIn(-15f, 15f)

    @Synchronized
    fun init(c: Context) {
        if (ready) return
        ready = true
        ctx = c.applicationContext
        OutputPresets.start(ctx)
        val p = ctx.getSharedPreferences("openeq", Context.MODE_PRIVATE)
        enabled = p.getBoolean("on", false)
        preamp = p.getFloat("pre", 0f)
        preset = p.getString("preset", "Flat") ?: "Flat"
        bass = p.getFloat("bass", 0f)
        treble = p.getFloat("treb", 0f)
        limiterOn = p.getBoolean("lim", false)
        compOn = p.getBoolean("comp", false)
        cAttack = p.getFloat("ca", 20f)
        cRelease = p.getFloat("cr", 200f)
        cRatio = p.getFloat("cra", 4f)
        cThresh = p.getFloat("cth", -20f)
        cGain = p.getFloat("cg", 0f)
        balance = p.getFloat("bal", 0f)
        try {
            p.getString("user", "")?.split(";")?.filter { it.contains("|") }?.forEach {
                val parts = it.split("|")
                userPresets[parts[0]] = parts[1].split(",").map { v -> v.toFloat() }
            }
        } catch (_: Exception) {}
        try {
            val g = p.getString("gains", "")?.split(",")?.map { it.toFloat() }
            if (g != null && g.size == 10) g.forEachIndexed { i, v -> gains[i] = v }
        } catch (_: Exception) {}
        try {
            p.getString("peqp", "")?.split(";")?.filter { it.contains("~") }?.forEach {
                val parts = it.split("~")
                peqPresets[parts[0]] = parts[1].split("|").filter { s -> s.isNotEmpty() }.map { s ->
                    val v = s.split(",")
                    PBand(v[0].toInt(), v[1].toFloat(), v[2].toFloat(), v[3].toFloat())
                }
            }
        } catch (_: Exception) {}
        val pb = peqPresets[preset]
        if (pb != null) {
            peq.clear()
            peq.addAll(pb)
            peqOn = true
            syncGains()
        }
    }

    private fun save() {
        ctx.getSharedPreferences("openeq", Context.MODE_PRIVATE).edit()
            .putBoolean("on", enabled)
            .putFloat("pre", preamp)
            .putString("preset", preset)
            .putString("gains", gains.joinToString(","))
            .putString("user", userPresets.entries.joinToString(";") { it.key + "|" + it.value.joinToString(",") })
            .putString("peqp", peqPresets.entries.joinToString(";") { e ->
                e.key + "~" + e.value.joinToString("|") { b -> "${b.type},${b.f},${b.g},${b.q}" }
            })
            .putFloat("bass", bass)
            .putFloat("treb", treble)
            .putBoolean("lim", limiterOn)
            .putBoolean("comp", compOn)
            .putFloat("ca", cAttack)
            .putFloat("cr", cRelease)
            .putFloat("cra", cRatio)
            .putFloat("cth", cThresh)
            .putFloat("cg", cGain)
            .putFloat("bal", balance)
            .apply()
    }

    fun changed() { applyAll(); save() }

    fun toggle() { enabled = !enabled; applyAll(); save() }

    fun updatePreamp(v: Float) { preamp = v; applyAll(); save() }

    fun setGain(i: Int, v: Float) {
        gains[i] = v
        if (peqOn || BUILTIN.containsKey(preset)) {
            var n = OutputPresets.editName()
            if (peqPresets.containsKey(n)) n = uniqueName(n)
            preset = n
        }
        peqOn = false
        userPresets[preset] = gains.toList()
        OutputPresets.keep()
        applyAll(); save()
    }

    fun select(name: String) {
        val pb = peqPresets[name]
        if (pb != null) {
            preset = name
            peq.clear()
            peq.addAll(pb)
            peqSel = 0
            peqOn = true
            syncGains()
            OutputPresets.keep()
            applyAll(); save()
            return
        }
        val g = BUILTIN[name] ?: userPresets[name] ?: return
        preset = name
        peqOn = false
        OutputPresets.keep()
        g.forEachIndexed { i, v -> gains[i] = v }
        applyAll(); save()
    }

    fun newPreset() {
        if (peqOn) {
            val n = nextName("PEQ")
            peqPresets[n] = peq.toList()
            select(n)
            return
        }
        val n = nextName("My set")
        preset = n
        userPresets[n] = gains.toList()
        OutputPresets.keep()
        save()
    }

    fun names(): List<String> = BUILTIN.keys.toList() + (userPresets.keys + peqPresets.keys).sorted()

    private fun nextName(prefix: String): String {
        var n = 1
        while (names().contains("$prefix $n")) n++
        return "$prefix $n"
    }

    fun uniqueName(base: String): String {
        var b = base.filter { it.isLetterOrDigit() || it == ' ' || it == '.' || it == '_' || it == '(' || it == ')' || it == '+' || it == '-' }
            .trim().take(28)
        if (b.isEmpty()) b = "AutoEq"
        if (!names().contains(b)) return b
        var n = 2
        while (names().contains("$b $n")) n++
        return "$b $n"
    }

    fun cloneCurrentAs(n: String): String {
        val m = uniqueName(n)
        if (peqOn) peqPresets[m] = peq.toList() else userPresets[m] = gains.toList()
        return m
    }

    // ---------- parametric ----------

    fun peqStart() {
        val n = nextName("PEQ")
        peqPresets[n] = listOf(
            PBand(1, 80f, 0f, 0.7f), PBand(0, 250f, 0f, 1f), PBand(0, 1000f, 0f, 1f),
            PBand(0, 4000f, 0f, 1f), PBand(2, 10000f, 0f, 0.7f)
        )
        select(n)
    }

    fun peqEdit(idx: Int, b: PBand) {
        if (!peqOn || idx !in peq.indices) return
        peq[idx] = b
        peqCommit()
    }

    fun peqAdd() {
        if (!peqOn || peq.size >= 16) return
        peq.add(PBand(0, 1000f, 0f, 1f))
        peqSel = peq.size - 1
        peqCommit()
    }

    fun peqRemove(idx: Int) {
        if (!peqOn || idx !in peq.indices) return
        peq.removeAt(idx)
        peqSel = peqSel.coerceIn(0, maxOf(peq.size - 1, 0))
        peqCommit()
    }

    fun importPeq(name: String, bands: List<PBand>, pre: Float?) {
        val n = uniqueName(name)
        peqPresets[n] = bands
        if (pre != null) preamp = pre.coerceIn(-12f, 12f)
        select(n)
    }

    fun peqResponse(freqs: DoubleArray): DoubleArray {
        val cs = peq.map { coefOf(it) }
        return DoubleArray(freqs.size) { i ->
            var s = 0.0
            for (c in cs) s += magDb(c, freqs[i])
            s
        }
    }

    private fun peqCommit() {
        peqPresets[preset] = peq.toList()
        syncGains()
        OutputPresets.keep()
        scheduleApply()
    }

    private fun scheduleApply() {
        if (pending) return
        pending = true
        ui.postDelayed({
            pending = false
            applyAll()
            save()
        }, 80)
    }

    private fun syncGains() {
        val r = peqResponse(DoubleArray(10) { CENTERS[it].toDouble() })
        for (i in 0 until 10) {
            gains[i] = (Math.round(r[i].toFloat() * 2f) / 2f).coerceIn(-12f, 12f)
        }
    }

    // ---------- effects ----------

    private fun wantPeq(): Boolean = peqOn && !peqUnsupported

    private fun edge(k: Int, n: Int): Float = 20f * 1100f.pow(k.toFloat() / n)

    private fun create(session: Int, n: Int): DynamicsProcessing? = try {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            true, n, true, 1, false, 0, true
        ).build()
        DynamicsProcessing(0, session, cfg)
    } catch (_: Throwable) {
        null
    }

    @Synchronized
    fun attach(session: Int) {
        if (!ready || effects.containsKey(session)) return
        build(session)
    }

    @Synchronized
    fun detach(session: Int) {
        try { effects.remove(session)?.release() } catch (_: Throwable) {}
        effBands.remove(session)
        lastG.remove(session)
    }

    private fun build(session: Int) {
        try { effects.remove(session)?.release() } catch (_: Throwable) {}
        effBands.remove(session)
        lastG.remove(session)
        if (wantPeq()) {
            for (n in intArrayOf(64, 48, 32, 16)) {
                if (n > peqN) continue
                val dp = create(session, n) ?: continue
                val g = denseApply(dp, n, null)
                if (g != null) {
                    effects[session] = dp
                    effBands[session] = n
                    lastG[session] = g
                    peqN = n
                    return
                }
                try { dp.release() } catch (_: Throwable) {}
            }
            peqUnsupported = true
        }
        val dp = create(session, 10) ?: return
        effects[session] = dp
        effBands[session] = 10
        configure(dp)
    }

    @Synchronized
    private fun applyAll() {
        for (s in effects.keys.toList()) {
            val dp = effects[s] ?: continue
            val have = effBands[s] ?: 10
            if (wantPeq() && have == 10) {
                build(s)
            } else if (!wantPeq() && have != 10) {
                build(s)
            } else if (have == 10) {
                configure(dp)
            } else {
                val g = denseApply(dp, have, lastG[s])
                if (g == null) build(s) else lastG[s] = g
            }
        }
    }

    private fun configure(dp: DynamicsProcessing) {
        try {
            for (i in 0 until 10) {
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, EDGES[i], eff(i)))
            }
        } catch (_: Throwable) {}
        configureRest(dp)
    }

    private fun denseApply(dp: DynamicsProcessing, n: Int, last: FloatArray?): FloatArray? {
        val out = FloatArray(n)
        val coefs = peq.map { coefOf(it) }
        try {
            for (i in 0 until n) {
                val lo = edge(i, n)
                val hi = edge(i + 1, n)
                val c = sqrt(lo * hi)
                var d = 0.0
                for (k in coefs) d += magDb(k, c.toDouble())
                d += (bass / 100f * 10f * interp(c, XB, YB) + treble / 100f * 10f * interp(c, XT, YT)).toDouble()
                val g = d.toFloat().coerceIn(-15f, 15f)
                out[i] = g
                if (last == null || last.size != n || abs(last[i] - g) > 0.02f) {
                    val cut = if (i == n - 1) 22000f else hi
                    dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cut, g))
                }
            }
        } catch (_: Throwable) {
            return null
        }
        configureRest(dp)
        return out
    }

    private fun configureRest(dp: DynamicsProcessing) {
        try {
            val l = preamp + (if (balance > 0f) -40f * balance else 0f)
            val r = preamp + (if (balance < 0f) 40f * balance else 0f)
            dp.setInputGainbyChannel(0, l)
            dp.setInputGainbyChannel(1, r)
        } catch (_: Throwable) {}
        try {
            val mb = DynamicsProcessing.MbcBand(compOn, 22000f, cAttack, cRelease, cRatio, cThresh, 6f, -90f, 1f, 0f, cGain)
            dp.setMbcBandAllChannelsTo(0, mb)
        } catch (_: Throwable) {}
        try {
            dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, limiterOn, 0, 1f, 60f, 10f, -2f, 0f))
        } catch (_: Throwable) {}
        try { dp.setEnabled(enabled) } catch (_: Throwable) {}
    }
}
