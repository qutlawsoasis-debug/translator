package com.magne.translator

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.*
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.abs

class WhisperManager(private val context: Context) {

    private val onnxEnv = OrtEnvironment.getEnvironment()
    private var initSession: OrtSession? = null
    private var encoderSession: OrtSession? = null
    private var cacheInitSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var detokenizerSession: OrtSession? = null

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    private val SAMPLE_RATE = 16000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT // Гарантированно работает везде
    
    private val AMPLITUDE_THRESHOLD = 2000
    private val SPEECH_TIMEOUT_MILLIS = 1300L
    private val MAX_SPEECH_LENGTH_MILLIS = 30000L

    private val START_TOKEN_ID = 50258
    private val TRANSCRIBE_TOKEN_ID = 50359
    private val NO_TIMESTAMPS_TOKEN_ID = 50363
    private val EOS_TOKEN_ID = 50257

    private val LANGUAGES = arrayOf(
        "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no", "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc", "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl", "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue"
    )

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val modelsDir = File(context.getExternalFilesDir(null), "onnx_models")

        val options = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(false)
            setCPUArenaAllocator(false)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }

        initSession = onnxEnv.createSession(File(modelsDir, "Whisper_initializer.onnx").absolutePath, options)
        encoderSession = onnxEnv.createSession(File(modelsDir, "Whisper_encoder.onnx").absolutePath, options.apply {
            setSymbolicDimensionValue("batch_size", 1)
        })
        cacheInitSession = onnxEnv.createSession(File(modelsDir, "Whisper_cache_initializer.onnx").absolutePath, options)
        decoderSession = onnxEnv.createSession(File(modelsDir, "Whisper_decoder.onnx").absolutePath, options)
        detokenizerSession = onnxEnv.createSession(File(modelsDir, "Whisper_detokenizer.onnx").absolutePath, options)
    }

    private fun getLanguageID(languageCode: String): Int {
        val index = LANGUAGES.indexOf(languageCode.lowercase())
        if (index != -1) return START_TOKEN_ID + index + 1
        return START_TOKEN_ID + 1 // Default to 'en'
    }

    @SuppressLint("MissingPermission")
    fun startListening(
        langCode: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit
    ) {
        if (isRecording) return
        isRecording = true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, minBufferSize * 2)

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            return
        }

        audioRecord?.startRecording()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val audioBuffer = mutableListOf<Float>()
            val readBuffer = ShortArray(minBufferSize)
            
            var lastVoiceHeardMillis = Long.MAX_VALUE
            var voiceStartedMillis = 0L
            var isVoiceActive = false
            
            var lastPartialProcessTime = 0L

            while (isActive && isRecording) {
                val readSize = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (readSize > 0) {
                    var maxAmplitude = 0f
                    for (i in 0 until readSize) {
                        val floatVal = readBuffer[i].toFloat() / 32768.0f
                        audioBuffer.add(floatVal)
                        
                        val amplitude = abs(readBuffer[i].toFloat())
                        if (amplitude > maxAmplitude) maxAmplitude = amplitude
                    }

                    val now = System.currentTimeMillis()

                    // Простой VAD
                    if (maxAmplitude > AMPLITUDE_THRESHOLD) {
                        if (!isVoiceActive) {
                            isVoiceActive = true
                            voiceStartedMillis = now
                            audioBuffer.clear() // очищаем тишину перед началом речи
                            // оставляем немного контекста (0.5 сек)
                            // Для простоты здесь просто начинаем с нуля
                        }
                        lastVoiceHeardMillis = now
                    }

                    if (isVoiceActive) {
                        // Каждую 1 секунду речи генерируем partial результат
                        if (now - lastPartialProcessTime > 1000) {
                            lastPartialProcessTime = now
                            val currentAudio = audioBuffer.toFloatArray()
                            processAudioChunk(currentAudio, langCode) { partialText ->
                                launch(Dispatchers.Main) { onPartial(partialText) }
                            }
                        }

                        // Проверяем паузу или таймаут
                        val silenceDuration = now - lastVoiceHeardMillis
                        val speechDuration = now - voiceStartedMillis

                        if (silenceDuration > SPEECH_TIMEOUT_MILLIS || speechDuration > MAX_SPEECH_LENGTH_MILLIS) {
                            // Конец фразы
                            isVoiceActive = false
                            val finalAudio = audioBuffer.toFloatArray()
                            audioBuffer.clear()
                            lastVoiceHeardMillis = Long.MAX_VALUE
                            
                            processAudioChunk(finalAudio, langCode) { finalText ->
                                launch(Dispatchers.Main) { onFinal(finalText) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
        processingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private suspend fun processAudioChunk(audioData: FloatArray, langCode: String, onResult: (String) -> Unit) {
        if (initSession == null || audioData.isEmpty()) return
        
        // Запускаем инференс, не блокируя захват аудио
        withContext(Dispatchers.Default) {
            try {
                // 1. Init Audio
                val audioTensor = OnnxTensor.createTensor(onnxEnv, FloatBuffer.wrap(audioData), longArrayOf(1, audioData.size.toLong()))
                val initResult = initSession!!.run(mapOf("audio_pcm" to audioTensor))
                val inputFeatures = initResult[0] as OnnxTensor

                // 2. Encoder
                val encoderResultSession = encoderSession!!.run(mapOf("input_features" to inputFeatures))
                val encoderOutput = encoderResultSession[0] as OnnxTensor

                // 3. Cache Initializer
                val cacheInitResult = cacheInitSession!!.run(mapOf("encoder_hidden_states" to encoderOutput))

                // 4. Decoder
                val langId = getLanguageID(langCode)
                val initialIds = intArrayOf(START_TOKEN_ID, langId, TRANSCRIBE_TOKEN_ID, NO_TIMESTAMPS_TOKEN_ID)
                
                var maxId = -1
                var iteration = 1
                val generatedIds = mutableListOf<Int>()
                var pastKeyValues: OrtSession.Result? = null

                while (maxId != EOS_TOKEN_ID && iteration < 100) {
                    val inputId = if (iteration <= 4) initialIds[iteration - 1] else maxId
                    val inputIdsTensor = createInt64Tensor(intArrayOf(inputId))

                    val decoderInputs = mutableMapOf<String, OnnxTensor>(
                        "input_ids" to inputIdsTensor
                    )

                    val nLayers = 12
                    if (iteration == 1) {
                        val pastTensor = createFloatTensor(longArrayOf(1, 12, 0, 64))
                        for (i in 0 until nLayers) {
                            decoderInputs["past_key_values.$i.decoder.key"] = pastTensor
                            decoderInputs["past_key_values.$i.decoder.value"] = pastTensor
                            decoderInputs["past_key_values.$i.encoder.key"] = cacheInitResult.get("present.$i.encoder.key").get() as OnnxTensor
                            decoderInputs["past_key_values.$i.encoder.value"] = cacheInitResult.get("present.$i.encoder.value").get() as OnnxTensor
                        }
                    } else {
                        for (i in 0 until nLayers) {
                            decoderInputs["past_key_values.$i.decoder.key"] = pastKeyValues!!.get("present.$i.decoder.key").get() as OnnxTensor
                            decoderInputs["past_key_values.$i.decoder.value"] = pastKeyValues!!.get("present.$i.decoder.value").get() as OnnxTensor
                            decoderInputs["past_key_values.$i.encoder.key"] = cacheInitResult.get("present.$i.encoder.key").get() as OnnxTensor
                            decoderInputs["past_key_values.$i.encoder.value"] = cacheInitResult.get("present.$i.encoder.value").get() as OnnxTensor
                        }
                    }

                    val oldPastKeyValues = pastKeyValues
                    pastKeyValues = decoderSession!!.run(decoderInputs)
                    oldPastKeyValues?.close()

                    val logitsTensor = pastKeyValues.get("logits").get() as OnnxTensor
                    val logitsData = logitsTensor.value as Array<Array<FloatArray>>
                    maxId = getIndexOfLargest(logitsData[0][0])
                    
                    if (iteration > 4 && maxId != EOS_TOKEN_ID) {
                        generatedIds.add(maxId)
                    }

                    inputIdsTensor.close()
                    iteration++
                }

                // 5. Detokenizer
                var finalText = ""
                if (generatedIds.isNotEmpty()) {
                    val sequencesArray = generatedIds.toIntArray()
                    val sequencesTensor = OnnxTensor.createTensor(onnxEnv, IntBuffer.wrap(sequencesArray), longArrayOf(1, 1, sequencesArray.size.toLong()))
                    val detokenizerResult = detokenizerSession!!.run(mapOf("sequences" to sequencesTensor))
                    val resultStrings = detokenizerResult[0].value as Array<Array<String>>
                    finalText = resultStrings[0][0]
                    detokenizerResult.close()
                    sequencesTensor.close()
                }

                // Очистка памяти
                audioTensor.close()
                initResult.close()
                encoderResultSession.close()
                cacheInitResult.close()
                pastKeyValues?.close()

                finalText = cleanUpText(finalText)
                if (finalText.isNotBlank()) {
                    onResult(finalText)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun cleanUpText(text: String): String {
        var corrected = text.replace(Regex("<\\|[^>]*\\|>"), "").trim()
        if (corrected.length >= 2 && corrected.first().isLowerCase()) {
            corrected = corrected.replaceFirstChar { it.uppercase() }
        }
        return corrected.replace("...", "")
    }

    private fun getIndexOfLargest(array: FloatArray): Int {
        var maxIdx = 0
        var maxVal = array[0]
        for (i in 1 until array.size) {
            if (array[i] > maxVal) {
                maxVal = array[i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    private fun createInt64Tensor(data: IntArray): OnnxTensor {
        val longData = LongArray(data.size) { data[it].toLong() }
        return OnnxTensor.createTensor(onnxEnv, LongBuffer.wrap(longData), longArrayOf(1, data.size.toLong()))
    }

    private fun createFloatTensor(shape: LongArray): OnnxTensor {
        var size = 1
        for (dim in shape) size *= dim.toInt()
        val floatData = FloatArray(size) { 0f }
        return OnnxTensor.createTensor(onnxEnv, FloatBuffer.wrap(floatData), shape)
    }
}
