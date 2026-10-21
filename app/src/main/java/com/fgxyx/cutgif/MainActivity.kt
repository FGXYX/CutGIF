package com.fgxyx.cutgif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fgxyx.cutgif.ui.CutGifApp
import com.fgxyx.cutgif.ui.theme.CutGifTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                0xFFFEF7FF.toInt(),
                0xFFFEF7FF.toInt(),
            ),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CutGifTheme {
                CutGifApp()
            }
        }
    }
}
