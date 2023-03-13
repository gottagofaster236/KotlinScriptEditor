package com.lr_soft.kotlin_script_editor.model

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class KotlinScriptRunner(
    private val codeSaveLocation: File
) {

    // Make sure we don't run the two programs at the same time, as we use the same save file.
    private val isRunning = AtomicBoolean(false)

    suspend fun runCode(code: String, lineOutputChannel: Channel<String>): Int = withContext(Dispatchers.IO) {
        if (!isRunning.compareAndSet(false, true)) {
            throw AlreadyRunningException()
        }
        try {
            codeSaveLocation.writeText(code)
            val process = ProcessBuilder("kotlinc", "-script", codeSaveLocation.absolutePath).start()
            var outputForwardJob: Job? = null
            var returnCode = 0
            try {
                outputForwardJob = launch {
                    forwardOutputToChannel(process, lineOutputChannel)
                }
                returnCode = runInterruptible { process.waitFor() }
                // Make sure outputForwardJob catches up to the output.
                delay(PROGRAM_FINISH_DELAY_MS)
            } catch (_: CancellationException) {
                process.destroyForcibly()
            }
            outputForwardJob?.cancel()
            lineOutputChannel.close()
            returnCode
        } finally {
            isRunning.set(false)
        }
    }

    private suspend fun forwardOutputToChannel(
        process: Process,
        lineOutputChannel: Channel<String>
    ) = withContext(Dispatchers.IO) {
        val inputStream = process.inputStream
        val bufferedReader = inputStream.bufferedReader()
        while (true) {
            try {
                while (inputStream.available() == 0) {
                    delay(INPUT_STREAM_CHECK_PERIODITY_MS)
                }
                val line = bufferedReader.readLine()
                lineOutputChannel.send(line + "\n")
            } catch (_: IOException) {
                break
            }
        }
    }

    class CompilationFailedException(
        val compilationErrors: List<CompilationError>
    ) : Exception("Compilation error")

    class AlreadyRunningException : Exception("Already running")

    private companion object Delays {
        const val INPUT_STREAM_CHECK_PERIODITY_MS = 10L
        const val PROGRAM_FINISH_DELAY_MS = 100L
    }
}
