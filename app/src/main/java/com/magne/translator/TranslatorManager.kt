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

    fun downloadModelIfNeeded(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val conditions = DownloadConditions.Builder()
            .build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener { onSuccess() }
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
