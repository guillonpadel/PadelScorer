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

        // Tomar control del volumen para que el sistema no lo intercepte
        volumeControlStream = AudioManager.STREAM_MUSIC

        viewModel.initTts(this)
        viewModel.initSpeechRecognizer(this)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PadelScorerScreen(uiState = uiState)
        }
        viewModel.startVoiceSetup()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                          keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKey) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                viewModel.onKeyEvent(keyCode)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.onKeyEvent(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }
}
