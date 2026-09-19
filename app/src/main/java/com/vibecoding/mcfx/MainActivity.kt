package com.vibecoding.mcfx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibecoding.mcfx.ui.ConverterScreen
import com.vibecoding.mcfx.ui.ConverterViewModel
import com.vibecoding.mcfx.ui.McfxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge so the forest hero block extends under the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            McfxTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    val vm: ConverterViewModel = viewModel()
                    ConverterScreen(vm)
                }
            }
        }
    }
}
