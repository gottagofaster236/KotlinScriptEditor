package com.lr_soft.kotlin_script_editor.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinSyntaxHighlighterKtTest {

    @Test
    fun testHighlightKotlinSyntax() {
        val code = "while (true) { break }; val breakDance = 0 "
        val color = Color.Red

        val expected = AnnotatedString(
            text = code,
            spanStyles = listOf(
                AnnotatedString.Range(SpanStyle(color = Color.Red), 0, 5),
                AnnotatedString.Range(SpanStyle(color = Color.Red), 7, 11),
                AnnotatedString.Range(SpanStyle(color = Color.Red), 15, 20),
                AnnotatedString.Range(SpanStyle(color = Color.Red), 24, 27),
            )
        )
        val annotated = highlightKotlinSyntax(code, color)
        assertEquals(expected, annotated)
    }
}