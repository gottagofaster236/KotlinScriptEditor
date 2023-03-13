package com.lr_soft.kotlin_script_editor.model

data class CompilationError(
    val errorText: String,
    val sourceCodePosition: Int
) {
    companion object {
        const val NO_SOURCE_CODE_POSITION = -1
    }
}