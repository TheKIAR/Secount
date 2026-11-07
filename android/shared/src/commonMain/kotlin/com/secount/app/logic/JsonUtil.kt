package com.secount.app.logic

/** Minimal JSON helpers: string escaping plus top-level object splitting. */
object JsonUtil {

    fun escape(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c.code < 0x20) {
                    out.append(String.format("\\u%04x", c.code))
                } else {
                    out.append(c)
                }
            }
        }
        return out.toString()
    }

    fun unquote(s: String): String {
        var t = s.trim()
        if (t.length >= 2 && t[0] == '"' && t[t.length - 1] == '"') {
            val out = StringBuilder()
            val inner = t.substring(1, t.length - 1)
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                if (c == '\\' && i + 1 < inner.length) {
                    i++
                    when (val n = inner[i]) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> if (i + 4 < inner.length) {
                            out.append(inner.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> out.append(n)
                    }
                } else {
                    out.append(c)
                }
                i++
            }
            return out.toString()
        }
        return t
    }

    /** Split the inside of a {...} or [...] on top-level commas. */
    fun splitTopLevel(inside: String): List<String> {
        var s = inside.trim()
        if (s.startsWith("{") && s.endsWith("}") && isSingleBalanced(s)) {
            s = s.substring(1, s.length - 1)
        }
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var inStr = false
        var depth = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                cur.append(c)
                if (c == '\\' && i + 1 < s.length) {
                    i++
                    cur.append(s[i])
                } else if (c == '"') {
                    inStr = false
                }
            } else if (c == '"') {
                inStr = true
                cur.append(c)
            } else if (c == '{' || c == '[') {
                depth++
                cur.append(c)
            } else if (c == '}' || c == ']') {
                depth--
                cur.append(c)
            } else if (c == ',' && depth == 0) {
                parts.add(cur.toString())
                cur.setLength(0)
            } else {
                cur.append(c)
            }
            i++
        }
        if (cur.toString().trim().isNotEmpty()) {
            parts.add(cur.toString())
        }
        return parts
    }

    /** Extract complete top-level JSON objects while preserving their braces. */
    fun extractTopLevelObjects(inside: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var inStr = false
        var start = -1
        var i = 0
        while (i < inside.length) {
            val c = inside[i]
            if (inStr) {
                if (c == '\\' && i + 1 < inside.length) i++
                else if (c == '"') inStr = false
            } else {
                if (c == '"') inStr = true
                else if (c == '{') {
                    if (depth == 0) start = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(inside.substring(start, i + 1))
                        start = -1
                    }
                    if (depth < 0) depth = 0
                }
            }
            i++
        }
        return out
    }

    /** True if the whole string is one balanced {...} or [...] object. */
    private fun isSingleBalanced(s: String): Boolean {
        var inStr = false
        var depth = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') {
                    i++
                } else if (c == '"') {
                    inStr = false
                }
            } else if (c == '"') {
                inStr = true
            } else if (c == '{' || c == '[') {
                depth++
            } else if (c == '}' || c == ']') {
                depth--
                if (depth == 0 && i != s.length - 1) {
                    return false
                }
            }
            i++
        }
        return depth == 0
    }
}
