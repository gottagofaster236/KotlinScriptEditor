package com.lr_soft.kotlin_script_editor.model

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class KotlinScriptRunner(private val codeSaveFile: File) {

    /**
     * Make sure we don't run the two programs at the same time, as we use the same [codeSaveFile].
     */
    private val isRunning = AtomicBoolean(false)

    private val hadStdoutOutput = AtomicBoolean(false)

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
            kotlinProcess = ProcessBuilder("kotlinc", "-script", codeSaveFile.absolutePath).start()
            interactWithKotlinProcess(kotlinProcess, lineOutputChannel, code)
        } finally {
            if (kotlinProcess != null) {
                stopKotlinProcess(kotlinProcess)
            }
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
            fun canReadNextSymbol(): Boolean {
                // A direct call to `readLine()` will block indefinitely if we terminate the Kotlin process.
                return bufferedReader.ready() || !process.isAlive
            }

            while (true) {
                try {
                    while (!canReadNextSymbol()) {
                        delay(INPUT_STREAM_CHECK_PERIODICITY_MS)
                    }
                    val nextChunk = StringBuilder()
                    while (canReadNextSymbol() && nextChunk.length < MAX_CHUNK_LENGTH) {
                        val nextChar = bufferedReader.read()
                        if (nextChar == -1) {
                            return@use
                        }
                        nextChunk.append(nextChar.toChar())
                    }
                    hadStdoutOutput.set(true)
                    lineOutputChannel.send(nextChunk.toString())
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
            val newError = stderrLine?.contains("${codeSaveFile.name}:") != false
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
                .split(":", limit = 4)
                .drop(1)
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

    private fun stopKotlinProcess(kotlinProcess: Process) {
        if (!System.getProperty("os.name").startsWith("Windows") && kotlinProcess.isAlive) {
            /**
             * Assume we're on POSIX. For some reason, destroyForcibly() doesn't kill
             * the child Java process, so we have to do it ourselves.
             */
            Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "kill \$(ps -o pid= --ppid ${kotlinProcess.pid()})")
            ).waitFor()
        }
        // Make sure the input and output streams are properly closed.
        kotlinProcess.destroyForcibly()
    }

    class CompilationFailedException(
        val compilationErrors: List<CompilationError>
    ) : Exception("Compilation error")

    class AlreadyRunningException : Exception("Already running")

    private companion object {
        const val INPUT_STREAM_CHECK_PERIODICITY_MS = 10L
        const val MAX_CHUNK_LENGTH = 65536
    }
}
