package pk.tailormaster.pro.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import pk.tailormaster.pro.R
import pk.tailormaster.pro.data.AppSettings
import java.io.File

/** A receipt described as simple lines. Rendered to a monochrome bitmap for Urdu-safe printing. */
sealed class RLine {
    enum class Align { START, CENTER, END }
    data class Text(val text: String, val scale: Float = 1f, val bold: Boolean = false, val align: Align = Align.START) : RLine()
    /** Label on the right, value on the left (RTL receipt). */
    data class Pair(val label: String, val value: String, val bold: Boolean = false, val scale: Float = 1f) : RLine()
    data class Divider(val dashed: Boolean = true) : RLine()
    data class Space(val px: Int = 8) : RLine()
    object Logo : RLine()
    /** Inverted (white on black) heading band. */
    data class Band(val text: String, val scale: Float = 1.1f) : RLine()
}

data class ReceiptDoc(val title: String, val lines: List<RLine>)

object ReceiptRenderer {

    fun typeface(context: Context, key: String): Typeface = try {
        when (key) {
            "nastaliq" -> ResourcesCompat.getFont(context, R.font.nastaliq)
            "naskh" -> ResourcesCompat.getFont(context, R.font.naskh)
            else -> null
        } ?: Typeface.DEFAULT
    } catch (e: Exception) {
        Typeface.DEFAULT
    }

    private fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment, extraSpacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(true)
            .setLineSpacing(extraSpacing, 1f)
            .build()

    private fun logoBitmap(context: Context, s: AppSettings, maxW: Int): Bitmap? {
        if (!s.showLogo || s.logoFile.isBlank()) return null
        val f = File(context.filesDir, s.logoFile)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        val maxH = 140
        val scale = minOf(maxW * 0.6f / b.width, maxH.toFloat() / b.height, 1f)
        return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
    }

    /**
     * Renders the receipt at the printer's printable width (in dots). The result is pure black/white,
     * so it prints identically on any ESC/POS printer that supports raster images.
     */
    fun render(context: Context, doc: ReceiptDoc, s: AppSettings): Bitmap {
        val width = s.printableDots.coerceIn(200, 1000)
        val margin = s.marginDots.coerceIn(0, 60)
        val inner = width - 2 * margin
        val tf = typeface(context, s.receiptFont)
        val tfBold = Typeface.create(tf, Typeface.BOLD)
        val base = s.receiptFontSize.coerceIn(14, 48).toFloat()
        val spacing = if (s.receiptFont == "nastaliq") base * 0.35f else base * 0.12f

        fun paint(scale: Float, bold: Boolean, color: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = base * scale
            typeface = if (bold) tfBold else tf
            this.color = color
            isFakeBoldText = bold && tf == Typeface.DEFAULT
        }

        // 1) Measure
        data class Block(val height: Int, val draw: (Canvas, Int) -> Unit)
        val blocks = mutableListOf<Block>()
        val logo = logoBitmap(context, s, inner)
        for (line in doc.lines) {
            when (line) {
                is RLine.Text -> {
                    if (line.text.isBlank()) continue
                    val al = when (line.align) {
                        RLine.Align.START -> Layout.Alignment.ALIGN_NORMAL
                        RLine.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                        RLine.Align.END -> Layout.Alignment.ALIGN_OPPOSITE
                    }
                    val l = layout(line.text, paint(line.scale, line.bold), inner, al, spacing)
                    blocks += Block(l.height) { c, y -> c.save(); c.translate(margin.toFloat(), y.toFloat()); l.draw(c); c.restore() }
                }
                is RLine.Pair -> {
                    val p = paint(line.scale, line.bold)
                    val valueW = (inner * 0.42f).toInt()
                    val labelW = inner - valueW - 6
                    val lab = layout(line.label, p, labelW, Layout.Alignment.ALIGN_NORMAL, spacing)
                    val v = layout(line.value, p, valueW, Layout.Alignment.ALIGN_OPPOSITE, spacing)
                    val h = maxOf(lab.height, v.height)
                    blocks += Block(h) { c, y ->
                        c.save(); c.translate((margin + valueW + 6).toFloat(), y.toFloat()); lab.draw(c); c.restore()
                        c.save(); c.translate(margin.toFloat(), y.toFloat()); v.draw(c); c.restore()
                    }
                }
                is RLine.Divider -> blocks += Block(14) { c, y ->
                    val dp = Paint().apply {
                        color = Color.BLACK; strokeWidth = 2f; style = Paint.Style.STROKE
                        if (line.dashed) pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                    }
                    c.drawLine(margin.toFloat(), y + 7f, (width - margin).toFloat(), y + 7f, dp)
                }
                is RLine.Space -> blocks += Block(line.px) { _, _ -> }
                is RLine.Logo -> if (logo != null) blocks += Block(logo.height + 6) { c, y ->
                    c.drawBitmap(logo, ((width - logo.width) / 2).toFloat(), y.toFloat(), null)
                }
                is RLine.Band -> {
                    val l = layout(line.text, paint(line.scale, true, Color.WHITE), inner - 8, Layout.Alignment.ALIGN_CENTER, spacing)
                    blocks += Block(l.height + 8) { c, y ->
                        c.drawRect(Rect(margin, y, width - margin, y + l.height + 8), Paint().apply { color = Color.BLACK })
                        c.save(); c.translate((margin + 4).toFloat(), (y + 4).toFloat()); l.draw(c); c.restore()
                    }
                }
            }
        }

        // 2) Draw
        val total = blocks.sumOf { it.height + 4 } + 16
        val bmp = Bitmap.createBitmap(width, total.coerceAtLeast(40), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = 8
        for (b in blocks) { b.draw(canvas, y); y += b.height + 4 }
        return toMonochrome(bmp)
    }

    /** Converts to pure black & white (thermal printers have no grey). */
    fun toMonochrome(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = Color.alpha(c)
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            val effective = 255 - ((255 - lum) * a / 255)
            px[i] = if (effective < 150) Color.BLACK else Color.WHITE
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
