package com.example.nagivator.car

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

class CarVoiceGuidance(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private var isMuted = false
    private var lastSpokenText: String = ""

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            tts?.setAudioAttributes(audioAttributes)
            val result = tts?.setLanguage(Locale.GERMAN)
            isReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!isReady || isMuted || text.isBlank()) return
        if (!force && text == lastSpokenText) return

        lastSpokenText = text
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "car_nav_utterance")
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            tts?.stop()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
