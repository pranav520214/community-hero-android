package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.repository.FirebaseService
import com.example.ui.screens.MainScreen
import com.example.ui.theme.CivicDexTheme
import com.example.ui.viewmodel.CivicViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CivicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase SDK safely with dynamic fallback support
        FirebaseService.initialize(applicationContext)

        enableEdgeToEdge()
        setContent {
            CivicDexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
