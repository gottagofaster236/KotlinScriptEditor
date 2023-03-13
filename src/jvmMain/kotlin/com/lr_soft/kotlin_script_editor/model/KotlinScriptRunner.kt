package com.lr_soft.kotlin_script_editor.model

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.IOException
import java.lang.IndexOutOfBoundsException
import java.lang.NumberFormatException
import java.lang.StringBuilder
import java.util.concurrent.atomic.AtomicBoolean

class KotlinScriptRunner(private val codeSaveFile: File) {

    /**
     * Make sure we don't run the two programs at the same time, as we use the same [codeSaveFile].
     */
    private val isRunning = AtomicBoolean(false)

    private val hadStdoutOutput = AtomicBoolean(false)

    private val codeSaveAbsolutePath: String by lazy {
        codeSaveFile.absolutePath
    }

    suspend fun runCode(
        code: String,
        lineOutputChannel: Channel<String>
    ): Int = withContext(Dispatchers.IO) {
        if (!isRunning.compareAndSet(false, true)) {
            throw AlreadyRunningException()
        }

        var kotlinProcess: Process? = null
        try {
            codeSaveFile.writeText(code)
            kotlinProcess = ProcessBuilder("kotlinc", "-script", codeSaveAbsolutePath).start()
            interactWithKotlinProcess(kotlinProcess, lineOutputChannel, code)
        } finally {
            // Make sure the input and output streams are properly closed.
            kotlinProcess?.destroyForcibly()
            isRunning.set(false)
            lineOutputChannel.close()
        }
    }

    private suspend fun interactWithKotlinProcess(
        kotlinProcess: Process,
        lineOutputChannel: Channel<String>,
        code: String
    ) = withContext(Dispatchers.IO) {
        val outputForwardJob = launch {
            forwardOutputToChannel(kotlinProcess, lineOutputChannel)
        }
        val returnCode = runInterruptible { kotlinProcess.waitFor() }
        outputForwardJob.join()
        checkForCompilationErrors(kotlinProcess, code)
        returnCode
    }

    private suspend fun forwardOutputToChannel(
        process: Process,
        lineOutputChannel: Channel<String>
    ) = withContext(Dispatchers.IO) {
        hadStdoutOutput.set(false)
        process.inputStream.bufferedReader().use { bufferedReader ->
            while (true) {
                try {
                    while (!bufferedReader.ready() && process.isAlive) {
                        // A direct call to `readLine()` will block indefinitely if we terminate the Kotlin process.
                        delay(INPUT_STREAM_CHECK_PERIODICITY_MS)
                    }
                    val line = bufferedReader.readLine() ?: break
                    hadStdoutOutput.set(true)
                    lineOutputChannel.send(line + "\n")
                } catch (_: IOException) {
                    break
                }
            }
        }
    }

    private fun checkForCompilationErrors(
        kotlinProcess: Process,
        code: String
    ) {
        if (kotlinProcess.exitValue() != 1) {
            return
        }
        if (hadStdoutOutput.get()) {
            return
        }
        val stderrLines = kotlinProcess.errorStream.bufferedReader().use {
            it.readLines()
        }
        if (stderrLines.isEmpty()) {
            return
        }
        parseAndThrowCompilationErrors(code, stderrLines)
    }

    private fun parseAndThrowCompilationErrors(
        code: String,
        stderrLines: List<String>
    ) {
        val errors = mutableListOf<CompilationError>()
        val codeLineStartPositions = getLineStartPositions(code)
        val currentErrorText = StringBuilder()
        var currentSourceCodePosition = CompilationError.NO_SOURCE_CODE_POSITION

        // Iterating up to `stderrLines.size` to add the last portion of lines.
        for (lineIndex in 0..stderrLines.size) {
            val stderrLine: String? = stderrLines.getOrNull(lineIndex)
            val newError = stderrLine?.startsWith(codeSaveAbsolutePath) != false
            if (newError && currentErrorText.isNotEmpty()) {
                errors.add(CompilationError(currentErrorText.toString(), currentSourceCodePosition))
            }
            if (stderrLine == null) {
                break
            }
            if (newError) {
                currentErrorText.clear()
                currentSourceCodePosition = getErrorSourceCodePosition(stderrLine, codeLineStartPositions)
            }

            if (currentErrorText.isNotEmpty()) {
                currentErrorText.append('\n')
            }
            currentErrorText.append(stderrLine)
        }

        throw CompilationFailedException(errors)
    }

    private fun getErrorSourceCodePosition(
        stderrLine: String,
        codeLineStartPositions: List<Int>
    ): Int {
        try {
            // Kotlin compiler errors are in format "{filePath}:{lineNumber}:{linePosition}: error: ...".
            val (lineNumber, linePosition) = stderrLine
                .substring(codeSaveAbsolutePath.length + 1)
                .split(":", limit = 3)
                .take(2)
                .map(String::toInt)
                .map { i -> i - 1 }  // Convert to 0-indexed indexes.

            return codeLineStartPositions[lineNumber] + linePosition
        } catch (_: IndexOutOfBoundsException) {
        } catch (_: NumberFormatException) {}

        return CompilationError.NO_SOURCE_CODE_POSITION
    }

    private fun getLineStartPositions(code: String): List<Int> {
        val result = mutableListOf(0)
        for ((index, char) in code.withIndex()) {
            if (char == '\n') {
                result.add(index + 1)
            }
        }
        return result
    }

    class CompilationFailedException(
        val compilationErrors: List<CompilationError>
    ) : Exception("Compilation error")

    class AlreadyRunningException : Exception("Already running")

    private companion object {
        const val INPUT_STREAM_CHECK_PERIODICITY_MS = 10L
    }
}
