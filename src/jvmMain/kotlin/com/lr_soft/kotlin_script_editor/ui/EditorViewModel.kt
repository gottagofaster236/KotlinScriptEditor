package com.lr_soft.kotlin_script_editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.lr_soft.kotlin_script_editor.model.CompilationError
import com.lr_soft.kotlin_script_editor.model.KotlinScriptRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class EditorViewModel(
    private val kotlinScriptRunner: KotlinScriptRunner,
    private val scope: CoroutineScope
) {

    private var _uiState by mutableStateOf(EditorUiState())
    var uiState: EditorUiState
        get() = _uiState
        /**
         * Guarded by [uiStateLock].
         */
        private set(value) {
            _uiState = value
        }

    /**
     * Guarded by [uiStateLock].
     */
    private var runCodeJob: Job? = null
    private val uiStateLock = Object()

    fun onEditorTextUpdated(editorTextFieldValue: TextFieldValue) {
        uiState = uiState.copy(
            editorTextFieldValue = fixTabs(editorTextFieldValue)
        )
    }

    // https://github.com/JetBrains/compose-multiplatform/issues/615
    private fun fixTabs(editorTextFieldValue: TextFieldValue): TextFieldValue {
        val isTab: (Char) -> Boolean = { it == '\t' }
        val tabsCount = editorTextFieldValue.text.count(isTab)
        if (tabsCount == 0) {
            return editorTextFieldValue
        }
        val lastTab = editorTextFieldValue.text.indexOfLast(isTab)
        val newSelection = TextRange(lastTab + tabsCount * 4)
        return TextFieldValue(editorTextFieldValue.text.replace("\t", "    "), newSelection)
    }

    fun runOrStopProgram() {
        if (uiState.isProgramRunning) {
            val currentRunCodeJob = runCodeJob!!
            scope.launch(Dispatchers.IO) {
                currentRunCodeJob.cancelAndJoin()
                synchronized(uiStateLock) {
                    if (runCodeJob != currentRunCodeJob) {
                        return@synchronized
                    }
                    uiState = uiState.copy(isProgramRunning = false)
                    runCodeJob = null
                }
            }
            return
        }

        synchronized(uiStateLock) {
            uiState = uiState.copy(
                isProgramRunning = true,
                outputText = "",
                errorList = emptyList()
            )
            runCodeJob = scope.launch(Dispatchers.IO) {
                runCode()
            }
        }
    }

    private suspend fun runCode() {
        var returnCode = -1
        try {
            returnCode = tryRunCode()
        } catch (e: KotlinScriptRunner.CompilationFailedException) {
            synchronized(uiStateLock) {
                uiState = uiState.copy(errorList = e.compilationErrors)
            }
        }

        synchronized(uiStateLock) {
            uiState = uiState.copy(isProgramRunning = false, lastReturnCode = returnCode)
            runCodeJob = null
        }
    }

    private suspend fun tryRunCode(): Int = coroutineScope {
        val outputChannel = Channel<String>(Channel.BUFFERED)
        val outputJob = launch(Dispatchers.IO) {
            for (line in outputChannel) {
                synchronized(uiStateLock) {
                    uiState = uiState.copy(outputText = uiState.outputText + line)
                }
            }
        }
        val returnCode = kotlinScriptRunner.runCode(uiState.editorTextFieldValue.text, outputChannel)
        outputJob.join()
        returnCode
    }

    fun onErrorClicked(error: CompilationError) {
        val sourceCodePosition = error.sourceCodePosition.takeUnless {
            it == CompilationError.NO_SOURCE_CODE_POSITION
        } ?: return

        synchronized(uiStateLock) {
            uiState = uiState.copy(
                editorTextFieldValue = uiState.editorTextFieldValue.copy(
                    selection = TextRange(sourceCodePosition)
                ),
                timesEditorFocusRequested = uiState.timesEditorFocusRequested + 1
            )
        }
    }
}
