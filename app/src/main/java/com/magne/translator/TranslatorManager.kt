package com.magne.translator

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslatorManager {
    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.RUSSIAN)
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build()

    private val translator = Translation.getClient(options)

    fun downloadModelIfNeeded(context: android.content.Context, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val prefs = context.getSharedPreferences("TranslatorPrefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_model_downloaded_once", false)) {
            onSuccess()
            return
        }

        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener { 
                prefs.edit().putBoolean("is_model_downloaded_once", true).apply()
                onSuccess() 
            }
            .addOnFailureListener { onError(it) }
    }

    fun translate(russianText: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val text = russianText.trim()
        if (text.isEmpty()) {
            onError(Exception("Empty text"))
            return
        }
        translator.translate(text)
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onError(it) }
    }
    
    fun close() {
        translator.close()
    }
}
