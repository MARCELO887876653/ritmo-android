package com.ritmo.treinos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ritmo.treinos.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { val vm: RitmoViewModel = viewModel(factory = RitmoViewModel.factory(application as RitmoApplication)); RitmoApp(vm) }
    }
}
