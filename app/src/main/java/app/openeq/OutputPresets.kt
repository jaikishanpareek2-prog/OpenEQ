package app.openeq

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object OutputPresets {
    var enabled by mutableStateOf(false)
    var current by mutableStateOf("speaker")

    private val map = HashMap<String, String>()
    private var started = false
    private lateinit var ctx: Context
    private lateinit var am: AudioManager
    private val main = Handler(Looper.getMainLooper())

    fun start(c: Context) {
        if (started) return
        started = true
        ctx = c.applicationContext
        am = ctx.getSystemService(AudioManager::class.java)
        val p = ctx.getSharedPreferences("openeq_out", Context.MODE_PRIVATE)
        enabled = p.getBoolean("on", false)
        try {
            p.getString("map", "")?.split(";")?.filter { it.contains("=") }?.forEach {
                val kv = it.split("=")
                map[kv[0]] = kv[1]
            }
        } catch (_: Exception) {}
        try {
            am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { refresh(false) }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { refresh(false) }
            }, main)
        } catch (_: Throwable) {}
        main.post { refresh(true) }
    }

    private fun detect(): String {
        var bt = false
        var wired = false
        try {
            for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26, 27, 30 -> bt = true
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> wired = true
                }
            }
        } catch (_: Throwable) {}
        return if (bt) "bluetooth" else if (wired) "wired" else "speaker"
    }

    private fun refresh(force: Boolean) {
        val now = detect()
        val changed = now != current
        current = now
        if (enabled && (changed || force)) apply()
    }

    private fun apply() {
        val name = map[current]
        if (name != null && EqEngine.names().contains(name)) {
            EqEngine.select(name)
            return
        }
        val shared = map.any { it.key != current && it.value == EqEngine.preset }
        if (shared && !EqEngine.BUILTIN.containsKey(EqEngine.preset)) {
            val n = editName()
            val m = EqEngine.cloneCurrentAs(n)
            EqEngine.select(m)
        } else {
            keep()
        }
    }

    fun editName(): String = if (started && enabled) "My set " + current else "My set"

    fun keep() {
        if (!started || !enabled) return
        if (map[current] == EqEngine.preset) return
        map[current] = EqEngine.preset
        ctx.getSharedPreferences("openeq_out", Context.MODE_PRIVATE).edit()
            .putString("map", map.entries.joinToString(";") { it.key + "=" + it.value })
            .apply()
    }

    fun toggle() {
        enabled = !enabled
        ctx.getSharedPreferences("openeq_out", Context.MODE_PRIVATE).edit().putBoolean("on", enabled).apply()
        if (enabled) keep()
    }
}

@Composable
fun OutputPill() {
    val c = LocalContext.current
    val name = when (OutputPresets.current) {
        "bluetooth" -> c.getString(R.string.b5_out_bluetooth)
        "wired" -> c.getString(R.string.b5_out_wired)
        else -> c.getString(R.string.b5_out_speaker)
    }
    InkPill(name, OutputPresets.enabled, Modifier.width(96.dp)) {
        OutputPresets.toggle()
        Toast.makeText(
            c,
            c.getString(if (OutputPresets.enabled) R.string.b5_per_on else R.string.b5_per_off),
            Toast.LENGTH_SHORT
        ).show()
    }
}
