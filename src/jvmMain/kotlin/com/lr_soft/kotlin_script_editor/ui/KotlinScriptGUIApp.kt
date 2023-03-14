package com.lr_soft.kotlin_script_editor.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.lr_soft.kotlin_script_editor.model.KotlinScriptRunner
import java.io.File

@Composable
fun KotlinScriptEditorApp() {
    val scope = rememberCoroutineScope()

    val editorViewModel = rememberSaveable {
        EditorViewModel(
            kotlinScriptRunner = KotlinScriptRunner(File("./script.kts")),
            scope = scope
        )
    }

    MaterialTheme {
        EditorScreen(editorViewModel)
    }
}
