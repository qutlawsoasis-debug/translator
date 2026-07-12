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
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.TranslateLanguage
import java.io.IOException
import java.util.Locale

class TranslatorActivity : AppCompatActivity(), RecognitionListener, TextToSpeech.OnInitListener {

    private var speechService: SpeechService? = null
    private var tts: TextToSpeech? = null
    private lateinit var translatorManager: TranslatorManager
    private var toLangCode: String = TranslateLanguage.ENGLISH
    
    private lateinit var tvStatus: TextView
    private lateinit var tvRecognized: TextView
    private lateinit var tvTranslated: TextView
    private lateinit var btnChangeLanguage: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translator)
        
        tvStatus = findViewById(R.id.tvStatus)
        tvRecognized = findViewById(R.id.tvRecognized)
        tvTranslated = findViewById(R.id.tvTranslated)
        btnChangeLanguage = findViewById(R.id.btnChangeLanguage)

        val fromLang = intent.getStringExtra("from_lang") ?: TranslateLanguage.RUSSIAN
        toLangCode = intent.getStringExtra("to_lang") ?: TranslateLanguage.ENGLISH
        
        translatorManager = TranslatorManager(fromLang, toLangCode)

        tts = TextToSpeech(this, this)
        
        btnChangeLanguage.setOnClickListener {
            val intent = Intent(this, LanguageSelectActivity::class.java)
            startActivity(intent)
            finish()
        }

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
        tvStatus.text = "Загрузка перевода (нужен интернет)..."
        translatorManager.downloadModelIfNeeded(
            context = this,
            onSuccess = {
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
            },
            onError = { exception ->
                tvStatus.text = "Ошибка скачивания словаря: ${exception.message}"
                Log.e("MLKit", "Failed to download model", exception)
            }
        )
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
        if (hypothesis == null) return
        try {
            val json = JSONObject(hypothesis)
            val partial = json.getString("partial")
            if (partial.isNotEmpty()) {
                tvRecognized.text = partial + "..."
            }
        } catch (e: Exception) {
            // Игнорируем
        }
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis == null) return
        try {
            val json = JSONObject(hypothesis)
            val text = json.getString("text")
            if (text.isNotEmpty()) {
                tvRecognized.text = text
                tvStatus.text = "Перевожу..."
                translatorManager.translate(text,
                    onSuccess = { translation ->
                        tvTranslated.text = translation
                        tts?.speak(translation, TextToSpeech.QUEUE_FLUSH, null, null)
                        tvStatus.text = "Переведено!"
                    },
                    onError = { e ->
                        tvTranslated.text = ""
                        tvStatus.text = "Ошибка перевода"
                        Log.e("MLKit", "Translation failed", e)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("Vosk", "JSON Parse error", e)
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
            tts?.language = getLocaleFromLanguageCode(toLangCode)
        }
    }

    private fun getLocaleFromLanguageCode(code: String): Locale {
        return when (code) {
            com.google.mlkit.nl.translate.TranslateLanguage.RUSSIAN -> Locale("ru")
            com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH -> Locale.US
            com.google.mlkit.nl.translate.TranslateLanguage.GERMAN -> Locale.GERMAN
            com.google.mlkit.nl.translate.TranslateLanguage.FRENCH -> Locale.FRENCH
            com.google.mlkit.nl.translate.TranslateLanguage.SPANISH -> Locale("es")
            com.google.mlkit.nl.translate.TranslateLanguage.ITALIAN -> Locale.ITALIAN
            com.google.mlkit.nl.translate.TranslateLanguage.CHINESE -> Locale.CHINESE
            com.google.mlkit.nl.translate.TranslateLanguage.KOREAN -> Locale.KOREAN
            com.google.mlkit.nl.translate.TranslateLanguage.JAPANESE -> Locale.JAPANESE
            com.google.mlkit.nl.translate.TranslateLanguage.PORTUGUESE -> Locale("pt")
            else -> Locale.US
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService?.shutdown()
        tts?.stop()
        tts?.shutdown()
        translatorManager.close()
    }
}
