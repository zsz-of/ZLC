package com.zsz.zlivephoto.core

/**
 * vivo/OPPO 共用的 cameralbum! footer 编解码。
 */
internal class FooterException(message: String) : Exception(message)

internal object FooterUtil {
    val magic: ByteArray = byteArrayOf(
        0x1B, 0x2A, 0x39, 0x48, 0x57, 0x66, 0x75,
        0x84.toByte(), 0x93.toByte(), 0xA2.toByte(), 0xB3.toByte()
    )
    val marker: ByteArray = "cameralbum!".toByteArray(Charsets.US_ASCII)
    val vivoPrefix: ByteArray = "vivo".toByteArray(Charsets.US_ASCII)
    val extPrefix: ByteArray = "vivoMediaExtInfovivo".toByteArray(Charsets.US_ASCII) // vivoMediaExtInfo + vivo
    const val idLen: Int = 28

    /** OPPO 内嵌单文件使用的固定 ID（'motionphoto' + '0'*17） */
    val oppoFixedId: String = "motionphoto" + "0".repeat(17)

    internal class FooterInfo {
        var json: Map<String, Any?>? = null
        var id: String? = null
        var prefix: ByteArray = ByteArray(0)
        var footerStart: Int = 0
        var version: Int? = null
        var imageTime: Long? = null
        var livephotoId: String? = null
    }

    /**
     * 生成 vivo 风格 livephoto ID：'-<数字><8位随机字符>'，'0' 填充至 28 字符。
     */
    fun generateLivephotoId(): String {
        val num = kotlin.random.Random.nextInt(1, 2147483647)
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rand = (0 until 8).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
        val s = "-$num$rand"
        return s.padEnd(idLen, '0').substring(0, idLen)
    }

    /**
     * 构造完整 footer（含前缀）。
     */
    fun buildFooter(jsonBytes: ByteArray, idStr: String, prefix: ByteArray): ByteArray {
        var idBytes = idStr.toByteArray(Charsets.US_ASCII)
        if (idBytes.size != idLen) {
            val padded = ByteArray(idLen)
            System.arraycopy(idBytes, 0, padded, 0, minOf(idBytes.size, idLen))
            for (i in idBytes.size until idLen) padded[i] = '0'.code.toByte()
            idBytes = padded
        }

        val tail = ByteArray(idLen + 4 + magic.size)
        System.arraycopy(idBytes, 0, tail, 0, idLen)
        tail[idLen] = 0xFF.toByte()
        tail[idLen + 1] = 0xFF.toByte()
        tail[idLen + 2] = 0xFF.toByte()
        tail[idLen + 3] = 0xFF.toByte()
        System.arraycopy(magic, 0, tail, idLen + 4, magic.size)

        val result = ByteArray(prefix.size + jsonBytes.size + 4 + marker.size + 4 + tail.size)
        var pos = 0
        System.arraycopy(prefix, 0, result, pos, prefix.size); pos += prefix.size
        System.arraycopy(jsonBytes, 0, result, pos, jsonBytes.size); pos += jsonBytes.size
        BinaryUtils.writeU32BE(result, pos, jsonBytes.size.toLong()); pos += 4
        System.arraycopy(marker, 0, result, pos, marker.size); pos += marker.size
        BinaryUtils.writeU32BE(result, pos, (tail.size + 4).toLong()); pos += 4
        System.arraycopy(tail, 0, result, pos, tail.size)
        return result
    }

    /**
     * 按样本键序生成 footer JSON（紧凑分隔符，UTF-8 直出，与 Python ensure_ascii=False 一致）。
     */
    fun buildFooterJson(fields: Map<String, Any?>): ByteArray {
        val sb = StringBuilder()
        sb.append('{')
        var first = true
        for ((key, value) in fields) {
            if (!first) sb.append(',')
            first = false
            sb.append('"')
            JsonMin.escapeString(key, sb)
            sb.append('"')
            sb.append(':')
            JsonMin.writeValue(value, sb)
        }
        sb.append('}')
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * 从数据尾部解析 footer。
     */
    fun parseFooter(data: ByteArray): FooterInfo? {
        val idx = BinaryUtils.lastIndexOf(data, marker)
        if (idx == -1 || idx + 15 + 43 > data.size) return null

        val len2 = BinaryUtils.readU32BE(data, idx + 11)
        val tailLen = (len2 - 4).toInt()
        if (tailLen < 0 || idx + 15 + tailLen > data.size) return null
        val tail = data.copyOfRange(idx + 15, idx + 15 + tailLen)
        if (tail.size != len2.toInt() - 4 || tail.size != 43) return null
        val ffMarker = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        if (!BinaryUtils.arrayEquals(tail, 28, ffMarker) ||
            !BinaryUtils.arrayEquals(tail, 32, magic)
        ) return null

        val idStr = String(tail.copyOfRange(0, 28), Charsets.US_ASCII)
        if (idx < 4) return null

        val len1 = BinaryUtils.readU32BE(data, idx - 4)
        val jsonStart = idx - 4 - len1.toInt()
        if (jsonStart < 0) return null

        val jsonBytes = data.copyOfRange(jsonStart, jsonStart + len1.toInt())
        val payload: Map<String, Any?>
        try {
            payload = JsonMin.parse(jsonBytes) ?: return null
        } catch (e: Exception) {
            return null
        }

        // 前缀识别
        var prefix: ByteArray = ByteArray(0)
        var footerStart = jsonStart
        if (jsonStart >= 20 && BinaryUtils.arrayEquals(data, jsonStart - 20, extPrefix)) {
            prefix = extPrefix
            footerStart = jsonStart - 20
        } else if (jsonStart >= 4 && BinaryUtils.arrayEquals(data, jsonStart - 4, vivoPrefix)) {
            prefix = vivoPrefix
            footerStart = jsonStart - 4
        }

        val info = FooterInfo()
        info.json = payload
        info.id = idStr
        info.prefix = prefix
        info.footerStart = footerStart

        payload["version"]?.let { v ->
            if (v is Int) info.version = v
            else if (v is Long) info.version = v.toInt()
        }
        payload["com.android.camera.imageTime"]?.let { v ->
            if (v is Int) info.imageTime = v.toLong()
            else if (v is Long) info.imageTime = v
        }
        payload["com.android.camera.livephoto"]?.let { v ->
            if (v is String) info.livephotoId = v
        }

        return info
    }
}

/** 最小化的 JSON 解析/序列化工具，仅用标准库。 */
internal object JsonMin {
    fun parse(bytes: ByteArray): Map<String, Any?>? {
        return try {
            val s = String(bytes, Charsets.UTF_8)
            val p = JsonParser(s)
            val v = p.parseValue() as? Map<String, Any?>
            v
        } catch (e: Exception) {
            null
        }
    }

