package com.zsz.zlivephoto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染器（仅用于更新说明展示，无第三方依赖）。
 *
 * 支持子集：标题(#~######)、无序/有序列表(含嵌套缩进)、段落、加粗(**x**)、
 * 行内代码(`x`)、引用(>)、分割线(---)、GitHub 风格表格(| a | b |)。
 * 表格分隔行（如 |---|:---|）自动忽略。
 */
private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    object Rule : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullets(val items: List<BulletItem>) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data class Quote(val text: String) : MdBlock
}

private data class BulletItem(val indent: Int, val prefix: String, val text: String)

private val HEADING_RE = Regex("""^#{1,6}\s+(.+?)\s*#*\s*$""")
private val RULE_RE = Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")
private val BULLET_RE = Regex("""^(\s*)([-*+])\s+(.+?)\s*$""")
private val NUMBERED_RE = Regex("""^(\s*)(\d{1,3})[.)]\s+(.+?)\s*$""")
private val TABLE_SEP_RE = Regex(""":?-+:?""")

/** 行内加粗 / 行内代码（用于 AnnotatedString 上色） */
private val INLINE_RE = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")

/** 行首缩进对应的嵌套深度（约 2 空格 = 1 级，最多 3 级） */
private fun indentOf(lead: String): Int =
    (lead.length / 2).coerceIn(0, 3)

private class BulletStart(val indent: Int, val prefix: String, val text: String) {
    fun toItem() = BulletItem(indent, prefix, text)
}

private fun parseBulletStart(line: String): BulletStart? {
    BULLET_RE.matchEntire(line)?.let { m ->
        return BulletStart(
            indentOf(m.groupValues[1]), "• ", m.groupValues[3]
        )
    }
    NUMBERED_RE.matchEntire(line)?.let { m ->
        return BulletStart(
            indentOf(m.groupValues[1]), "${m.groupValues[2]}. ", m.groupValues[3]
        )
    }
    return null
}

/** 该行是否属于另起一类的特殊块（列表连续行/段落收集时判断用） */
private fun isSpecialStart(line: String): Boolean {
    val t = line.trimStart()
    return HEADING_RE.matchEntire(t) != null ||
        RULE_RE.matches(line) ||
        parseBulletStart(line) != null ||
        t.startsWith(">") ||
        t.startsWith("|")
}

private fun parseMarkdown(md: String): List<MdBlock> {
    val lines = md.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) { i++; continue }

        // 标题
        HEADING_RE.matchEntire(line.trim())?.let { m ->
            blocks.add(MdBlock.Heading(headingLevel(line), m.groupValues[1].trim()))
            i++
            continue
        }
        // 分割线
        if (RULE_RE.matches(line)) {
            blocks.add(MdBlock.Rule)
            i++
            continue
        }
        // 引用（连续引用行合并）
        if (line.trimStart().startsWith(">")) {
            val sb = StringBuilder()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                val content = lines[i].trimStart().removePrefix(">").trimStart()
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(content)
                i++
            }
            blocks.add(MdBlock.Quote(sb.toString()))
            continue
        }
        // 列表
        val start = parseBulletStart(line)
        if (start != null) {
            val items = mutableListOf(start)
            i++
            while (i < lines.size) {
                val ln = lines[i]
                if (ln.isBlank()) { i++; break }
                val nested = parseBulletStart(ln)
                if (nested != null) {
                    items.add(nested)
                    i++
                    continue
                }
                if (isSpecialStart(ln)) break
                // 列表项续行（正文折行）
                val last = items.last()
                items[items.lastIndex] = BulletStart(last.indent, last.prefix, last.text + "\n" + ln.trim())
                i++
            }
            blocks.add(MdBlock.Bullets(items.map { it.toItem() }))
            continue
        }
        // 表格
        if (line.trimStart().startsWith("|")) {
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                val cells = lines[i].trim()
                    .removePrefix("|").removeSuffix("|")
                    .split("|").map { it.trim() }
                val isSep = cells.size > 1 && cells.all { TABLE_SEP_RE.matches(it) }
                if (!isSep) rows.add(cells)
                i++
            }
            if (rows.isNotEmpty()) blocks.add(MdBlock.Table(rows))
            continue
        }
        // 普通段落（合并相邻非空非特殊行）
        val sb = StringBuilder(line.trim())
        i++
        while (i < lines.size) {
            val nx = lines[i]
            if (nx.isBlank()) { i++; break }
            if (isSpecialStart(nx)) break
            sb.append("\n").append(nx.trim())
            i++
        }
        blocks.add(MdBlock.Paragraph(sb.toString()))
    }
    return blocks
}

private fun headingLevel(line: String): Int =
    line.takeWhile { it == '#' }.length

/** 把加粗 / 行内代码转成 AnnotatedString */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var pos = 0
    for (m in INLINE_RE.findAll(text)) {
        append(text, pos, m.range.first)
        if (m.groupValues[1].isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        } else {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[2]) }
        }
        pos = m.range.last + 1
    }
    if (pos < text.length) append(text, pos, text.length)
}

/**
 * 把整段 Markdown 说明渲染成可滚动内容。
 * 由调用方给 [modifier] 施加高度限制 / 外边距。
 */
@Composable
internal fun MarkdownBody(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> {
                    val style = when {
                        block.level <= 1 -> MaterialTheme.typography.titleLarge
                        block.level == 2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        inline(block.text),
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface
                    )
                }
                is MdBlock.Paragraph -> Text(
                    inline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Text(
                            inline("${item.prefix}${item.text}"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.padding(start = (item.indent * 16).dp)
                        )
                    }
                }
                is MdBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.rows.forEachIndexed { idx, cells ->
                        Text(
                            inline(cells.joinToString("    ")),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = scheme.onSurface
                        )
                    }
                }
                is MdBlock.Quote -> Text(
                    inline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(scheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                is MdBlock.Rule -> HorizontalDivider(color = scheme.outlineVariant)
            }
        }
    }
}
