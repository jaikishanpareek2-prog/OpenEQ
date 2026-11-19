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
    val gains = mutableStateListOf<Float>().apply { repeat(10) { add(0f) } }
    val userPresets = mutableStateMapOf<String, List<Float>>()

    private val effects = HashMap<Int, DynamicsProcessing>()
    private var ready = false
    private lateinit var ctx: Context

    @Synchronized
    fun init(c: Context) {
        if (ready) return
        ready = true
        ctx = c.applicationContext
        val p = ctx.getSharedPreferences("openeq", Context.MODE_PRIVATE)
        enabled = p.getBoolean("on", false)
        preamp = p.getFloat("pre", 0f)
        preset = p.getString("preset", "Flat") ?: "Flat"
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
            .apply()
    }

    fun toggle() { enabled = !enabled; applyAll(); save() }

    fun setPreamp(v: Float) { preamp = v; applyAll(); save() }

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
                true, 10, false, 0, false, 0, false
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
            dp.setInputGainAllChannelsTo(preamp)
            for (i in 0 until 10) {
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, EDGES[i], gains[i]))
            }
            dp.setEnabled(enabled)
        } catch (_: Throwable) {}
    }
}
