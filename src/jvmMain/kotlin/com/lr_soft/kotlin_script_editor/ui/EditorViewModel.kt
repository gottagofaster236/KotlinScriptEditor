package com.lr_soft.kotlin_script_editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lr_soft.kotlin_script_editor.model.CompilationError

class EditorViewModel {

    private var _uiState by mutableStateOf(EditorUiState())

    var uiState: EditorUiState
        get() = _uiState
        private set(value) {
            _uiState = value
        }

    fun onEditorTextChanged(editorText: String) {
        uiState = uiState.copy(editorText = editorText)
    }

    fun runOrStopProgram() {
        uiState = _uiState.copy(isProgramRunning = !_uiState.isProgramRunning)
    }

    fun onErrorClicked(error: CompilationError) {
        TODO()
    }
}
