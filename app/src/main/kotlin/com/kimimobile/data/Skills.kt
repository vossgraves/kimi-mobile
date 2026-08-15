package com.kimimobile.data

import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Tool library for agent mode. The kimi.com web API has no native function
 * calling (verified: forced `tools` returns code -2001), so the agent uses a
 * prompt protocol: the model emits `TOOL_CALL:<id>|<args>` lines, the app
 * executes them and injects `TOOL_RESULT:<id>|...` back into the history.
 */
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val run: suspend (args: String) -> String,
)

object SkillEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val memoryNotes = mutableListOf<String>()

    val all: List<Skill> = listOf(
        Skill(
            id = "web_search",
            name = "Web search",
            description = "Search the web. Args: the search query",
            category = "Research",
            run = ::webSearch,
        ),
        Skill(
            id = "fetch_url",
            name = "Fetch URL",
            description = "Fetch a web page and return its text. Args: full URL",
            category = "Research",
            run = ::fetchUrl,
        ),
        Skill(
            id = "wikipedia",
            name = "Wikipedia",
            description = "Read a Wikipedia article summary. Args: article title",
            category = "Research",
            run = ::wikipedia,
        ),
        Skill(
            id = "calculator",
            name = "Calculator",
            description = "Evaluate a math expression. Args: e.g. (12+34)*5^2",
            category = "Utility",
            run = ::calculate,
        ),
        Skill(
            id = "datetime",
            name = "Date & time",
            description = "Current date and time. Args: ignored",
            category = "Utility",
        ) { "${LocalDate.now()} ${LocalTime.now().withNano(0)}" },
        Skill(
            id = "memory",
            name = "Memory notes",
            description = "Session memory. Args: \"add <note>\" | \"list\" | \"clear\"",
            category = "Memory",
            run = ::memory,
        ),
    )

    fun byId(id: String): Skill? = all.firstOrNull { it.id == id }

    // ---- Executors ---------------------------------------------------------

    private suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        val url = "https://html.duckduckgo.com/html/?q=" +
            java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val html = fetch(url) ?: return@withContext "Error: search request failed"
        val titleRe = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>")
        val snippetRe = Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>")
        val titles = titleRe.findAll(html).map { m -> stripTags(m.groupValues[2]) }.toList()
        val snippets = snippetRe.findAll(html).map { m -> stripTags(m.groupValues[1]) }.toList()
        if (titles.isEmpty()) return@withContext "No results found."
        titles.take(5).mapIndexed { i, t ->
            val s = snippets.getOrNull(i)?.take(200) ?: ""
            "${i + 1}. $t\n   ${s.ifBlank { "" }}"
        }.joinToString("\n")
    }

    private suspend fun fetchUrl(url: String): String = withContext(Dispatchers.IO) {
        val target = url.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        val html = fetch(target) ?: return@withContext "Error: fetch failed"
        stripTags(html)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(6000)
            .ifBlank { "Page is empty or requires JavaScript." }
    }

    private suspend fun wikipedia(title: String): String = withContext(Dispatchers.IO) {
        val searchUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=" +
            java.net.URLEncoder.encode(title.trim(), "UTF-8") +
            "&limit=3&format=json"
        val searchBody = fetch(searchUrl)
        val bestTitle = runCatching {
            json.parseToJsonElement(searchBody.orEmpty())
                .jsonArray[1].jsonArray.firstOrNull()?.jsonPrimitive?.content
        }.getOrNull()
        val resolved = bestTitle ?: title.trim()
        val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
            java.net.URLEncoder.encode(resolved, "UTF-8")
        val body = fetch(summaryUrl)
        val extract = runCatching {
            json.parseToJsonElement(body.orEmpty()).jsonObject["extract"]?.jsonPrimitive?.content
        }.getOrNull()
        if (extract.isNullOrBlank()) "No article found for \"$title\"."
        else "\"$resolved\":\n${extract.take(3000)}"
    }

    private suspend fun calculate(expr: String): String = runCatching {
        val value = Calculator.eval(expr)
        if (value.isNaN() || value.isInfinite()) "Error: undefined result"
        else "= ${if (value == value.toLong().toDouble()) value.toLong().toString() else value}"
    }.getOrElse { "Error: ${it.message ?: "invalid expression"}" }

    private suspend fun memory(args: String): String {
        val a = args.trim()
        return when {
            a.startsWith("add ") -> { memoryNotes.add(a.removePrefix("add ").trim()); "Saved (${memoryNotes.size} notes)." }
            a == "list" -> if (memoryNotes.isEmpty()) "No notes yet." else memoryNotes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n")
            a == "clear" -> { memoryNotes.clear(); "Cleared." }
            else -> "Usage: add <note> | list | clear"
        }
    }

    // ---- Helpers -----------------------------------------------------------

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) KimiMobile/1.0")
            .get()
            .build()
        client.newCall(request).execute().use { it.body?.string() }
    }.getOrNull()

    private fun stripTags(html: String): String =
        html.replace(Regex("<script[\\s\\S]*?</script>"), " ")
            .replace(Regex("<style[\\s\\S]*?</style>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
}

/** Tiny safe expression evaluator: + - * / % ^ ( ) with standard precedence. */
object Calculator {

    fun eval(expression: String): Double {
        val parser = Parser(expression.filterNot { it.isWhitespace() })
        val value = parser.parseExpression()
        require(parser.atEnd()) { "unexpected trailing input" }
        return value
    }

    /** Recursive descent — methods can call each other regardless of order. */
    private class Parser(private val src: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= src.length

        private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        private fun advance(): Char = src[pos++]

        fun parseExpression(): Double {
            var v = parseTerm()
            while (peek() == '+' || peek() == '-') {
                val op = advance()
                v = if (op == '+') v + parseTerm() else v - parseTerm()
            }
            return v
        }

        private fun parseTerm(): Double {
            var v = parsePower()
            while (peek() == '*' || peek() == '/' || peek() == '%') {
                val op = advance()
                val r = parsePower()
                v = when (op) {
                    '*' -> v * r
                    '/' -> {
                        require(r != 0.0) { "division by zero" }
                        v / r
                    }
                    else -> {
                        require(r != 0.0) { "modulo by zero" }
                        v % r
                    }
                }
            }
            return v
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            // Right-associative: 2^3^2 == 2^9
            return if (peek() == '^') {
                advance()
                base.pow(parsePower())
            } else base
        }

        private fun parseUnary(): Double {
            if (peek() == '-') {
                advance()
                return -parseUnary()
            }
            if (peek() == '+') {
                advance()
                return parseUnary()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            val c = peek()
            if (c == '(') {
                advance()
                val v = parseExpression()
                require(peek() == ')') { "missing ')'" }
                advance()
                return v
            }
            if (c.isDigit() || c == '.') {
                val sb = StringBuilder()
                while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) sb.append(advance())
                return sb.toString().toDoubleOrNull()
                    ?: throw IllegalArgumentException("bad number \"$sb\"")
            }
            throw IllegalArgumentException(
                if (c == '\u0000') "unexpected end of expression" else "unexpected '$c'"
            )
        }
    }
}
