package com.lr_soft.kotlin_script_editor.model

object KotlinScriptSamples {

    const val helloWorld = """println("Hello world!")"""
    const val helloWorldWithoutNewline = """print("Hello world!")"""

    const val compilationError = """hello()
world()"""
    const val returnCode123 = """System.exit(123)"""
    const val helloWorldPause = """println("Hello"); Thread.sleep(10000); println("world")"""
}