    fun writeValue(v: Any?, sb: StringBuilder) {
        when (v) {
            is String -> {
                sb.append('"')
                escapeString(v, sb)
                sb.append('"')
            }
            is Int -> sb.append(v.toString())
            is Long -> sb.append(v.toString())
            is Double -> sb.append(v.toString())
            is Boolean -> sb.append(if (v) "true" else "false")
            is IntArray -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    sb.append(v[i].toString())
                }
                sb.append(']')
            }
            is LongArray -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    sb.append(v[i].toString())
                }
                sb.append(']')
            }
            is DoubleArray -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    sb.append(v[i].toString())
                }
                sb.append(']')
            }
            is Array<*> -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    writeValue(v[i], sb)
                }
                sb.append(']')
            }
            else -> throw FooterException("不支持的 JSON 值类型：${v?.javaClass?.simpleName}")
        }
    }

    fun escapeString(s: String, sb: StringBuilder) {
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u")
                        sb.append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
    }
}

private class JsonParser(val s: String) {
    var i = 0

    fun parseValue(): Any? {
        skipWs()
        if (i >= s.length) throw IllegalStateException("EOF")
        return when (s[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBool()
            'n' -> parseNull()
            else -> parseNumber()
        }
    }

    private fun skipWs() {
        while (i < s.length) {
            val c = s[i]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++
            else break
        }
    }

    private fun parseObject(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        i++ // skip '{'
        skipWs()
        if (i < s.length && s[i] == '}') { i++; return m }
        while (true) {
            skipWs()
            val key = parseString()
            skipWs()
            if (i >= s.length || s[i] != ':') throw IllegalStateException("expected :")
            i++
            val value = parseValue()
            m[key] = value
            skipWs()
            if (i < s.length) {
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
            }
            throw IllegalStateException("expected , or }")
        }
    }

    private fun parseArray(): List<Any?> {
        val arr = mutableListOf<Any?>()
        i++ // skip '['
        skipWs()
        if (i < s.length && s[i] == ']') { i++; return arr }
        while (true) {
            val v = parseValue()
            arr.add(v)
            skipWs()
            if (i < s.length) {
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return arr }
            }
            throw IllegalStateException("expected , or ]")
        }
    }

    private fun parseString(): String {
        if (i >= s.length || s[i] != '"') throw IllegalStateException("expected string")
        i++
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '"' -> { i++; return sb.toString() }
                '\\' -> {
                    i++
                    if (i >= s.length) throw IllegalStateException("EOF in escape")
                    when (s[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            i++
                            if (i + 4 > s.length) throw IllegalStateException("EOF in unicode escape")
                            val hex = s.substring(i, i + 4)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                            continue
                        }
                        else -> throw IllegalStateException("bad escape")
                    }
                    i++
                }
                else -> { sb.append(c); i++ }
            }
        }
        throw IllegalStateException("EOF in string")
    }

    private fun parseBool(): Boolean {
        if (s.startsWith("true", i)) { i += 4; return true }
        if (s.startsWith("false", i)) { i += 5; return false }
        throw IllegalStateException("expected bool")
    }

    private fun parseNull(): Any? {
        if (s.startsWith("null", i)) { i += 4; return null }
        throw IllegalStateException("expected null")
    }

    private fun parseNumber(): Any {
        val start = i
        while (i < s.length) {
            val c = s[i]
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || c in '0'..'9') {
                i++
            } else break
        }
        val num = s.substring(start, i)
        return try {
            if (num.contains('.') || num.contains('e') || num.contains('E')) {
                num.toDouble()
            } else {
                val l = num.toLong()
                if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
            }
        } catch (e: Exception) {
            throw IllegalStateException("bad number")
        }
    }
}
