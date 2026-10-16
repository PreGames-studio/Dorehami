package sandbox

import ir.dorehami.games.hokm.ui.HokmChoreography
import ir.dorehami.games.hokm.ui.HokmMotion

/**
 * GX-04 helper — prints the timing table straight out of `HokmChoreography`, as Markdown.
 *
 * The roadmap's GX-04 deliverable is "a timing table for every event". A hand-written table drifts
 * the day somebody edits a beat, so this tool *generates* the table from the code: the document
 * (`games/hokm/design/gx04/README.md`) contains exactly what this prints, and the JVM tests pin the
 * numbers themselves.
 *
 *   bash tools/hokm/choreography.sh
 *
 * Kept out of the app build on purpose (same rule as `CardSheet.kt`): it is a review tool.
 */
fun main() {
    val out = StringBuilder()

    out.appendLine("### جدولِ زمان‌بندی (تولیدشده از `HokmChoreography` — دستی ویرایش نکنید)")
    out.appendLine()
    out.appendLine("| رخداد (id) | نوع | کل | فازها (ms) | نشانه‌ها (cue) با فاصله | reduced-motion |")
    out.appendLine("| --- | --- | --- | --- | --- | --- |")
    HokmChoreography.beats.forEach { beat ->
        val phases = beat.phases.joinToString(" · ") { "${it.titleFa} ${it.fromMs}–${it.toMs} (${it.curve})" }
        val cues = if (beat.cues.isEmpty()) "—" else beat.cues.joinToString(" · ") {
            "${it.id}=${it.kind}/${it.name}@${it.atMs}"
        }
        val reduced = HokmChoreography.beat(beat.event, reducedMotion = true)
        val reducedText = when (beat.event.kind) {
            HokmChoreography.BeatKind.READ -> "بدون تغییر (${reduced.totalMs} ms)"
            HokmChoreography.BeatKind.MOVEMENT -> "${reduced.totalMs} ms (سقف ${HokmMotion.REDUCED_MAX_MS})"
            HokmChoreography.BeatKind.DECORATION -> "حذف (۰)"
        }
        out.appendLine(
            "| `${beat.event.id}` — ${beat.event.titleFa} | ${beat.event.kind} | ${beat.totalMs} | $phases | $cues | $reducedText |",
        )
    }
    out.appendLine()
    out.appendLine(
        "سقفِ هر رخداد: **${HokmChoreography.MAX_EVENT_MS} ms** · عمرِ جرقه/کاغذرنگی: " +
            HokmChoreography.Fx.ALL_MS.joinToString(", ") + " ms",
    )
    print(out)
}
