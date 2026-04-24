package com.tosin.docprocessor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.by
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tosin.docprocessor.ui.theme.TDocTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TDocTheme {
                EditorScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    // In MVP, we keep text state here for now. Later it moves to ViewModel.
    var textState by remember { mutableStateOf("Welcome to TDoc. Start typing...") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TDoc Processor") },
                actions = {
                    IconButton(onClick = { /* TODO: Save logic */ }) {
                        Icon(imageVector = Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        TextField(
            value = textState,
            onValueChange = { textState = it },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding(), // Ensures keyboard doesn't hide text
            placeholder = { Text("Type here...") }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {
    TDocTheme {
        EditorScreen()
    }
}