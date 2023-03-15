package com.lr_soft.kotlin_script_editor.model

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class KotlinScriptRunner(private val codeSaveFile: File) {

    /**
     * Make sure we don't run the two programs at the same time, as we use the same [codeSaveFile].
     */
    private val isRunning = AtomicBoolean(false)

    private var hadStdoutOutput = false

    private val stderrOutput = StringBuilder()

    suspend fun runCode(
        code: String,
        outputChannel: Channel<String>
    ): Int = withContext(Dispatchers.IO) {
        if (!isRunning.compareAndSet(false, true)) {
            throw AlreadyRunningException()
        }

        var kotlinProcess: Process? = null
        try {
            codeSaveFile.writeText(code)
            if (isWindows()) {
                kotlinProcess = ProcessBuilder("cmd", "/c", "kotlinc -script \"${codeSaveFile.absolutePath}\"").start()
            } else {
                kotlinProcess = ProcessBuilder("kotlinc", "-script", codeSaveFile.absolutePath).start()
            }
            interactWithKotlinProcess(kotlinProcess, outputChannel, code)
        } finally {
            kotlinProcess?.let {
                kill(it.toHandle())
            }
            isRunning.set(false)
            outputChannel.close()
        }
    }

    private suspend fun interactWithKotlinProcess(
        kotlinProcess: Process,
        outputChannel: Channel<String>,
        code: String
    ) = withContext(Dispatchers.IO) {
        stderrOutput.clear()

        val outputForwardJob = launch {
            hadStdoutOutput = false
            readStreamInChunks(
                process = kotlinProcess,
                stream = kotlinProcess.inputStream,
                onNextChunk = {
                    outputChannel.send(it)
                    hadStdoutOutput = true
                }
            )
        }

        val errorForwardJob = launch {
            readStreamInChunks(
                process = kotlinProcess,
                stream = kotlinProcess.errorStream,
                onNextChunk = {
                    outputChannel.send(it)
                    stderrOutput.append(it)
                }
            )
        }

        val returnCode = runInterruptible { kotlinProcess.waitFor() }
        outputForwardJob.join()
        errorForwardJob.join()
        checkForCompilationErrors(kotlinProcess, code)
        returnCode
    }

    private suspend fun readStreamInChunks(
        process: Process,
        stream: InputStream,
        onNextChunk: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        stream.bufferedReader().use { bufferedReader ->
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
                    onNextChunk(nextChunk.toString())
                } catch (_: IOException) {
                    break
                }
            }
        }
    }

    private fun checkForCompilationErrors(kotlinProcess: Process, code: String) {
        if (kotlinProcess.exitValue() != 1) {
            return
        }
        if (hadStdoutOutput) {
            return
        }
        val stderrLines = stderrOutput.toString().lines()
        if (stderrLines.isEmpty()) {
            return
        }
        parseAndThrowCompilationErrors(code, stderrLines)
    }

    private fun parseAndThrowCompilationErrors(code: String, stderrLines: List<String>) {
        val errors = mutableListOf<CompilationError>()
        val codeLineStartPositions = getLineStartPositions(code)
        val currentErrorText = StringBuilder()
        var currentSourceCodeLineNumber = CompilationError.NO_POSITION
        var currentSourceCodePosition = CompilationError.NO_POSITION

        // Iterating up to `stderrLines.size` to add the last portion of lines.
        for (lineIndex in 0..stderrLines.size) {
            val stderrLine: String? = stderrLines.getOrNull(lineIndex)
            val isNewError = stderrLine?.contains("${codeSaveFile.name}:") != false
            if (isNewError && currentErrorText.isNotEmpty()) {
                // Add the previous error.
                errors.add(
                    CompilationError(
                        currentErrorText.toString(),
                        currentSourceCodeLineNumber,
                        currentSourceCodePosition
                    )
                )
            }
            if (stderrLine == null) {
                break
            }
            if (isNewError) {
                currentErrorText.clear()
                val lineAndPosition = getErrorSourceCodeLineAndPosition(stderrLine, codeLineStartPositions)
                currentSourceCodeLineNumber = lineAndPosition.first
                currentSourceCodePosition = lineAndPosition.second
            }

            if (currentErrorText.isNotEmpty()) {
                currentErrorText.append('\n')
            }
            currentErrorText.append(stderrLine)
        }

        throw CompilationFailedException(errors)
    }

    private fun getErrorSourceCodeLineAndPosition(
        stderrLine: String,
        codeLineStartPositions: List<Int>
    ): Pair<Int, Int> {
        try {
            // Kotlin compiler errors are in format "{filePath}:{lineNumber}:{linePosition}: error: ...".
            val (lineNumber, linePosition) = stderrLine
                .split(":", limit = 4)
                .subList(1, 3)
                .map(String::toInt)
                .map { i -> i - 1 }  // Convert to 0-indexed.

            return lineNumber to codeLineStartPositions[lineNumber] + linePosition
        } catch (_: IndexOutOfBoundsException) {
        } catch (_: NumberFormatException) {}

        return CompilationError.NO_POSITION to CompilationError.NO_POSITION
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

    // https://stackoverflow.com/a/10124625/6120487
    private fun kill(handle: ProcessHandle) {
        handle.descendants().forEach { child -> kill(child) }
        handle.destroy()
    }
    
    private fun isWindows(): Boolean {
        return System.getProperty("os.name").startsWith("Windows")
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
