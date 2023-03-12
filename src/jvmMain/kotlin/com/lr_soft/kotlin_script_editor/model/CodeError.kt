package com.lr_soft.kotlin_script_editor.model

data class CodeError(
    val errorText: String,
    val sourceLine: Int
)
