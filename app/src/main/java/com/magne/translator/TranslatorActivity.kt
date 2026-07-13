package com.magne.translator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class TranslatorActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var whisperManager: WhisperManager
    private lateinit var nllbManager: NLLBManager
    private var tts: TextToSpeech? = null
    
    private lateinit var tvStatus: TextView
    private lateinit var tvRecognized: TextView
    private lateinit var tvTranslated: TextView
    private lateinit var btnChangeLanguage: Button

    private var fromLangCode: String = "de"
    private var toLangCode: String = "ru"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translator)

        tvStatus = findViewById(R.id.tvStatus)
        tvRecognized = findViewById(R.id.tvRecognized)
        tvTranslated = findViewById(R.id.tvTranslated)
        btnChangeLanguage = findViewById(R.id.btnChangeLanguage)

        fromLangCode = intent.getStringExtra("from_lang") ?: "de"
        toLangCode = intent.getStringExtra("to_lang") ?: "ru"

        tts = TextToSpeech(this, this)

        btnChangeLanguage.setOnClickListener {
            val intent = Intent(this, LanguageSelectActivity::class.java)
            startActivity(intent)
            finish()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initModels()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initModels()
        } else {
            tvStatus.text = "Нет доступа к микрофону"
        }
    }

    private fun initModels() {
        tvStatus.text = "Инициализация нейросетей..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                whisperManager = WhisperManager(this@TranslatorActivity)
                whisperManager.initialize()
                
                nllbManager = NLLBManager(this@TranslatorActivity)
                nllbManager.initialize()

                withContext(Dispatchers.Main) {
                    tvStatus.text = "Готов. Говорите."
                    startListening()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Ошибка инициализации: ${e.message}"
                }
            }
        }
    }

    private fun startListening() {
        val langName = getLanguageName(fromLangCode)
        tvStatus.text = "🎙 Говорите на: $langName"
        
        whisperManager.startListening(
            langCode = fromLangCode,
            onPartial = { text ->
                tvRecognized.text = "$text..."
            },
            onFinal = { text ->
                tvRecognized.text = text
                tvStatus.text = "Перевожу..."
                
                lifecycleScope.launch {
                    val translation = nllbManager.translate(text, fromLangCode, toLangCode)
                    tvTranslated.text = translation
                    tts?.speak(translation, TextToSpeech.QUEUE_FLUSH, null, null)
                    tvStatus.text = "Говорите."
                }
            }
        )
    }

    private fun getLanguageName(code: String): String {
        return when (code) {
            "ru" -> "Русский"
            "en" -> "Английский"
            "de" -> "Немецкий"
            "fr" -> "Французский"
            "es" -> "Испанский"
            "it" -> "Итальянский"
            "zh" -> "Китайский"
            "ko" -> "Корейский"
            "ja" -> "Японский"
            "pt" -> "Португальский"
            else -> "Неизвестный"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = getLocaleFromLanguageCode(toLangCode)
        }
    }

    private fun getLocaleFromLanguageCode(code: String): Locale {
        return when (code) {
            "ru" -> Locale("ru")
            "en" -> Locale.US
            "de" -> Locale.GERMAN
            "fr" -> Locale.FRENCH
            "es" -> Locale("es")
            "it" -> Locale.ITALIAN
            "zh" -> Locale.CHINESE
            "ko" -> Locale.KOREAN
            "ja" -> Locale.JAPANESE
            "pt" -> Locale("pt")
            else -> Locale.US
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::whisperManager.isInitialized) {
            whisperManager.stopListening()
        }
        tts?.stop()
        tts?.shutdown()
    }
}
