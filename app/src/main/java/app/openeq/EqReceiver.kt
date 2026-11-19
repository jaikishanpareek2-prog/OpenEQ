package app.openeq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class EqReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        EqEngine.init(c)
        val s = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (s < 0) return
        when (i.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> EqEngine.attach(s)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> EqEngine.detach(s)
        }
    }
}
