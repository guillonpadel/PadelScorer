package com.padel.scorer

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

// ─────────────────────────────────────────────
//  Estado de UI
// ─────────────────────────────────────────────

data class UiState(
    val matchState: MatchState = MatchState(),
    val isListening: Boolean = false,
    val voicePrompt: String = "",        // texto que guía al usuario en setup
    val setupPhase: SetupPhase = SetupPhase.NOT_STARTED,
    val isSpeaking: Boolean = false,
    val errorMessage: String? = null
)

enum class SetupPhase {
    NOT_STARTED,
    WAITING_TEAM_A,   // "Decí el nombre de la pareja A"
    WAITING_TEAM_B,
    READY,
    IN_PROGRESS
}

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

class PadelViewModel : ViewModel() {

    private val engine = PadelMatchEngine(goldenPoint = false)

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false

    // ── Inicialización ─────────────────────────

    fun initTts(context: Context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                isTtsReady = true

                // Callback para saber cuándo termina de hablar
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(isSpeaking = true)
                    }
                    override fun onDone(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(isSpeaking = false)
                    }
                    override fun onError(utteranceId: String?) {
                        _uiState.value = _uiState.value.copy(isSpeaking = false)
                    }
                })
            }
        }
    }

    fun initSpeechRecognizer(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Reconocimiento de voz no disponible en este dispositivo."
            )
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(buildRecognitionListener())
    }

    // ── Setup del partido por voz ──────────────

    fun startVoiceSetup() {
        _uiState.value = _uiState.value.copy(
            setupPhase = SetupPhase.WAITING_TEAM_A,
            voicePrompt = "Decí el nombre de la pareja A"
        )
        speak("Bienvenido al marcador de pádel. Decí el nombre de la pareja A.")
    }

    private fun onVoiceResult(text: String) {
        val phase = _uiState.value.setupPhase
        val trimmed = text.trim()

        when (phase) {
            SetupPhase.WAITING_TEAM_A -> {
                engine.setTeamNames(trimmed, "Pareja B")
                _uiState.value = _uiState.value.copy(
                    matchState = engine.currentState(),
                    setupPhase = SetupPhase.WAITING_TEAM_B,
                    voicePrompt = "Decí el nombre de la pareja B"
                )
                speak("Pareja A: $trimmed. Ahora decí el nombre de la pareja B.")
            }
            SetupPhase.WAITING_TEAM_B -> {
                val currentA = engine.currentState().teamA
                val event = engine.setTeamNames(currentA, trimmed)
                _uiState.value = _uiState.value.copy(
                    matchState = event.newState,
                    setupPhase = SetupPhase.READY,
                    voicePrompt = ""
                )
                speak("Pareja B: $trimmed. ${event.ttsText}")
            }
            else -> { /* noop */ }
        }
    }

    fun startListening() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
        _uiState.value = _uiState.value.copy(isListening = true)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    // ── Control de puntos ──────────────────────

    fun addPointTeamA() = applyEvent(engine.addPoint(Team.A))
    fun addPointTeamB() = applyEvent(engine.addPoint(Team.B))
    fun undoLastPoint() = applyEvent(engine.undoLastPoint())

    private fun applyEvent(event: MatchEvent) {
        _uiState.value = _uiState.value.copy(
            matchState = event.newState,
            setupPhase = if (_uiState.value.setupPhase == SetupPhase.READY)
                SetupPhase.IN_PROGRESS
            else _uiState.value.setupPhase
        )
        speak(event.ttsText)
    }

    // ── Manejo del botón selfie (Bluetooth) ────
    //
    //  Un botón selfie se registra como KeyEvent. Los más comunes:
    //    KEYCODE_VOLUME_UP   → punto pareja A
    //    KEYCODE_VOLUME_DOWN → punto pareja B
    //    KEYCODE_CAMERA / KEYCODE_MEDIA_PLAY_PAUSE → deshacer
    //
    //  En tu Activity/Composable, intercepta onKeyDown y llamá este método:

    fun onKeyEvent(keyCode: Int): Boolean {
        val phase = _uiState.value.setupPhase
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (phase == SetupPhase.IN_PROGRESS || phase == SetupPhase.READY) {
                    addPointTeamA(); true
                } else false
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (phase == SetupPhase.IN_PROGRESS || phase == SetupPhase.READY) {
                    addPointTeamB(); true
                } else false
            }
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (phase == SetupPhase.IN_PROGRESS) {
                    undoLastPoint(); true
                } else if (phase == SetupPhase.READY || phase == SetupPhase.WAITING_TEAM_A
                    || phase == SetupPhase.WAITING_TEAM_B) {
                    startListening(); true
                } else false
            }
            // DPAD center en mandos Android TV
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (phase == SetupPhase.WAITING_TEAM_A || phase == SetupPhase.WAITING_TEAM_B) {
                    startListening(); true
                } else false
            }
            else -> false
        }
    }

    // ── TTS ────────────────────────────────────

    private fun speak(text: String) {
        if (!isTtsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "padel_tts_${System.currentTimeMillis()}")
    }

    // ── Recognition Listener ───────────────────

    private fun buildRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: return
            _uiState.value = _uiState.value.copy(isListening = false)
            onVoiceResult(text)
        }

        override fun onError(error: Int) {
            _uiState.value = _uiState.value.copy(isListening = false)
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No entendí. Intentá de nuevo."
                SpeechRecognizer.ERROR_NETWORK -> "Sin conexión de red para voz."
                SpeechRecognizer.ERROR_AUDIO -> "Error de audio."
                else -> "Error de reconocimiento ($error)."
            }
            speak(msg)
        }

        override fun onEndOfSpeech() {
            _uiState.value = _uiState.value.copy(isListening = false)
        }
    }

    // ── Limpieza ───────────────────────────────

    override fun onCleared() {
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onCleared()
    }
}
