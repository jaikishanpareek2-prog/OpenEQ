package app.openeq

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object EqEngine {
    val LABELS = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    private val EDGES = floatArrayOf(44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 22000f)
    private val WB = floatArrayOf(1f, 1f, 0.8f, 0.4f, 0.1f, 0f, 0f, 0f, 0f, 0f)
    private val WT = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.1f, 0.4f, 0.8f, 1f, 1f)

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

    private val effects = HashMap<Int, DynamicsProcessing>()
    private var ready = false
    private lateinit var ctx: Context

    fun eff(i: Int): Float =
        (gains[i] + bass / 100f * 10f * WB[i] + treble / 100f * 10f * WT[i]).coerceIn(-15f, 15f)

    @Synchronized
    fun init(c: Context) {
        if (ready) return
        ready = true
        ctx = c.applicationContext
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
    }

    private fun save() {
        ctx.getSharedPreferences("openeq", Context.MODE_PRIVATE).edit()
            .putBoolean("on", enabled)
            .putFloat("pre", preamp)
            .putString("preset", preset)
            .putString("gains", gains.joinToString(","))
            .putString("user", userPresets.entries.joinToString(";") { it.key + "|" + it.value.joinToString(",") })
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
        if (BUILTIN.containsKey(preset)) preset = "My set"
        userPresets[preset] = gains.toList()
        applyAll(); save()
    }

    fun select(name: String) {
        val g = BUILTIN[name] ?: userPresets[name] ?: return
        preset = name
        g.forEachIndexed { i, v -> gains[i] = v }
        applyAll(); save()
    }

    fun newPreset() {
        var n = 1
        while (userPresets.containsKey("My set $n")) n++
        preset = "My set $n"
        userPresets[preset] = gains.toList()
        save()
    }

    fun names(): List<String> = BUILTIN.keys.toList() + userPresets.keys.sorted()

    @Synchronized
    fun attach(session: Int) {
        if (!ready || effects.containsKey(session)) return
        try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                true, 10, true, 1, false, 0, true
            ).build()
            val dp = DynamicsProcessing(0, session, cfg)
            effects[session] = dp
            configure(dp)
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun detach(session: Int) {
        try { effects.remove(session)?.release() } catch (_: Throwable) {}
    }

    @Synchronized
    private fun applyAll() { effects.values.forEach { configure(it) } }

    private fun configure(dp: DynamicsProcessing) {
        try {
            val l = preamp + (if (balance > 0f) -40f * balance else 0f)
            val r = preamp + (if (balance < 0f) 40f * balance else 0f)
            dp.setInputGainbyChannel(0, l)
            dp.setInputGainbyChannel(1, r)
            for (i in 0 until 10) {
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, EDGES[i], eff(i)))
            }
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
