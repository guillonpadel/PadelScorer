package com.padel.scorer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: PadelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Inicializar TTS y voz ──────────────
        viewModel.initTts(this)
        viewModel.initSpeechRecognizer(this)

        // ── Permiso de micrófono ───────────────
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        // ── UI ────────────────────────────────
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PadelScorerScreen(uiState = uiState)
        }

        // Arrancar setup por voz
        viewModel.startVoiceSetup()
    }

    // ─────────────────────────────────────────
    //  CLAVE: interceptar teclas ANTES de que
    //  el sistema las use para volumen
    // ─────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Si el ViewModel consume la tecla, no la propagamos al sistema
        if (viewModel.onKeyEvent(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    // Evitar que VOLUME_UP/DOWN muestren el control de volumen del sistema
    // cuando el partido está en curso
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                          event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        val phase = viewModel.uiState.value.setupPhase
        val matchInProgress = phase == SetupPhase.IN_PROGRESS || phase == SetupPhase.READY

        if (isVolumeKey && matchInProgress) {
            // Procesar solo en ACTION_DOWN (ignorar ACTION_UP para no duplicar)
            if (event.action == KeyEvent.ACTION_DOWN) {
                viewModel.onKeyEvent(event.keyCode)
            }
            return true  // consumido: el sistema NO muestra el slider de volumen
        }

        return super.dispatchKeyEvent(event)
    }
}
