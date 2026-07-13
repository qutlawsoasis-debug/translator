package com.magne.translator

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.magne.translator.utils.SentencePieceProcessorJava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

class NLLBManager(private val context: Context) {
    private val onnxEnv = OrtEnvironment.getEnvironment()
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var embedAndLmHeadSession: OrtSession? = null
    private var cacheInitSession: OrtSession? = null
    private var spProcessor: SentencePieceProcessorJava? = null

    private val DICTIONARY_LENGTH = 256000
    private val languagesNLLB = arrayOf(
        "ace_Arab", "ace_Latn", "acm_Arab", "acq_Arab", "aeb_Arab", "afr_Latn", "ajp_Arab", "aka_Latn", "amh_Ethi", "apc_Arab", "arb_Arab", "ars_Arab", "ary_Arab", "arz_Arab", "asm_Beng", "ast_Latn", "awa_Deva", "ayr_Latn", "azb_Arab", "azj_Latn", "bak_Cyrl", "bam_Latn", "ban_Latn", "bel_Cyrl", "bem_Latn", "ben_Beng", "bho_Deva", "bjn_Arab", "bjn_Latn", "bod_Tibt", "bos_Latn", "bug_Latn", "bul_Cyrl", "cat_Latn", "ceb_Latn", "ces_Latn", "cjk_Latn", "ckb_Arab", "crh_Latn", "cym_Latn", "dan_Latn", "deu_Latn", "dik_Latn", "dyu_Latn", "dzo_Tibt", "ell_Grek", "eng_Latn", "epo_Latn", "est_Latn", "eus_Latn", "ewe_Latn", "fao_Latn", "pes_Arab", "fij_Latn", "fin_Latn", "fon_Latn", "fra_Latn", "fur_Latn", "fuv_Latn", "gla_Latn", "gle_Latn", "glg_Latn", "grn_Latn", "guj_Gujr", "hat_Latn", "hau_Latn", "heb_Hebr", "hin_Deva", "hne_Deva", "hrv_Latn", "hun_Latn", "hye_Armn", "ibo_Latn", "ilo_Latn", "ind_Latn", "isl_Latn", "ita_Latn", "jav_Latn", "jpn_Jpan", "kab_Latn", "kac_Latn", "kam_Latn", "kan_Knda", "kas_Arab", "kas_Deva", "kat_Geor", "knc_Arab", "knc_Latn", "kaz_Cyrl", "kbp_Latn", "kea_Latn", "khm_Khmr", "kik_Latn", "kin_Latn", "kir_Cyrl", "kmb_Latn", "kon_Latn", "kor_Hang", "kmr_Latn", "lao_Laoo", "lvs_Latn", "lij_Latn", "lim_Latn", "lin_Latn", "lit_Latn", "lmo_Latn", "ltg_Latn", "ltz_Latn", "lua_Latn", "lug_Latn", "luo_Latn", "lus_Latn", "mag_Deva", "mai_Deva", "mal_Mlym", "mar_Deva", "min_Latn", "mkd_Cyrl", "plt_Latn", "mlt_Latn", "mni_Beng", "khk_Cyrl", "mos_Latn", "mri_Latn", "zsm_Latn", "mya_Mymr", "nld_Latn", "nno_Latn", "nob_Latn", "npi_Deva", "nso_Latn", "nus_Latn", "nya_Latn", "oci_Latn", "gaz_Latn", "ory_Orya", "pag_Latn", "pan_Guru", "pap_Latn", "pol_Latn", "por_Latn", "prs_Arab", "pbt_Arab", "quy_Latn", "ron_Latn", "run_Latn", "rus_Cyrl", "sag_Latn", "san_Deva", "sat_Beng", "scn_Latn", "shn_Mymr", "sin_Sinh", "slk_Latn", "slv_Latn", "smo_Latn", "sna_Latn", "snd_Arab", "som_Latn", "sot_Latn", "spa_Latn", "als_Latn", "srd_Latn", "srp_Cyrl", "ssw_Latn", "sun_Latn", "swe_Latn", "swh_Latn", "szl_Latn", "tam_Taml", "tat_Cyrl", "tel_Telu", "tgk_Cyrl", "tgl_Latn", "tha_Thai", "tir_Ethi", "taq_Latn", "taq_Tfng", "tpi_Latn", "tsn_Latn", "tso_Latn", "tuk_Latn", "tum_Latn", "tur_Latn", "twi_Latn", "tzm_Tfng", "uig_Arab", "ukr_Cyrl", "umb_Latn", "urd_Arab", "uzn_Latn", "vec_Latn", "vie_Latn", "war_Latn", "wol_Latn", "xho_Latn", "ydd_Hebr", "yor_Latn", "yue_Hant", "zho_Hans", "zho_Hant", "zul_Latn"
    )

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val modelsDir = File(context.getExternalFilesDir(null), "onnx_models")
        val encoderPath = File(modelsDir, "NLLB_encoder.onnx").absolutePath
        val decoderPath = File(modelsDir, "NLLB_decoder.onnx").absolutePath
        val embedPath = File(modelsDir, "NLLB_embed_and_lm_head.onnx").absolutePath
        val cachePath = File(modelsDir, "NLLB_cache_initializer.onnx").absolutePath
        val vocabPath = File(modelsDir, "flores200_sacrebleu_tokenizer_spm.model").absolutePath

