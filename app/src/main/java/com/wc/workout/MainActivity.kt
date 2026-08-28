package com.wc.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.wc.workout.ui.WorkoutRoot
import com.wc.workout.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val container = (applicationContext as WorkoutApp).container
                    WorkoutRoot(container)
                }
            }
        }
    }
}
