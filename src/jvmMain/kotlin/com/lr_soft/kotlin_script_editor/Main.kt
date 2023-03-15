package com.lr_soft.kotlin_script_editor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lr_soft.kotlin_script_editor.ui.KotlinScriptEditorApp

fun main() = application {
    Window(
        title = "KotlinScriptEditor",
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
        onCloseRequest = ::exitApplication
    ) {
        KotlinScriptEditorApp()
    }
}
