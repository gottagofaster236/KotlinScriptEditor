package com.lr_soft.kotlin_script_editor.ui

import androidx.compose.runtime.Immutable
import com.lr_soft.kotlin_script_editor.model.CompilationError

@Immutable
data class EditorUiState(
    val editorText: String = "",
    val outputText: String = "",
    val errorList: List<CompilationError> = emptyList(),
    val isProgramRunning: Boolean = false,
    val lastReturnCode: Int = 0,
)
