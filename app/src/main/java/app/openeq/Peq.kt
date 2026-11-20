package app.openeq

import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

private val Q_PANEL = Color(0xFF00222A)
private val Q_GREEN = Color(0xFF3CFF14)
private val Q_TXT = Color(0xFF8FEBFF)
private val Q_SUB = Color(0xFF6FA8B5)
private val Q_TRACK = Color(0xFF3A4350)

private fun xOf(f: Float, w: Float): Float = ln(f / 20f) / ln(1000f) * w
private fun fOf(x: Float, w: Float): Float =
    Math.round(20f * 1000f.pow((x / w).coerceIn(0f, 1f))).toFloat().coerceIn(20f, 20000f)
private fun yOf(g: Float, h: Float): Float = h / 2f - g / 15f * (h / 2f - 10f)
private fun gOf(y: Float, h: Float): Float =
    Math.round(((h / 2f - y) / (h / 2f - 10f) * 15f).coerceIn(-15f, 15f) * 10f) / 10f

private fun editSel(fn: (PBand) -> PBand) {
    val i = EqEngine.peqSel
    val cur = EqEngine.peq.getOrNull(i) ?: return
    EqEngine.peqEdit(i, fn(cur))
}

object AutoEq {
    class Result(val bands: List<PBand>, val preamp: Float?, val skipped: Int, val capped: Boolean)

    private const val NUM = "([+-]?\\d+(?:[.,]\\d+)?)"
    private val FILTER = Regex(
        "(ON|OFF)\\s+([A-Za-z]+)\\s+Fc\\s+" + NUM + "\\s*(?:Hz)?\\s+Gain\\s+" + NUM +
            "\\s*(?:dB)?\\s+(?:Q\\s+" + NUM + "|BW\\s+Oct\\s+" + NUM + ")",
        RegexOption.IGNORE_CASE
    )
    private val PREAMP = Regex("Preamp\\s*:\\s*" + NUM, RegexOption.IGNORE_CASE)
    private val ON_WORD = Regex("\\bON\\b", RegexOption.IGNORE_CASE)

    fun parse(text: String): Result {
        val out = ArrayList<PBand>()
        var pre: Float? = null
        var skipped = 0
        var capped = false
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (pre == null) {
                val pm = PREAMP.find(l)
                if (pm != null) pre = pm.groupValues[1].replace(',', '.').toFloatOrNull()
            }
            if (!l.contains("Filter", true)) continue
            val m = FILTER.find(l)
            if (m == null) {
                if (ON_WORD.containsMatchIn(l)) skipped++
                continue
            }
            val v = m.groupValues
            if (v[1].equals("OFF", true)) continue
            val type = when (v[2].uppercase()) {
                "PK", "PEQ" -> 0
                "LS", "LSC" -> 1
                "HS", "HSC" -> 2
                else -> -1
            }
            if (type < 0) { skipped++; continue }
            val fc = v[3].replace(',', '.').toFloatOrNull()
            val g = v[4].replace(',', '.').toFloatOrNull()
            var q = v[5].replace(',', '.').toFloatOrNull()
            if (q == null) {
                val bw = v[6].replace(',', '.').toFloatOrNull()
                if (bw != null && bw > 0f) {
                    val p = 2.0.pow(bw.toDouble())
                    q = (sqrt(p) / (p - 1.0)).toFloat()
                }
            }
            if (fc == null || g == null || q == null) { skipped++; continue }
            if (out.size >= 16) { capped = true; continue }
            out.add(PBand(type, fc.coerceIn(20f, 20000f), g.coerceIn(-15f, 15f), q.coerceIn(0.3f, 10f)))
        }
        return Result(out, pre, skipped, capped)
    }
}

private fun doImport(ctx: Context, text: String, fileName: String?) {
    val r = AutoEq.parse(text)
    if (r.bands.isEmpty()) {
        Toast.makeText(ctx, ctx.getString(R.string.b6_imp_none), Toast.LENGTH_LONG).show()
        return
    }
    val base = (fileName ?: "AutoEq").removeSuffix(".txt").replace(" ParametricEQ", "")
    EqEngine.importPeq(base, r.bands, r.preamp)
    val sb = StringBuilder(ctx.getString(R.string.b6_imp_ok, r.bands.size, EqEngine.preamp))
    if (r.skipped > 0) sb.append("\n").append(ctx.getString(R.string.b6_imp_skip, r.skipped))
    if (r.capped) sb.append("\n").append(ctx.getString(R.string.b6_imp_cap))
    Toast.makeText(ctx, sb.toString(), Toast.LENGTH_LONG).show()
}