        val options = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(false)
            setCPUArenaAllocator(false)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }

        encoderSession = onnxEnv.createSession(encoderPath, options)
        decoderSession = onnxEnv.createSession(decoderPath, options)
        embedAndLmHeadSession = onnxEnv.createSession(embedPath, options)
        cacheInitSession = onnxEnv.createSession(cachePath, options)

        spProcessor = SentencePieceProcessorJava().apply {
            Load(vocabPath)
        }
    }

    private fun getLanguageCode(lang: String): String {
        return when (lang.lowercase()) {
            "de" -> "deu_Latn"
            "ru" -> "rus_Cyrl"
            "en" -> "eng_Latn"
            "fr" -> "fra_Latn"
            "es" -> "spa_Latn"
            "zh" -> "zho_Hans"
            "ja" -> "jpn_Jpan"
            "ko" -> "kor_Hang"
            "it" -> "ita_Latn"
            "pt" -> "por_Latn"
            else -> "eng_Latn"
        }
    }

    private fun getLanguageID(language: String): Int {
        val index = languagesNLLB.indexOf(language)
        if (index != -1) return DICTIONARY_LENGTH + index + 1
        return -1
    }

    private fun tokenize(text: String, srcLang: String): Pair<IntArray, IntArray> {
        val spmIds = spProcessor!!.encode(text)
        val ids = IntArray(spmIds.size)
        for (i in spmIds.indices) {
            var id = spmIds[i] + 1
            if (id == 1) id = 3
            else if (id == 2) id = 0
            else if (id == 3) id = 2
            ids[i] = id
        }

        val srcLanguageID = getLanguageID(srcLang)
        val eos = 2 // </s> in NLLB

        val idsExtended = IntArray(ids.size + 2)
        idsExtended[0] = srcLanguageID
        System.arraycopy(ids, 0, idsExtended, 1, ids.size)
        idsExtended[idsExtended.size - 1] = eos

        val attentionMask = IntArray(idsExtended.size) { 1 }
        return Pair(idsExtended, attentionMask)
    }

    private fun decode(ids: IntArray): String {
        var output = ""
        for (id in ids) {
            if (id in 4 until DICTIONARY_LENGTH) {
                output += spProcessor!!.IDToPiece(id - 1)
            }
        }
        if (output.isNotEmpty() && output[0] == '\u2581') {
            output = output.substring(1)
        }
        return output.replace('\u2581', ' ')
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

    private fun createInt64Tensor(shape: LongArray, longData: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(onnxEnv, LongBuffer.wrap(longData), shape)
    }

    private fun createFloatTensor(shape: LongArray): OnnxTensor {
        var size = 1
        for (dim in shape) size *= dim.toInt()
        val floatData = FloatArray(size) { 0f }
        return OnnxTensor.createTensor(onnxEnv, FloatBuffer.wrap(floatData), shape)
    }

    suspend fun translate(text: String, fromLang: String, toLang: String): String = withContext(Dispatchers.IO) {
        if (encoderSession == null) {
            return@withContext "Error: NLLB models not initialized"
        }

        val srcCode = getLanguageCode(fromLang)
        val tgtCode = getLanguageCode(toLang)

        val (inputIds, attentionMask) = tokenize(text, srcCode)

        val inputIdsTensor = createInt64Tensor(inputIds)
        val attentionMaskTensor = createInt64Tensor(attentionMask)
        val emptyPreLogits = createFloatTensor(longArrayOf(1, 1, 1024))
        val useLmHeadFalse = OnnxTensor.createTensor(onnxEnv, booleanArrayOf(false))
        val useLmHeadTrue = OnnxTensor.createTensor(onnxEnv, booleanArrayOf(true))

        // 1. Embed Matrix
        val embedInputs = mapOf(
            "input_ids" to inputIdsTensor,
            "pre_logits" to emptyPreLogits,
            "use_lm_head" to useLmHeadFalse
        )
        val embedResult = embedAndLmHeadSession!!.run(embedInputs, setOf("embed_matrix"))
        val embedMatrix = embedResult[0] as OnnxTensor

        // 2. Encoder
        val encoderInputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "embed_matrix" to embedMatrix
        )
        val encoderResultSession = encoderSession!!.run(encoderInputs)
        val encoderHiddenStates = encoderResultSession.get("last_hidden_state").get() as OnnxTensor

        embedResult.close()

        // 3. Cache Initializer
        val cacheInitInputs = mapOf("encoder_hidden_states" to encoderHiddenStates)
        val cacheInitResult = cacheInitSession!!.run(cacheInitInputs)

        // 4. Decoder Loop
        val eos = 2
        var maxId = -1
        var iteration = 1

        val currentInputId = intArrayOf(2) // start with </s>
        val generatedIds = mutableListOf<Int>()
        generatedIds.add(0) // <s>

        var pastKeyValues: OrtSession.Result? = null
        val emptyInputIdsForLmHead = createInt64Tensor(longArrayOf(1, 2), LongArray(2) { 0 })

        while (maxId != eos && iteration < inputIds.size * 5) {
            val currentInputIdTensor = createInt64Tensor(currentInputId)

            val curEmbedInputs = mapOf(
                "input_ids" to currentInputIdTensor,
                "pre_logits" to emptyPreLogits,
                "use_lm_head" to useLmHeadFalse
            )
            val curEmbedResult = embedAndLmHeadSession!!.run(curEmbedInputs, setOf("embed_matrix"))
            val curEmbedMatrix = curEmbedResult[0] as OnnxTensor

            val decoderInputs = mutableMapOf<String, OnnxTensor>()
            decoderInputs["input_ids"] = currentInputIdTensor
            decoderInputs["encoder_attention_mask"] = attentionMaskTensor
            decoderInputs["embed_matrix"] = curEmbedMatrix

            val nLayers = 12
            val hiddenSize = 64

            if (iteration == 1) {
                val pastTensor = createFloatTensor(longArrayOf(1, 16, 0, hiddenSize.toLong()))
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
            curEmbedResult.close()

            val preLogits = pastKeyValues.get("pre_logits").get() as OnnxTensor

            val lmHeadInputs = mapOf(
                "input_ids" to emptyInputIdsForLmHead,
                "pre_logits" to preLogits,
                "use_lm_head" to useLmHeadTrue
            )
            val lmHeadResult = embedAndLmHeadSession!!.run(lmHeadInputs, setOf("logits"))
            val logitsTensor = lmHeadResult[0] as OnnxTensor

            val logitsData = logitsTensor.value as Array<Array<FloatArray>>
            val lastLogits = logitsData[0][0]
            maxId = getIndexOfLargest(lastLogits)

            if (iteration == 1) {
                currentInputId[0] = getLanguageID(tgtCode)
            } else {
                currentInputId[0] = maxId
                generatedIds.add(maxId)
            }

            lmHeadResult.close()
            currentInputIdTensor.close()

            iteration++
        }

        encoderResultSession.close()
        cacheInitResult.close()
        pastKeyValues?.close()
        inputIdsTensor.close()
        attentionMaskTensor.close()
        emptyPreLogits.close()
        useLmHeadFalse.close()
        emptyInputIdsForLmHead.close()
        useLmHeadTrue.close()

        decode(generatedIds.toIntArray())
    }
}
