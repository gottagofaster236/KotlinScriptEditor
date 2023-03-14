package com.lr_soft.kotlin_script_editor.model

data class CompilationError(
    val errorText: String,
    val sourceCodeLineNumber: Int,
    val sourceCodePosition: Int
) {
    companion object {
        const val NO_POSITION = -1
    }
}