@Composable
private fun PeqCurve() {
    Canvas(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)).background(Q_PANEL)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    var hit = -1
                    var best = 40.dp.toPx()
                    EqEngine.peq.forEachIndexed { i, b ->
                        val dx = xOf(b.f, w) - down.position.x
                        val dy = yOf(b.g, h) - down.position.y
                        val d = sqrt(dx * dx + dy * dy)
                        if (d < best) { best = d; hit = i }
                    }
                    if (hit < 0) return@awaitEachGesture
                    down.consume()
                    EqEngine.peqSel = hit
                    do {
                        val ev = awaitPointerEvent()
                        val p = ev.changes.first()
                        if (p.pressed) {
                            val b = EqEngine.peq.getOrNull(hit)
                            if (b != null) EqEngine.peqEdit(hit, b.copy(f = fOf(p.position.x, w), g = gOf(p.position.y, h)))
                            p.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        val w = size.width
        val h = size.height
        for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
            drawLine(Q_TRACK, Offset(0f, yOf(db, h)), Offset(w, yOf(db, h)), if (db == 0f) 2.dp.toPx() else 1.dp.toPx())
        }
        for (f in listOf(100f, 1000f, 10000f)) {
            drawLine(Q_TRACK, Offset(xOf(f, w), 0f), Offset(xOf(f, w), h), 1.dp.toPx())
        }
        val n = 96
        val fr = DoubleArray(n) { 20.0 * 1000.0.pow(it / (n - 1.0)) }
        val rs = EqEngine.peqResponse(fr)
        val path = Path()
        for (i in 0 until n) {
            val x = w * i / (n - 1f)
            val y = yOf(rs[i].toFloat().coerceIn(-15f, 15f), h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Q_GREEN, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        EqEngine.peq.forEachIndexed { i, b ->
            val c = Offset(xOf(b.f, w), yOf(b.g, h))
            if (i == EqEngine.peqSel) drawCircle(Q_GREEN, 10.dp.toPx(), c)
            else drawCircle(Q_TXT, 6.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
fun PeqTab(modifier: Modifier) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                var name: String? = null
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (ix >= 0) name = c.getString(ix)
                    }
                }
                doImport(ctx, text.take(200000), name)
            } catch (_: Throwable) {
                Toast.makeText(ctx, ctx.getString(R.string.b6_imp_none), Toast.LENGTH_LONG).show()
            }
        }
    }
    val hz = stringResource(R.string.b6_hz)
    val khz = stringResource(R.string.b6_khz)
    val db = stringResource(R.string.b6_db)

    Column(modifier.verticalScroll(rememberScrollState())) {
        if (!EqEngine.peqOn) {
            Text(stringResource(R.string.b6_peq_intro), color = Q_SUB, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            Pill(stringResource(R.string.b6_start), true, Modifier.fillMaxWidth()) { EqEngine.peqStart() }
        } else {
            PeqCurve()
            Text(stringResource(R.string.b6_hint_drag), color = Q_SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EqEngine.peq.forEachIndexed { i, _ ->
                    Pill("${i + 1}", EqEngine.peqSel == i, Modifier.width(52.dp)) { EqEngine.peqSel = i }
                }
            }
            val b = EqEngine.peq.getOrNull(EqEngine.peqSel)
            if (b != null) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.b6_type_peak), b.type == 0, Modifier.weight(1f)) { editSel { it.copy(type = 0) } }
                    Pill(stringResource(R.string.b6_type_low), b.type == 1, Modifier.weight(1f)) { editSel { it.copy(type = 1) } }
                    Pill(stringResource(R.string.b6_type_high), b.type == 2, Modifier.weight(1f)) { editSel { it.copy(type = 2) } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val ft = if (b.f >= 1000f) "%.2f %s".format(b.f / 1000f, khz) else "%d %s".format(b.f.toInt(), hz)
                    Knob(stringResource(R.string.b6_freq), ft, (ln(b.f / 20f) / ln(1000f)).coerceIn(0f, 1f)) { t ->
                        editSel { it.copy(f = Math.round(20f * 1000f.pow(t)).toFloat()) }
                    }
                    PKnob(stringResource(R.string.b6_gain), b.g, -15f, 15f, " $db", 1) { v ->
                        editSel { it.copy(g = Math.round(v * 10f) / 10f) }
                    }
                    Knob(stringResource(R.string.b6_q), "%.2f".format(b.q), (ln(b.q / 0.3f) / ln(10f / 0.3f)).coerceIn(0f, 1f)) { t ->
                        editSel { it.copy(q = Math.round(0.3f * (10f / 0.3f).pow(t) * 100f) / 100f) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.b6_add), false, Modifier.weight(1f)) { EqEngine.peqAdd() }
                Pill(stringResource(R.string.b6_remove), false, Modifier.weight(1f)) { EqEngine.peqRemove(EqEngine.peqSel) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.b6_imp_file), false, Modifier.weight(1f)) { picker.launch(arrayOf("*/*")) }
            Pill(stringResource(R.string.b6_imp_paste), false, Modifier.weight(1f)) {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                val t = try { cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: "" } catch (_: Throwable) { "" }
                doImport(ctx, t.take(200000), null)
            }
        }
        Text(stringResource(R.string.b6_imp_hint), color = Q_SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        if (EqEngine.peqUnsupported) {
            Text(stringResource(R.string.b6_unsupported), color = Q_SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
