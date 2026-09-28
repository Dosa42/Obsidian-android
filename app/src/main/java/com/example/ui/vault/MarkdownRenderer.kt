package com.example.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun MarkdownRenderer(
    content: String,
    onWikilinkClicked: (String) -> Unit,
    onTagClicked: ((String) -> Unit)? = null,
    onCheckboxToggled: ((Int, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lines = content.lines()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        lines.forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End of code block
                    CodeBlockView(codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                return@forEachIndexed
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                return@forEachIndexed
            }

            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ObsidianTextPrimary
                        ),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = ObsidianPurpleLight
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = ObsidianTeal
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("> ") -> {
                    // Blockquote
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(ObsidianSurfaceElevated, RoundedCornerShape(4.dp))
                            .border(width = 3.dp, color = ObsidianPurple, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = trimmed.removePrefix("> ").trim(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = ObsidianTextSecondary
                            )
                        )
                    }
                }
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    // Checklist item
                    val isChecked = trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")
                    val taskText = trimmed.substring(6).trim()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onCheckboxToggled != null) {
                                onCheckboxToggled?.invoke(lineIndex, !isChecked)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (isChecked) "Checked" else "Unchecked",
                            tint = if (isChecked) ObsidianGreen else ObsidianTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FormattedInlineText(
                            rawText = taskText,
                            onWikilinkClicked = onWikilinkClicked,
                            onTagClicked = onTagClicked,
                            isStrikethrough = isChecked
                        )
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    // Bullet list item
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = ObsidianPurpleLight,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FormattedInlineText(
                            rawText = trimmed.substring(2).trim(),
                            onWikilinkClicked = onWikilinkClicked,
                            onTagClicked = onTagClicked
                        )
                    }
                }
                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    FormattedInlineText(
                        rawText = trimmed,
                        onWikilinkClicked = onWikilinkClicked,
                        onTagClicked = onTagClicked
                    )
                }
            }
        }

        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            CodeBlockView(codeBlockLines.joinToString("\n"))
        }
    }
}

@Composable
fun CodeBlockView(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianCodeBackground)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = ObsidianTeal,
            lineHeight = 18.sp
        )
    }
}

@Suppress("DEPRECATION")
@Composable
fun FormattedInlineText(
    rawText: String,
    onWikilinkClicked: (String) -> Unit,
    onTagClicked: ((String) -> Unit)?,
    isStrikethrough: Boolean = false
) {
    val annotatedString = buildAnnotatedString {
        var i = 0
        while (i < rawText.length) {
            // Check for wikilink [[Target]]
            if (rawText.startsWith("[[", i)) {
                val end = rawText.indexOf("]]", i + 2)
                if (end != -1) {
                    val target = rawText.substring(i + 2, end).trim()
                    pushStringAnnotation(tag = "WIKILINK", annotation = target)
                    withStyle(
                        SpanStyle(
                            color = ObsidianPurpleLight,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("[[${target}]]")
                    }
                    pop()
                    i = end + 2
                    continue
                }
            }

            // Check for tags #tag
            if (rawText[i] == '#' && (i == 0 || rawText[i - 1].isWhitespace())) {
                var end = i + 1
                while (end < rawText.length && (rawText[end].isLetterOrDigit() || rawText[end] == '_' || rawText[end] == '-')) {
                    end++
                }
                if (end > i + 1) {
                    val tag = rawText.substring(i + 1, end)
                    pushStringAnnotation(tag = "TAG", annotation = tag)
                    withStyle(
                        SpanStyle(
                            color = ObsidianYellow,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append("#$tag")
                    }
                    pop()
                    i = end
                    continue
                }
            }

            // Check for bold **text**
            if (rawText.startsWith("**", i)) {
                val end = rawText.indexOf("**", i + 2)
                if (end != -1) {
                    val boldText = rawText.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)) {
                        append(boldText)
                    }
                    i = end + 2
                    continue
                }
            }

            // Check for italic *text*
            if (rawText[i] == '*' && (i + 1 < rawText.length && rawText[i + 1] != '*')) {
                val end = rawText.indexOf('*', i + 1)
                if (end != -1 && (end + 1 >= rawText.length || rawText[end + 1] != '*')) {
                    val italicText = rawText.substring(i + 1, end)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = ObsidianTextPrimary)) {
                        append(italicText)
                    }
                    i = end + 1
                    continue
                }
            }

            // Check for inline code `code`
            if (rawText[i] == '`') {
                val end = rawText.indexOf('`', i + 1)
                if (end != -1) {
                    val codeText = rawText.substring(i + 1, end)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = ObsidianCodeBackground,
                            color = ObsidianTeal
                        )
                    ) {
                        append(" $codeText ")
                    }
                    i = end + 1
                    continue
                }
            }

            append(rawText[i])
            i++
        }
    }

    androidx.compose.foundation.text.ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = if (isStrikethrough) ObsidianTextMuted else ObsidianTextPrimary,
            lineHeight = 22.sp
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onWikilinkClicked(annotation.item)
                    return@ClickableText
                }

            annotatedString.getStringAnnotations(tag = "TAG", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onTagClicked?.invoke(annotation.item)
                }
        }
    )
}
