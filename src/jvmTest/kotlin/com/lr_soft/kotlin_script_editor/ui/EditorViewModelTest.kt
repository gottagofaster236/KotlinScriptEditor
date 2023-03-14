package com.lr_soft.kotlin_script_editor.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.lr_soft.kotlin_script_editor.model.CompilationError
import com.lr_soft.kotlin_script_editor.model.KotlinScriptRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


@ExtendWith(MockitoExtension::class)
@Timeout(10)
class EditorViewModelTest {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val kotlinScriptRunner = mock<KotlinScriptRunner>()
    private var editorViewModel = EditorViewModel(kotlinScriptRunner, coroutineScope)

    @AfterEach
    fun tearDown() {
        coroutineScope.cancel()
    }

    @Test
    fun testOnEditorTextUpdated() {
        assert(editorViewModel.uiState.editorTextFieldValue == TextFieldValue())
        val newValue = TextFieldValue("abc", TextRange(1, 2))
        editorViewModel.onEditorTextUpdated(newValue)
        assertEquals(newValue, editorViewModel.uiState.editorTextFieldValue)

        editorViewModel.onEditorTextUpdated(
            TextFieldValue("abc\t\tabc")
        )
        assertEquals(
            TextFieldValue(
                "abc        abc",
                TextRange(12)
            ),
            editorViewModel.uiState.editorTextFieldValue
        )
    }

    @Test
    fun testRunOrStopProgram() = runBlocking {
        val wasStopped = AtomicBoolean(false)
        kotlinScriptRunner.stub {
            onBlocking { runCode(any(), any()) } doSuspendableAnswer {
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    wasStopped.set(true)
                }
                0
            }
        }

        val code = "123"
        editorViewModel.onEditorTextUpdated(TextFieldValue(code))
        assertFalse(editorViewModel.uiState.isProgramRunning)
        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        
        verifyBlocking(kotlinScriptRunner) { runCode(eq(code), any()) }
        assertTrue(editorViewModel.uiState.isProgramRunning)

        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        assertTrue(wasStopped.get())
        assertFalse(editorViewModel.uiState.isProgramRunning)
    }

    @Test
    fun testOnErrorClicked() {
        editorViewModel.onEditorTextUpdated(TextFieldValue("abc"))
        assertEquals(TextRange(0), editorViewModel.uiState.editorTextFieldValue.selection)
        assertEquals(0, editorViewModel.uiState.timesEditorFocusRequested)

        editorViewModel.onErrorClicked(CompilationError("error", 2))
        assertEquals(TextRange(2), editorViewModel.uiState.editorTextFieldValue.selection)
        assertEquals(1, editorViewModel.uiState.timesEditorFocusRequested)
    }

    @Test
    fun testOutput() = runBlocking {
        val punctuation = AtomicInteger('!'.code)
        kotlinScriptRunner.stub {
            onBlocking { runCode(any(), any()) } doSuspendableAnswer {
                val channel = getChannel(it)
                channel.send("Hello\n")
                channel.send("World" + punctuation.get().toChar())
                channel.close()
                0
            }
        }
        assertEquals("", editorViewModel.uiState.outputText)
        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        assertEquals("Hello\nWorld!", editorViewModel.uiState.outputText)

        punctuation.set('?'.code)
        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        assertEquals("Hello\nWorld?", editorViewModel.uiState.outputText)
    }

    @Test
    fun testInterimOutput() = runBlocking {
        kotlinScriptRunner.stub {
            onBlocking { runCode(any(), any()) } doSuspendableAnswer {
                getChannel(it).send("Hello\n")
                awaitCancellation()
            }
        }

        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        assertEquals("Hello\n", editorViewModel.uiState.outputText)
        editorViewModel.runOrStopProgram()
    }

    @Test
    fun testReturnCode() = runBlocking {
        kotlinScriptRunner.stub {
            onBlocking { runCode(any(), any()) } doSuspendableAnswer {
                getChannel(it).close()
                123
            }
        }
        assertEquals(0, editorViewModel.uiState.lastReturnCode)
        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        assertEquals(123, editorViewModel.uiState.lastReturnCode)
    }

    @Test
    fun testErrorList() = runBlocking {
        val errorList = listOf(
            CompilationError("error1", 123),
            CompilationError("error2", 321)
        )
        kotlinScriptRunner.stub {
            onBlocking { runCode(any(), any()) } doSuspendableAnswer {
                getChannel(it).close()
                throw KotlinScriptRunner.CompilationFailedException(errorList)
            }
        }
        assertEquals(emptyList<CompilationError>(), editorViewModel.uiState.errorList)
        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        assertEquals(errorList, editorViewModel.uiState.errorList)

        kotlinScriptRunner.stub {
            onBlocking { runCode(any(), any()) } doSuspendableAnswer {
                getChannel(it).close()
                0
            }
        }
        editorViewModel.runOrStopProgram()
        waitUntilProgramExecutes()
        assertEquals(emptyList<CompilationError>(), editorViewModel.uiState.errorList)
    }

    private fun getChannel(invocationOnMock: InvocationOnMock): Channel<String> {
        return invocationOnMock.getArgument(1)
    }

    private suspend fun waitUntilProgramExecutes() {
        delay(1000)
    }
}
