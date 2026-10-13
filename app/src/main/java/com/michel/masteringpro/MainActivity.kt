package com.michel.masteringpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.michel.masteringpro.ui.MichelApp
import com.michel.masteringpro.ui.theme.MichelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MichelTheme {
                MichelApp()
            }
        }
    }
}
