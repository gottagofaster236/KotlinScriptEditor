package com.lr_soft.kotlin_script_editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lr_soft.kotlin_script_editor.model.CompilationError

@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    EditorScreen(
        uiState = viewModel.uiState,
        onEditorTextUpdated = viewModel::onEditorTextUpdated,
        runOrStopProgram = viewModel::runOrStopProgram,
        onErrorClicked = viewModel::onErrorClicked,
    )
}

@Composable
fun EditorScreen(
    uiState: EditorUiState,
    onEditorTextUpdated: (TextFieldValue) -> Unit,
    runOrStopProgram: () -> Unit,
    onErrorClicked: (CompilationError) -> Unit,
) {
    Scaffold(
        topBar = {
            EditorTopBar(
                isProgramRunning = uiState.isProgramRunning,
                runOrStopProgram = runOrStopProgram
            )
        }
    ) { innerPaddingModifier ->
        EditorScreenContent(
            uiState = uiState,
            onEditorTextUpdated = onEditorTextUpdated,
            onErrorClicked = onErrorClicked,
            modifier = Modifier.padding(innerPaddingModifier)
        )
    }
}

@Composable
fun EditorScreenContent(
    uiState: EditorUiState,
    onEditorTextUpdated: (TextFieldValue) -> Unit,
    onErrorClicked: (CompilationError) -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(2f)
        ) {
            CodePanel(
                textFieldValue = uiState.editorTextFieldValue,
                onEditorTextUpdated = onEditorTextUpdated,
                timesEditorFocusRequested = uiState.timesEditorFocusRequested,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                OutputPanel(
                    text = uiState.outputText,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                LastReturnCodePanel(lastReturnCode = uiState.lastReturnCode)
            }
        }

        ErrorsPanel(
            errorsList = uiState.errorList,
            onErrorClicked = onErrorClicked,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
fun CodePanel(
    textFieldValue: TextFieldValue,
    onEditorTextUpdated: (TextFieldValue) -> Unit,
    timesEditorFocusRequested: Int,
    modifier: Modifier
) {
    EditorPanelContainer(
        title = "Code",
        modifier = modifier
    ) {
        val focusRequester = remember { FocusRequester() }
        TextField(
            value = textFieldValue,
            onValueChange = onEditorTextUpdated,
            textStyle = TextStyle.Default.copy(fontFamily = FontFamily.Monospace, fontSize = 17.sp),
            modifier = Modifier.fillMaxSize().padding(5.dp).focusRequester(focusRequester)
        )
        LaunchedEffect(timesEditorFocusRequested) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun OutputPanel(
    text: String,
    modifier: Modifier
) {
    EditorPanelContainer(
        title = "Output",
        modifier = modifier
    ) {
        SelectionContainer {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
                    .background(editorBackgroundColor())
            )
        }
    }
}

@Composable
private fun LastReturnCodePanel(lastReturnCode: Int) {
    EditorPanelContainer(
        title = "Last return code: $lastReturnCode",
        modifier = Modifier.fillMaxWidth(),
        content = {}
    )
}

@Composable
fun ErrorsPanel(
    errorsList: List<CompilationError>,
    onErrorClicked: (CompilationError) -> Unit,
    modifier: Modifier
) {
    EditorPanelContainer(
        title = "Compilation errors",
        modifier = modifier
    ) {
        Column(
            modifier = modifier.fillMaxSize().padding(5.dp)
        ) {
            Text(
                text = "${errorsList.size} error" +
                        (if (errorsList.size != 1) "s" else "") +
                        (if (errorsList.isNotEmpty()) ":" else ""),
                modifier = Modifier.padding(5.dp),
                fontSize = 17.sp
            )

            Divider()

            LazyColumn(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = errorsList,
                    itemContent = { item: CompilationError ->
                        Card(
                            modifier = modifier
                                .fillMaxWidth()
                                .clickable { onErrorClicked(item) },
                            elevation = 5.dp,
                            backgroundColor = editorBackgroundColor()
                        ) {
                            Text(
                                text = item.errorText,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun editorBackgroundColor(): Color {
    val color1 = MaterialTheme.colors.onSurface
    val percent1 = 0.12f
    val color2 = MaterialTheme.colors.surface
    val percent2 = 1 - percent1
    return Color(
        red = color1.red * percent1 + color2.red * percent2,
        green = color1.green * percent1 + color2.green * percent2,
        blue = color1.blue * percent1 + color2.blue * percent2
    )
}

@Composable
fun EditorTopBar(
    isProgramRunning: Boolean,
    runOrStopProgram: () -> Unit
) {
    val runStopIcon = if (isProgramRunning) {
        Icons.Default.Stop
    } else {
        Icons.Default.PlayArrow
    }
    TopAppBar(
        title = {
            Text("Kotlin Script Editor")
        },
        actions = {
            val sizeModifier = Modifier.size(35.dp)
            IconButton(
                onClick = runOrStopProgram,
                modifier = sizeModifier
            ) {
                Icon(
                    imageVector = runStopIcon,
                    contentDescription = "Start or stop the script",
                    modifier = sizeModifier,
                    tint = MaterialTheme.colors.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
        }
    )
}

@Composable
fun EditorPanelContainer(
    title: String,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.padding(10.dp),
        elevation = 15.dp
    ) {
        Column {
            Text(
                text = title,
                modifier = Modifier.background(MaterialTheme.colors.primary).fillMaxWidth().padding(6.dp),
                color = MaterialTheme.colors.onPrimary,
                fontSize = 17.sp
            )
            content()
        }
    }
}
