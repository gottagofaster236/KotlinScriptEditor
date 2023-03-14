package com.lr_soft.kotlin_script_editor.model

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File

private const val CODE_FILENAME = "test.kts"

@Timeout(15)
class KotlinScriptRunnerTest {

    @TempDir
    lateinit var tempDir: File
    private lateinit var kotlinScriptRunner: KotlinScriptRunner
    private val outputChannel = Channel<String>(Channel.UNLIMITED)

    @BeforeEach
    fun setUp() {
        kotlinScriptRunner = KotlinScriptRunner(File(tempDir, CODE_FILENAME))
    }

    @Test
    fun testHelloWorld() = runBlocking {
        kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorld, outputChannel)
        assertEquals("Hello world!\n", outputChannel.joinToString())
    }

    @Test
    fun testHelloWorldWithoutNewline() = runBlocking {
        kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorldWithoutNewline, outputChannel)
        assertEquals("Hello world!", outputChannel.joinToString())
    }

    @Test
    fun testCompilationError() = runBlocking {
        try {
            kotlinScriptRunner.runCode(KotlinScriptSamples.compilationError, outputChannel)
            fail("Should throw CompilationFailedException!")
        } catch (e: KotlinScriptRunner.CompilationFailedException) {
            assertEquals(2, e.compilationErrors.size)

            assertTrue("error: unresolved reference: hello" in e.compilationErrors[0].errorText)
            assertFalse("world" in e.compilationErrors[0].errorText)
            assertTrue("error: unresolved reference: world" in e.compilationErrors[1].errorText)
            assertFalse("hello" in e.compilationErrors[1].errorText)

            assertEquals(0, e.compilationErrors[0].sourceCodePosition)
            assertEquals(8, e.compilationErrors[1].sourceCodePosition)
        }
    }

    @Test
    fun testReturnCode() = runBlocking {
        assertEquals(123, kotlinScriptRunner.runCode(KotlinScriptSamples.returnCode123, outputChannel))
    }

    @Test
    fun testCancel() = runBlocking {
        val codeJob = launch {
            kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorldPause, outputChannel)
        }
        var helloLine = outputChannel.receive()
        delay(100)
        outputChannel.tryReceive().getOrNull()?.let {
            helloLine += it
        }
        assertEquals("Hello\n", helloLine)
        codeJob.cancelAndJoin()
    }

    @Test
    fun testAlreadyRunningException() = runBlocking {
        val helloWorldPauseJob = launch {
            kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorldPause, outputChannel)
        }
        delay(100)

        try {
            kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorld, outputChannel)
            fail("Should throw ProgramAlreadyRunningException")
        } catch (_: KotlinScriptRunner.AlreadyRunningException) {}

        helloWorldPauseJob.cancelAndJoin()
    }

    private suspend fun Channel<String>.joinToString(): String {
        return this.consumeAsFlow().toList().joinToString()
    }
}
