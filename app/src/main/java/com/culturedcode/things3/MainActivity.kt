package com.culturedcode.things3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.culturedcode.things3.ui.screens.ThingsSmartViewScreen
import com.culturedcode.things3.ui.theme.Things3Theme
import com.culturedcode.things3.ui.viewmodel.ThingsbookViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Things3Theme {
                val viewModel: ThingsbookViewModel = hiltViewModel()
                ThingsSmartViewScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
