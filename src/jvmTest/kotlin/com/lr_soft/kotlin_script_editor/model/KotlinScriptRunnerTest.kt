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

@Timeout(10)
class KotlinScriptRunnerTest {

    @TempDir
    lateinit var tempDir: File
    private lateinit var kotlinScriptRunner: KotlinScriptRunner
    private val lineOutputChannel = Channel<String>(Channel.UNLIMITED)

    @BeforeEach
    fun setUp() {
        kotlinScriptRunner = KotlinScriptRunner(File(tempDir, CODE_FILENAME))
    }

    @Test
    fun testHelloWorld() = runBlocking {
        kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorld, lineOutputChannel)
        assertEquals("Hello world!\n", lineOutputChannel.joinToString())
    }

    @Test
    fun testHelloWorldWithoutNewline() = runBlocking {
        kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorldWithoutNewline, lineOutputChannel)
        assertEquals("Hello world!\n", lineOutputChannel.joinToString())
    }

    @Test
    fun testCompilationError() = runBlocking {
        try {
            kotlinScriptRunner.runCode(KotlinScriptSamples.compilationError, lineOutputChannel)
            println("Output:\n${lineOutputChannel.joinToString()}")
            fail("Should throw CompilationFailedException!")
        } catch (e: KotlinScriptRunner.CompilationFailedException) {
            assertEquals(1, e.compilationErrors.size)
            val compilationError = e.compilationErrors[0]
            assertEquals(0, compilationError.sourceLine)
        }
    }

    @Test
    fun testReturnCode() = runBlocking {
        assertEquals(0, kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorld, lineOutputChannel))
        assertEquals(123, kotlinScriptRunner.runCode(KotlinScriptSamples.returnCode123, lineOutputChannel))
    }

    @Test
    fun testSleep() = runBlocking {
        val codeJob = launch {
            kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorldPause, lineOutputChannel)
        }
        delay(100)
        val helloLine = lineOutputChannel.receive()
        assertEquals("Hello\n", helloLine)
        codeJob.cancelAndJoin()
    }

    @Test
    fun testAlreadyRunningException() = runBlocking {
        val helloWorldPauseJob = launch {
            kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorldPause, lineOutputChannel)
        }
        delay(100)

        try {
            kotlinScriptRunner.runCode(KotlinScriptSamples.helloWorld, lineOutputChannel)
            fail("Should throw ProgramAlreadyRunningException")
        } catch (_: KotlinScriptRunner.AlreadyRunningException) {}

        helloWorldPauseJob.cancelAndJoin()
    }

    private suspend fun Channel<String>.joinToString(): String {
        return this.consumeAsFlow().toList().joinToString()
    }

}
