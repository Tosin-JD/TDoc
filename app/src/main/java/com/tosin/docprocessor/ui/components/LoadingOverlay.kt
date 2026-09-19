package com.tosin.docprocessor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Thin determinate progress bar for in-place loading (parse/save) plus an
 * optional centered spinner for full-screen loads. [visible] is animated so
 * accessibility live-regions announce the change rather than every frame.
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    fullScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!fullScreen) {
        AnimatedVisibility(visible = visible, modifier = modifier) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        return
    }
    AnimatedVisibility(visible = visible, modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}