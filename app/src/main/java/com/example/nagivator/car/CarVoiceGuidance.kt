package com.example.nagivator.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.nagivator.data.repository.UserSettingsRepository
import com.example.nagivator.data.repository.VoiceSpeechRate
import com.example.nagivator.domain.NavigationStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class CarVoiceGuidance(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val settingsRepository = UserSettingsRepository.getInstance(appContext)
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isReady = false
    private var isMuted = NavigationStateHolder.isMuted.value
    private var lastSpokenText: String = ""
    private var pendingPrompt: String? = null
    private val utteranceCounter = AtomicInteger(0)

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _activeVoiceName = MutableStateFlow<String?>(null)
    val activeVoiceName: StateFlow<String?> = _activeVoiceName.asStateFlow()

    private var voiceMapByName: Map<String, Voice> = emptyMap()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener {}
        .build()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setAudioAttributes(audioAttributes)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    NavigationStateHolder.setSpeaking(true)
                }

                override fun onDone(utteranceId: String?) {
                    NavigationStateHolder.setSpeaking(false)
                    abandonFocus()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    NavigationStateHolder.setSpeaking(false)
                    abandonFocus()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    NavigationStateHolder.setSpeaking(false)
                    abandonFocus()
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (!interrupted) {
                        NavigationStateHolder.setSpeaking(false)
                        abandonFocus()
                    }
                }
            })

            val result = tts?.setLanguage(Locale.GERMAN)
            isReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

            if (isReady) {
                refreshAvailableVoices()
                val settings = settingsRepository.settings.value
                applyVoiceSettings(settings.voiceName, settings.voiceSpeechRate)
            }

            pendingPrompt?.let { queued ->
                pendingPrompt = null
                if (isReady) {
                    speakInternal(queued, queueMode = TextToSpeech.QUEUE_FLUSH)
                }
            }
        }
    }

    /**
     * Scans the current Android TTS engine for installed German offline voices
     * and publishes them sorted by quality.
     */
    fun refreshAvailableVoices(): List<TtsVoiceOption> {
        val engine = tts ?: return _availableVoices.value
        val platformVoices = try {
            engine.voices.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not query TTS voices", e)
            emptySet()
        }

        val rawVoices = platformVoices.map { voice ->
            val features = voice.features ?: emptySet()
            RawTtsVoice(
                name = voice.name,
                language = voice.locale?.language.orEmpty(),
                country = voice.locale?.country.orEmpty(),
                quality = voice.quality,
                latency = voice.latency,
                isNetworkRequired = voice.isNetworkConnectionRequired,
                isNotInstalled = features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            )
        }

        val ranked = OfflineVoiceSelector.filterAndRankGermanOfflineVoices(rawVoices)
        voiceMapByName = platformVoices.associateBy { it.name }
        _availableVoices.value = ranked

        Log.i(
            TAG,
            "Detected ${ranked.size} offline German TTS voices: ${
                ranked.joinToString { "${it.name}(q=${it.qualityScore})" }
            }"
        )
        return ranked
    }

    /**
     * Reinitializes the Android TTS engine (e.g. after returning from Android's
     * system voice pack installer) so newly downloaded offline voices appear immediately.
     */
    fun reloadEngine() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        isReady = false
        tts = TextToSpeech(appContext, this)
    }

    /**
     * Applies the user's preferred offline voice (or best available offline German HD voice
     * when [preferredVoiceName] is null) and speech rate.
     */
    fun applyVoiceSettings(
        preferredVoiceName: String?,
        speechRate: VoiceSpeechRate
    ) {
        val engine = tts ?: return
        if (!isReady) return

        try {
            engine.setSpeechRate(speechRate.rate)
            engine.setPitch(1.0f)

            if (_availableVoices.value.isEmpty()) {
                refreshAvailableVoices()
            }

            val resolvedName = OfflineVoiceSelector.resolveVoiceName(
                rankedVoices = _availableVoices.value,
                preferredVoiceName = preferredVoiceName
            )
            val targetVoice = resolvedName?.let { voiceMapByName[it] }
            if (targetVoice != null) {
                val res = engine.setVoice(targetVoice)
                if (res == TextToSpeech.SUCCESS) {
                    _activeVoiceName.value = targetVoice.name
                    return
                }
            }
            engine.setLanguage(Locale.GERMAN)
            _activeVoiceName.value = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply TTS voice settings", e)
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!isReady || isMuted || text.isBlank()) return
        if (!force && text == lastSpokenText) return

        val settings = settingsRepository.settings.value
        applyVoiceSettings(settings.voiceName, settings.voiceSpeechRate)

        lastSpokenText = text
        speakInternal(text, queueMode = TextToSpeech.QUEUE_ADD)
    }

    /**
     * Speaks a ~6-second test prompt on the navigation guidance audio channel so the driver
     * can comfortably adjust the car's navigation volume knob at any time.
     */
    fun speakTestPrompt() {
        val text = "Test der Sprachausgabe. Sie können jetzt die Navigationslautstärke am Autoradio einstellen."
        if (!isReady) {
            pendingPrompt = text
            return
        }
        val settings = settingsRepository.settings.value
        applyVoiceSettings(settings.voiceName, settings.voiceSpeechRate)
        speakInternal(text, queueMode = TextToSpeech.QUEUE_FLUSH)
    }

    /**
     * Speaks a short natural navigation phrase so the user can immediately compare voices
     * and speech rates in settings.
     */
    fun speakVoicePreview(
        preferredVoiceName: String? = settingsRepository.settings.value.voiceName,
        speechRate: VoiceSpeechRate = settingsRepository.settings.value.voiceSpeechRate
    ) {
        val text = "In zweihundert Metern rechts abbiegen auf die Hauptstraße."
        if (!isReady) {
            pendingPrompt = text
            return
        }
        applyVoiceSettings(preferredVoiceName, speechRate)
        speakInternal(text, queueMode = TextToSpeech.QUEUE_FLUSH)
    }

    private fun speakInternal(text: String, queueMode: Int) {
        Log.i(TAG, "SPEAK: $text")
        try {
            audioManager.requestAudioFocus(audioFocusRequest)
        } catch (_: Exception) {}
        val id = "car_nav_utterance_${utteranceCounter.incrementAndGet()}"
        val res = tts?.speak(text, queueMode, null, id)
        if (res == TextToSpeech.SUCCESS) {
            NavigationStateHolder.setSpeaking(true)
        }
    }

    private fun abandonFocus() {
        try {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            tts?.stop()
            NavigationStateHolder.setSpeaking(false)
            abandonFocus()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        NavigationStateHolder.setSpeaking(false)
        abandonFocus()
    }

    companion object {
        private const val TAG = "CarVoiceGuidance"

        @Volatile
        private var instance: CarVoiceGuidance? = null

        fun getInstance(context: Context): CarVoiceGuidance {
            return instance ?: synchronized(this) {
                instance ?: CarVoiceGuidance(context.applicationContext).also { instance = it }
            }
        }
    }
}
