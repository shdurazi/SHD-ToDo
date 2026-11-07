package com.shdurazi.todo.core

/**
 * Tiny dependency-free JSON reader / writer.
 * Objects become `Map<String, Any?>`, arrays `List<Any?>`, integers `Long`, other numbers `Double`.
 */
object Json {

    fun parse(text: String): Any? {
        val r = Reader(text)
        r.skipWs()
        val v = r.value()
        r.skipWs()
        require(r.pos == text.length) { "Unexpected trailing data at ${r.pos}" }
        return v
    }

    fun stringify(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Float -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }

    private class Reader(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            if (pos >= s.length) error("Unexpected end of input")
            val c = s[pos]
            return when {
                c == '{' -> obj()
                c == '[' -> arr()
                c == '"' -> str()
                c == 't' -> lit("true", true)
                c == 'f' -> lit("false", false)
                c == 'n' -> lit("null", null)
                c == '-' || (c in '0'..'9') -> num()
                else -> error("Unexpected '$c' at $pos")
            }
        }

        private fun lit(word: String, result: Any?): Any? {
            if (!s.startsWith(word, pos)) error("Bad literal at $pos")
            pos += word.length
            return result
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return out
            }
            while (true) {
                skipWs()
                if (pos >= s.length || s[pos] != '"') error("Expected key at $pos")
                val k = str()
                skipWs()
                if (pos >= s.length || s[pos] != ':') error("Expected ':' at $pos")
                pos++
                skipWs()
                out[k] = value()
                skipWs()
                if (pos >= s.length) error("Unterminated object")
                val c = s[pos]
                if (c == ',') {
                    pos++
                } else if (c == '}') {
                    pos++
                    return out
                } else {
                    error("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun arr(): List<Any?> {
            val out = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return out
            }
            while (true) {
                skipWs()
                out.add(value())
                skipWs()
                if (pos >= s.length) error("Unterminated array")
                val c = s[pos]
                if (c == ',') {
                    pos++
                } else if (c == ']') {
                    pos++
                    return out
                } else {
                    error("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) error("Unterminated string")
                val c = s[pos++]
                if (c == '"') return sb.toString()
                if (c != '\\') {
                    sb.append(c)
                    continue
                }
                if (pos >= s.length) error("Bad escape")
                val e = s[pos++]
                when (e) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (pos + 4 > s.length) error("Bad unicode escape")
                        sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                        pos += 4
                    }
                    else -> error("Bad escape at $pos")
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && s[pos] in '0'..'9') pos++
            var isDouble = false
            if (pos < s.length && s[pos] == '.') {
                isDouble = true
                pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            val t = s.substring(start, pos)
            return if (isDouble) t.toDouble() else (t.toLongOrNull() ?: t.toDouble())
        }
    }
}
