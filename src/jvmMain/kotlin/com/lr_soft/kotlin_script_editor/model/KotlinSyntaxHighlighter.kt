package com.lr_soft.kotlin_script_editor.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * https://kotlinlang.org/docs/keyword-reference.html
 */
private val kotlinKeywords = setOf(
    "as", "as?", "break", "class", "continue", "do", "else", "false",
    "for", "fun", "if", "in", "!in", "interface", "is", "!is", "null",
    "object", "package", "return", "super", "this", "throw", "true",
    "try", "typealias", "val", "var", "when", "while", "by", "catch",
    "constructor", "delegate", "dynamic", "field", "file", "finally",
    "get", "import", "init", "param", "property", "receiver", "set",
    "setparam", "value", "where", "abstract", "actual", "annotation",
    "companion", "const", "crossinline", "data", "enum", "expect",
    "external", "final", "infix", "inline", "inner", "internal",
    "lateinit", "noinline", "open", "operator", "out", "override",
    "private", "protected", "public", "reified", "sealed", "suspend",
    "tailrec", "vararg"
)

fun highlightKotlinSyntax(code: String, keywordsColor: Color): AnnotatedString {
    val colorStyle = SpanStyle(color = keywordsColor)

    val wordRegex = Regex("\\b[a-z]+\\b")
    val wordMatches = wordRegex.findAll(code)
    val spanStyles = wordMatches
        .filter { it.value in kotlinKeywords }
        .map { AnnotatedString.Range(colorStyle, it.range.first, it.range.last + 1) }
        .toList()

    return AnnotatedString(code, spanStyles, listOf())
}
