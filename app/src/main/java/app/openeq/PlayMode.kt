package app.openeq

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

object PlayMode {
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(0)

    fun pickNext(): Int {
        val n = Player.queue.size
        if (shuffle && n > 1) {
            var r = Player.index
            while (r == Player.index) r = Random.nextInt(n)
            return r
        }
        return (Player.index + 1) % n
    }

    fun onDone() {
        if (repeat == 2) Player.play(Player.index)
        else if (!shuffle && repeat == 0 && Player.index >= Player.queue.size - 1) Player.pause()
        else Player.next()
    }
}
