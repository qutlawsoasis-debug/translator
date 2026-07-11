package com.magne.translator

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.util.Locale

class TranslatorActivity : Activity(), RecognitionListener, TextToSpeech.OnInitListener {

    private var speechService: SpeechService? = null
    private var tts: TextToSpeech? = null
    private val translatorManager = TranslatorManager()
    
    private lateinit var tvStatus: TextView
    private lateinit var tvRecognized: TextView
    private lateinit var tvTranslated: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translator)
        
        tvStatus = findViewById(R.id.tvStatus)
        tvRecognized = findViewById(R.id.tvRecognized)
        tvTranslated = findViewById(R.id.tvTranslated)

        tts = TextToSpeech(this, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initModel()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initModel()
        } else {
            tvStatus.text = "Нет доступа к микрофону"
        }
    }

    private fun initModel() {
        tvStatus.text = "Распаковка модели (один раз)..."
        StorageService.unpack(this, "model-ru", "model",
            { model ->
                tvStatus.text = "Запуск микрофона..."
                startRecognition(model)
            },
            { exception ->
                tvStatus.text = "Ошибка загрузки: ${exception.message}"
                Log.e("Vosk", "Failed to unpack the model", exception)
            })
    }

    private fun startRecognition(model: Model) {
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            tvStatus.text = "Говорите..."
        } catch (e: IOException) {
            tvStatus.text = "Ошибка микрофона"
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        // Опционально: показывать промежуточный результат (убрал, чтобы не мерцало)
    }

    override fun onResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                val json = JSONObject(it)
                val text = json.getString("text")
                if (text.isNotEmpty()) {
                    tvRecognized.text = text
                    val translation = translatorManager.translate(text)
                    if (translation != null) {
                        tvTranslated.text = translation
                        tts?.speak(translation, TextToSpeech.QUEUE_FLUSH, null, null)
                        tvStatus.text = "Переведено!"
                    } else {
                        tvTranslated.text = ""
                        tvStatus.text = "Нет в словаре"
                    }
                }
            } catch (e: Exception) {
                Log.e("Vosk", "JSON Parse error", e)
            }
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        tvStatus.text = "Ошибка: ${exception?.message}"
    }

    override fun onTimeout() {
        tvStatus.text = "Таймаут"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService?.shutdown()
        tts?.stop()
        tts?.shutdown()
    }
}
