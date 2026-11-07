package com.tosin.docprocessor.ui.help

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tosin.docprocessor.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpFeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Help & Feedback") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Opening a document", style = MaterialTheme.typography.titleMedium)
            Text("Use Open to choose a DOCX, ODT, or text document. Recent keeps a short list of files you have opened.", Modifier.padding(top = 6.dp))
            Text("Folders and removable storage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            Text("Android only shows folders you select and grant access to. If a card or USB drive is unavailable, reconnect it and select its folder again.", Modifier.padding(top = 6.dp))
            Button(onClick = {
                val address = context.getString(R.string.feedback_email)
                val intent = Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()).putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
                runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, context.getString(R.string.feedback_fallback, address), Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.padding(top = 24.dp)) { Text("Send feedback") }
        }
    }
}
