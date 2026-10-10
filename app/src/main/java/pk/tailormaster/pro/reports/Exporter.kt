package pk.tailormaster.pro.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import pk.tailormaster.pro.data.AppSettings
import pk.tailormaster.pro.print.ReceiptRenderer
import pk.tailormaster.pro.ui.Fmt
import java.io.OutputStream

object Exporter {

    /** UTF-8 CSV with BOM so that Excel shows Urdu correctly. */
    fun writeCsv(t: ReportTable, out: OutputStream) {
        fun esc(s: String): String {
            val v = s.replace("\"", "\"\"")
            return if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"$v\"" else v
        }
        val sb = StringBuilder()
        sb.append('﻿')
        sb.append(esc(t.title)).append('\n')
        if (t.subtitle.isNotBlank()) sb.append(esc(t.subtitle.replace('\n', ' '))).append('\n')
        sb.append(t.headers.joinToString(",") { esc(it) }).append('\n')
        t.csvRows.forEach { r -> sb.append(r.joinToString(",") { esc(it) }).append('\n') }
        t.csvTotals?.let { sb.append(it.joinToString(",") { x -> esc(x) }).append('\n') }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun layout(text: String, p: TextPaint, w: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, w.coerceAtLeast(10))
            .setAlignment(align).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(true).build()

    /** Right-to-left PDF table. Urdu is shaped by Android's text engine with a bundled Urdu font. */
    fun writePdf(context: Context, s: AppSettings, t: ReportTable, out: OutputStream) {
        val landscape = t.headers.size > 6
        val pw = if (landscape) 842 else 595
        val ph = if (landscape) 595 else 842
        val margin = 28
        val tableW = pw - 2 * margin
        val tf = ReceiptRenderer.typeface(context, "naskh")
        val cellP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = 8.5f; color = Color.BLACK }
        val headP = TextPaint(cellP).apply { isFakeBoldText = true; textSize = 9f }
        val titleP = TextPaint(cellP).apply { textSize = 15f; isFakeBoldText = true }
        val subP = TextPaint(cellP).apply { textSize = 9.5f; color = Color.DKGRAY }
        val line = Paint().apply { color = Color.GRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE }
        val fill = Paint().apply { color = Color.rgb(230, 238, 240) }

        val n = t.headers.size.coerceAtLeast(1)
        // Wider column for long text columns (heuristic: longest average content gets more space)
        val weights = (0 until n).map { c ->
            val avg = (t.rows.take(50).map { it.getOrNull(c)?.length ?: 0 } + t.headers[c].length).average()
            avg.coerceIn(4.0, 40.0)
        }
        val wSum = weights.sum()
        val colW = weights.map { (it / wSum * tableW).toInt() }
        // x positions from the RIGHT edge (column 0 is right-most)
        val colRight = mutableListOf<Int>()
        var x = pw - margin
        colW.forEach { colRight += x; x -= it }

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0

        fun drawRow(cells: List<String>, p: TextPaint, shade: Boolean): Int {
            val lays = (0 until n).map { c -> layout(cells.getOrElse(c) { "" }, p, colW[c] - 6, Layout.Alignment.ALIGN_NORMAL) }
            val h = (lays.maxOf { it.height } + 6)
            val cv = page!!.canvas
            if (shade) cv.drawRect((pw - margin - tableW).toFloat(), y.toFloat(), (pw - margin).toFloat(), (y + h).toFloat(), fill)
            for (c in 0 until n) {
                cv.save(); cv.translate((colRight[c] - colW[c] + 3).toFloat(), (y + 3).toFloat()); lays[c].draw(cv); cv.restore()
                cv.drawRect((colRight[c] - colW[c]).toFloat(), y.toFloat(), colRight[c].toFloat(), (y + h).toFloat(), line)
            }
            return h
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
            val cv = page!!.canvas
            y = margin
            if (pageNo == 1) {
                val head = layout(s.shopName + " — " + t.title, titleP, tableW, Layout.Alignment.ALIGN_NORMAL)
                cv.save(); cv.translate(margin.toFloat(), y.toFloat()); head.draw(cv); cv.restore(); y += head.height + 2
                val sub = (t.subtitle + "\nتیار کردہ: " + Fmt.dateTime(System.currentTimeMillis())).trim()
                val sl = layout(sub, subP, tableW, Layout.Alignment.ALIGN_NORMAL)
                cv.save(); cv.translate(margin.toFloat(), y.toFloat()); sl.draw(cv); cv.restore(); y += sl.height + 8
            }
            val foot = layout("صفحہ " + Fmt.num(pageNo), subP, tableW, Layout.Alignment.ALIGN_CENTER)
            cv.save(); cv.translate(margin.toFloat(), (ph - margin + 6).toFloat()); foot.draw(cv); cv.restore()
            y += drawRow(t.headers, headP, true)
        }

        newPage()
        if (t.rows.isEmpty()) {
            y += drawRow(listOf("کوئی ریکارڈ نہیں") + List(n - 1) { "" }, cellP, false)
        }
        for (r in t.rows) {
            val est = 30
            if (y + est > ph - margin - 10) newPage()
            y += drawRow(r, cellP, false)
        }
        t.totals?.let {
            if (y + 30 > ph - margin - 10) newPage()
            y += drawRow(it, headP, true)
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
        out.flush()
    }
}
