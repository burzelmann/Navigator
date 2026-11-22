package com.example.nagivator.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.nagivator.domain.NavigationStateHolder
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class CarVoiceGuidance(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isReady = false
    private var isMuted = NavigationStateHolder.isMuted.value
    private var lastSpokenText: String = ""
    private var pendingPrompt: String? = null
    private val utteranceCounter = AtomicInteger(0)

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

            pendingPrompt?.let { queued ->
                pendingPrompt = null
                if (isReady) {
                    speakInternal(queued, queueMode = TextToSpeech.QUEUE_FLUSH)
                }
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!isReady || isMuted || text.isBlank()) return
        if (!force && text == lastSpokenText) return

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
        speakInternal(text, queueMode = TextToSpeech.QUEUE_FLUSH)
    }

    private fun speakInternal(text: String, queueMode: Int) {
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
        @Volatile
        private var instance: CarVoiceGuidance? = null

        fun getInstance(context: Context): CarVoiceGuidance {
            return instance ?: synchronized(this) {
                instance ?: CarVoiceGuidance(context.applicationContext).also { instance = it }
            }
        }
    }
}
