package pk.tailormaster.pro.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * App language. Urdu is the original text everywhere in the code and the data; when English is
 * chosen every text is translated just before it is shown or printed, using i18n/en.json
 * (the same dictionary the web version uses).
 *
 * Lookup order: exact text → text with {0}-style blanks (e.g. "{0} دن" → "{0} days") →
 * pieces split at separators (•, —, :, new line) → longest known phrases word by word.
 * Anything not in the dictionary (customer names, notes) is left as written.
 */
object I18n {
    const val URDU = "ur"
    const val ENGLISH = "en"

    /** Compose state, so every Text on screen redraws when the language changes. */
    var lang by mutableStateOf(URDU)
        private set
    val en: Boolean get() = lang == ENGLISH

    private val dict = HashMap<String, String>()
    private class Tpl(val regex: Regex, val order: List<Int>, val english: String, val literalLen: Int)
    private var templates: List<Tpl> = emptyList()
    private var maxPhraseWords = 1
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 4000
    }

    private val EDGE_UR = Regex("^[\\s:•—،\\-]+|[\\s:•—،\\-]+$")
    private val EDGE_EN = Regex("^[\\s:•—,\\-]+|[\\s:•—,\\-]+$")
    private val urduChar = Regex("[\\u0600-\\u06FF]")
    private val placeholder = Regex("\\{(\\d+)\\}")

    fun load(context: Context) {
        if (dict.isNotEmpty()) return
        try {
            val text = context.assets.open("en.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            loadJson(text)
        } catch (e: Exception) {
            android.util.Log.e("I18n", "dictionary not loaded", e)
        }
    }

    /** Also used by unit tests. */
    fun loadJson(text: String) {
        val o = Json.parseToJsonElement(text).jsonObject
        val tpls = mutableListOf<Tpl>()
        for ((k, el) in o) {
            val v = el.jsonPrimitive.content
            if (placeholder.containsMatchIn(k)) {
                val order = mutableListOf<Int>()
                val sb = StringBuilder("^")
                var last = 0
                for (m in placeholder.findAll(k)) {
                    sb.append(Regex.escape(k.substring(last, m.range.first)))
                    sb.append("(.*?)")
                    order += m.groupValues[1].toInt()
                    last = m.range.last + 1
                }
                sb.append(Regex.escape(k.substring(last))).append("$")
                val literal = placeholder.replace(k, "")
                tpls += Tpl(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), order, v, literal.trim().length)
            } else {
                dict[k] = v
                val t = k.trim()
                if (t != k && t.isNotEmpty() && !dict.containsKey(t)) dict[t] = v.trim()
                // "خرابی: " is also known as "خرابی", so pieces split at separators are found
                val bare = k.replace(EDGE_UR, "")
                if (bare.isNotEmpty() && bare != t && !dict.containsKey(bare)) dict[bare] = v.replace(EDGE_EN, "")
                maxPhraseWords = maxOf(maxPhraseWords, t.split(' ').size)
            }
        }
        templates = tpls.sortedByDescending { it.literalLen }
        synchronized(cache) { cache.clear() }
    }

    fun setLanguage(code: String) {
        lang = if (code == ENGLISH) ENGLISH else URDU
    }

    /** Translates [s] for display when English is on; returns it unchanged otherwise. */
    fun tr(s: String): String {
        if (!en || s.isEmpty() || !urduChar.containsMatchIn(s)) return s
        synchronized(cache) { cache[s]?.let { return it } }
        val r = translate(s, 0)
        synchronized(cache) { cache[s] = r }
        return r
    }

    private fun translate(s: String, depth: Int): String {
        if (!urduChar.containsMatchIn(s)) return s
        dict[s]?.let { return it }
        // keep surrounding spaces / new lines as they are
        val lead = s.takeWhile { it.isWhitespace() }
        val trail = s.takeLastWhile { it.isWhitespace() }
        if (lead.length + trail.length >= s.length) return s
        val core = s.substring(lead.length, s.length - trail.length)
        if (core != s) dict[core]?.let { return lead + it + trail }

        if (depth < 4) {
            for (t in templates) {
                val m = t.regex.find(core) ?: continue
                // a blank must not swallow a separator, e.g. "بکنگ {0}" must not match "بکنگ محفوظ ہو گئی — B-1"
                if (m.groupValues.drop(1).any { SEPARATORS.containsMatchIn(it) }) continue
                var out = t.english
                m.groupValues.drop(1).forEachIndexed { i, g ->
                    out = out.replace("{${t.order[i]}}", translate(g, depth + 1))
                }
                return lead + out + trail
            }
            // split at separators and translate each piece
            val parts = SEPARATORS.split(core)
            if (parts.size > 1) {
                val sb = StringBuilder()
                var pos = 0
                for (m in SEPARATORS.findAll(core)) {
                    sb.append(translate(core.substring(pos, m.range.first), depth + 1))
                    sb.append(SEP_EN[m.value] ?: m.value)
                    pos = m.range.last + 1
                }
                sb.append(translate(core.substring(pos), depth + 1))
                return lead + sb.toString() + trail
            }
        }
        // "Ali (محمد اسلم (نمونہ))": translate what is inside brackets on its own
        val plain = phrases(core)
        if (depth < 4 && core.contains('(') && urduChar.containsMatchIn(plain)) {
            val inner = BRACKETS.replace(plain) { m -> "(" + translate(m.groupValues[1], depth + 1) + ")" }
            if (urduCount(inner) < urduCount(plain)) return lead + inner + trail
        }
        return lead + plain + trail
    }

    private fun urduCount(s: String) = urduChar.findAll(s).count()

    /** Last resort: replace the longest known phrases, word by word. */
    private fun phrases(s: String): String {
        val words = s.split(' ')
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var done = false
            if (urduChar.containsMatchIn(words[i])) {
                for (n in minOf(maxPhraseWords, words.size - i) downTo 1) {
                    val phrase = words.subList(i, i + n).joinToString(" ")
                    val hit = dict[phrase] ?: dict[phrase.trimEnd('۔', '،', ':', '?', '؟')]?.let { it + phrase.takeLastWhile { c -> c in "۔،:?؟" }.map { c -> PUNCT_EN[c] ?: c }.joinToString("") }
                    if (hit != null) { out += hit; i += n; done = true; break }
                }
            }
            if (!done) { out += words[i]; i++ }
        }
        return out.joinToString(" ")
    }

    private val BRACKETS = Regex("\\(((?:[^()]|\\([^()]*\\))*)\\)")
    private val SEPARATORS = Regex(" • |\\n| — | ← | → |، |: | \\| ")
    private val SEP_EN = mapOf("، " to ", ", " ← " to " → ")
    private val PUNCT_EN = mapOf('۔' to '.', '،' to ',', '؟' to '?')
}

/** Short form used in the UI code. */
fun tr(s: String): String = I18n.tr(s)
