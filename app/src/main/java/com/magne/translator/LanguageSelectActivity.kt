package com.magne.translator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel

data class LanguageItem(val name: String, val code: String)

class LanguageSelectActivity : Activity() {

    private val languages = listOf(
        LanguageItem("Русский", TranslateLanguage.RUSSIAN),
        LanguageItem("Английский", TranslateLanguage.ENGLISH),
        LanguageItem("Немецкий", TranslateLanguage.GERMAN),
        LanguageItem("Французский", TranslateLanguage.FRENCH),
        LanguageItem("Испанский", TranslateLanguage.SPANISH),
        LanguageItem("Итальянский", TranslateLanguage.ITALIAN),
        LanguageItem("Китайский", TranslateLanguage.CHINESE),
        LanguageItem("Корейский", TranslateLanguage.KOREAN),
        LanguageItem("Японский", TranslateLanguage.JAPANESE),
        LanguageItem("Португальский", TranslateLanguage.PORTUGUESE)
    )

    private lateinit var spinnerSource: Spinner
    private lateinit var spinnerTarget: Spinner
    private lateinit var tvSourceStatus: TextView
    private lateinit var tvTargetStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var progressBar: ProgressBar

    private val modelManager = RemoteModelManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_select)

        spinnerSource = findViewById(R.id.spinnerSource)
        spinnerTarget = findViewById(R.id.spinnerTarget)
        tvSourceStatus = findViewById(R.id.tvSourceStatus)
        tvTargetStatus = findViewById(R.id.tvTargetStatus)
        btnStart = findViewById(R.id.btnStart)
        progressBar = findViewById(R.id.progressBar)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages.map { it.name })
        spinnerSource.adapter = adapter
        spinnerTarget.adapter = adapter

        // Load saved preferences if any
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedSource = prefs.getString("pref_from_lang", TranslateLanguage.RUSSIAN)
        val savedTarget = prefs.getString("pref_to_lang", TranslateLanguage.ENGLISH)

        spinnerSource.setSelection(languages.indexOfFirst { it.code == savedSource }.takeIf { it >= 0 } ?: 0)
        spinnerTarget.setSelection(languages.indexOfFirst { it.code == savedTarget }.takeIf { it >= 0 } ?: 1)

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                checkModelsStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerSource.onItemSelectedListener = itemSelectedListener
        spinnerTarget.onItemSelectedListener = itemSelectedListener

        btnStart.setOnClickListener {
            val sourceIdx = spinnerSource.selectedItemPosition
            val targetIdx = spinnerTarget.selectedItemPosition

            if (sourceIdx == targetIdx) {
                Toast.makeText(this, "Выберите разные языки", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceLang = languages[sourceIdx]
            val targetLang = languages[targetIdx]

            // Save preferences
            prefs.edit()
                .putString("pref_from_lang", sourceLang.code)
                .putString("pref_to_lang", targetLang.code)
                .apply()

            downloadModelsAndStart(sourceLang.code, targetLang.code)
        }
    }

    private fun checkModelsStatus() {
        val sourceIdx = spinnerSource.selectedItemPosition
        val targetIdx = spinnerTarget.selectedItemPosition
        if (sourceIdx < 0 || targetIdx < 0) return

        val sourceLang = languages[sourceIdx]
        val targetLang = languages[targetIdx]

        checkSingleModelStatus(sourceLang.code, tvSourceStatus)
        checkSingleModelStatus(targetLang.code, tvTargetStatus)
    }

    private fun checkSingleModelStatus(langCode: String, statusView: TextView) {
        statusView.text = "Проверка..."
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.isModelDownloaded(model).addOnSuccessListener { isDownloaded ->
            if (isDownloaded) {
                statusView.text = "✅ Модель готова"
                statusView.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            } else {
                statusView.text = "⬇️ Нужно скачать ~30MB"
                statusView.setTextColor(android.graphics.Color.parseColor("#FFC107"))
            }
        }.addOnFailureListener {
            statusView.text = "❌ Ошибка проверки"
            statusView.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }
    }

    private fun downloadModelsAndStart(sourceCode: String, targetCode: String) {
        btnStart.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val sourceModel = TranslateRemoteModel.Builder(sourceCode).build()
        val targetModel = TranslateRemoteModel.Builder(targetCode).build()
        val conditions = DownloadConditions.Builder().build()

        var sourceDone = false
        var targetDone = false

        fun checkAndStart() {
            if (sourceDone && targetDone) {
                progressBar.visibility = View.GONE
                btnStart.isEnabled = true
                
                val intent = Intent(this, TranslatorActivity::class.java)
                intent.putExtra("from_lang", sourceCode)
                intent.putExtra("to_lang", targetCode)
                startActivity(intent)
                finish()
            }
        }

        modelManager.download(sourceModel, conditions)
            .addOnSuccessListener {
                sourceDone = true
                checkSingleModelStatus(sourceCode, tvSourceStatus)
                checkAndStart()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnStart.isEnabled = true
                Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("LanguageSelect", "Failed to download source model", e)
            }

        modelManager.download(targetModel, conditions)
            .addOnSuccessListener {
                targetDone = true
                checkSingleModelStatus(targetCode, tvTargetStatus)
                checkAndStart()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnStart.isEnabled = true
                Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("LanguageSelect", "Failed to download target model", e)
            }
    }
}
