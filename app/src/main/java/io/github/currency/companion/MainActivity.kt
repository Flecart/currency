package io.github.currency.companion

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.currency.companion.ui.CurrencyApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by viewModels<CurrencyViewModel>()
    private val pickBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) lifecycleScope.launch { model.connect(uri) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.refreshLoop() } }
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            CurrencyApp(state, onChooseFile = { pickBackup.launch(arrayOf("*/*")) }, onOpenStt = model::openStt)
        }
    }
}
