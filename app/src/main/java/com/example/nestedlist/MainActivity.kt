package com.example.nestedlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier

/**
 * Single-screen host for the accessibility demo.
 *
 * Responsibilities are intentionally tiny: set up Material 3 theming, draw a
 * Scaffold so system bar insets are respected, make the content scrollable for
 * small screens, and render [NestedAccessibleListScreen]. All of the
 * accessibility behavior lives in NestedAccessibleList.kt.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars; Scaffold's inner padding keeps the
        // content clear of them.
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NestedAccessibleListScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
