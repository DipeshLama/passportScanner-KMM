package com.example.passportscannerkmm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.passportscannerkmm.features.Features
import com.example.passportscannerkmm.theme.DemoTheme
import com.example.passportscannerkmm.ui.HomeScreen

/** Demo entry point: a home screen listing [Features]; selecting one opens its screen. */
@Composable
fun App() {
    DemoTheme {
        var openId by rememberSaveable { mutableStateOf<String?>(null) }
        val open = Features.firstOrNull { it.id == openId }

        if (open == null) {
            HomeScreen(features = Features, onOpen = { openId = it.id })
        } else {
            open.Content(onBack = { openId = null })
        }
    }
}
