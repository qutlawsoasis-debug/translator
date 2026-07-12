package com.magne.translator

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import android.util.Log

class TranslatorManager(
    private val sourceLang: String = TranslateLanguage.RUSSIAN,
    private val targetLang: String = TranslateLanguage.ENGLISH
) {
    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(sourceLang)
        .setTargetLanguage(targetLang)
        .build()

    private val translator = Translation.getClient(options)
    private val modelManager = RemoteModelManager.getInstance()

    fun downloadModelIfNeeded(context: android.content.Context, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val sourceModel = TranslateRemoteModel.Builder(sourceLang).build()
        val targetModel = TranslateRemoteModel.Builder(targetLang).build()

        modelManager.isModelDownloaded(sourceModel).addOnSuccessListener { isSourceDownloaded ->
            modelManager.isModelDownloaded(targetModel).addOnSuccessListener { isTargetDownloaded ->
                if (isSourceDownloaded && isTargetDownloaded) {
                    Log.d("TranslatorManager", "Models already downloaded.")
                    onSuccess()
                } else {
                    Log.d("TranslatorManager", "Models not found. Starting download...")
                    val conditions = DownloadConditions.Builder().build()
                    translator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener { 
                            Log.d("TranslatorManager", "Download complete.")
                            onSuccess() 
                        }
                        .addOnFailureListener { onError(it) }
                }
            }.addOnFailureListener { onError(it) }
        }.addOnFailureListener { onError(it) }
    }

    fun translate(text: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onError(Exception("Empty text"))
            return
        }
        translator.translate(trimmed)
            .addOnSuccessListener { onSuccess(it) }
            .addOnFailureListener { onError(it) }
    }
    
    fun close() {
        translator.close()
    }
}
