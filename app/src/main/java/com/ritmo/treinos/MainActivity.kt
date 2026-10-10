package com.ritmo.treinos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ritmo.treinos.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var model: RitmoViewModel
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); if(::model.isInitialized) intent.data?.let(model::handleRecovery); intent.data=null }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RitmoApplication
        model = androidx.lifecycle.ViewModelProvider(this, RitmoViewModel.factory(app))[RitmoViewModel::class.java]
        intent.data?.let(model::handleRecovery)
        intent.data=null
        setContent { val vm: RitmoViewModel = viewModel(factory = RitmoViewModel.factory(application as RitmoApplication)); RitmoApp(vm) }
    }
}
