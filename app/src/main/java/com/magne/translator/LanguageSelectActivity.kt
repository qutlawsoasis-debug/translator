package com.magne.translator

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.launch

data class LanguageItem(val name: String, val code: String, val emoji: String)

class LanguageSelectActivity : AppCompatActivity() {

    private val languages = listOf(
        LanguageItem("Русский", TranslateLanguage.RUSSIAN, "🇷🇺"),
        LanguageItem("Английский", TranslateLanguage.ENGLISH, "🇬🇧"),
        LanguageItem("Немецкий", TranslateLanguage.GERMAN, "🇩🇪"),
        LanguageItem("Французский", TranslateLanguage.FRENCH, "🇫🇷"),
        LanguageItem("Испанский", TranslateLanguage.SPANISH, "🇪🇸"),
        LanguageItem("Итальянский", TranslateLanguage.ITALIAN, "🇮🇹"),
        LanguageItem("Китайский", TranslateLanguage.CHINESE, "🇨🇳"),
        LanguageItem("Корейский", TranslateLanguage.KOREAN, "🇰🇷"),
        LanguageItem("Японский", TranslateLanguage.JAPANESE, "🇯🇵"),
        LanguageItem("Португальский", TranslateLanguage.PORTUGUESE, "🇵🇹")
    )

    private lateinit var cardSource: LinearLayout
    private lateinit var cardTarget: LinearLayout
    private lateinit var tvSourceEmoji: TextView
    private lateinit var tvSourceName: TextView
    private lateinit var tvTargetEmoji: TextView
    private lateinit var tvTargetName: TextView
    private lateinit var tvSourceStatus: TextView
    private lateinit var tvTargetStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var progressBar: LinearProgressIndicator

    private var sourceLang: LanguageItem = languages[0]
    private var targetLang: LanguageItem = languages[1]

    private val mlModelManager = RemoteModelManager.getInstance()
    private val voskManager by lazy { VoskModelManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_select)

        cardSource = findViewById(R.id.cardSource)
        cardTarget = findViewById(R.id.cardTarget)
        tvSourceEmoji = findViewById(R.id.tvSourceEmoji)
        tvSourceName = findViewById(R.id.tvSourceName)
        tvTargetEmoji = findViewById(R.id.tvTargetEmoji)
        tvTargetName = findViewById(R.id.tvTargetName)
        tvSourceStatus = findViewById(R.id.tvSourceStatus)
        tvTargetStatus = findViewById(R.id.tvTargetStatus)
        btnStart = findViewById(R.id.btnStart)
        progressBar = findViewById(R.id.progressBar)

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedSource = prefs.getString("pref_from_lang", TranslateLanguage.RUSSIAN)
        val savedTarget = prefs.getString("pref_to_lang", TranslateLanguage.ENGLISH)

        sourceLang = languages.find { it.code == savedSource } ?: languages[0]
        targetLang = languages.find { it.code == savedTarget } ?: languages[1]

        updateUI()

        cardSource.setOnClickListener { showLanguagePicker(true) }
        cardTarget.setOnClickListener { showLanguagePicker(false) }

        btnStart.setOnClickListener {
            if (sourceLang.code == targetLang.code) {
                Toast.makeText(this, "Выберите разные языки", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("pref_from_lang", sourceLang.code)
                .putString("pref_to_lang", targetLang.code)
                .apply()

            downloadModelsAndStart()
        }
    }

    private fun updateUI() {
        tvSourceEmoji.text = sourceLang.emoji
        tvSourceName.text = sourceLang.name
        tvTargetEmoji.text = targetLang.emoji
        tvTargetName.text = targetLang.name
        checkModelsStatus()
    }

    private fun showLanguagePicker(isSource: Boolean) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_languages, null)
        val recyclerView: RecyclerView = view.findViewById(R.id.rvLanguages)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = LanguageAdapter(languages) { selected ->
            if (isSource) sourceLang = selected else targetLang = selected
            bottomSheetDialog.dismiss()
            updateUI()
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun checkModelsStatus() {
        // Source needs Vosk AND ML Kit
        tvSourceStatus.text = "⏳ Проверка..."
        tvSourceStatus.setTextColor(Color.parseColor("#AAAAAA"))
        
        val sourceMlModel = TranslateRemoteModel.Builder(sourceLang.code).build()
        mlModelManager.isModelDownloaded(sourceMlModel).addOnSuccessListener { mlDownloaded ->
            val voskReady = voskManager.isModelReady(sourceLang.code)
            
            if (mlDownloaded && voskReady) {
                tvSourceStatus.text = "✅ Готово"
                tvSourceStatus.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                tvSourceStatus.text = "⬇ Нужно скачать ~70MB (Vosk + ML)"
                tvSourceStatus.setTextColor(Color.parseColor("#FFC107"))
            }
        }.addOnFailureListener {
            tvSourceStatus.text = "❌ Ошибка"
            tvSourceStatus.setTextColor(Color.parseColor("#F44336"))
        }

        // Target needs ONLY ML Kit
        tvTargetStatus.text = "⏳ Проверка..."
        tvTargetStatus.setTextColor(Color.parseColor("#AAAAAA"))
        val targetMlModel = TranslateRemoteModel.Builder(targetLang.code).build()
        mlModelManager.isModelDownloaded(targetMlModel).addOnSuccessListener { mlDownloaded ->
            if (mlDownloaded) {
                tvTargetStatus.text = "✅ Готово"
                tvTargetStatus.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                tvTargetStatus.text = "⬇ ~30MB"
                tvTargetStatus.setTextColor(Color.parseColor("#FFC107"))
            }
        }.addOnFailureListener {
            tvTargetStatus.text = "❌ Ошибка"
            tvTargetStatus.setTextColor(Color.parseColor("#F44336"))
        }
    }

    private fun downloadModelsAndStart() {
        btnStart.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val sourceMlModel = TranslateRemoteModel.Builder(sourceLang.code).build()
        val targetMlModel = TranslateRemoteModel.Builder(targetLang.code).build()
        val conditions = DownloadConditions.Builder().build()

        var sourceMlDone = false
        var targetMlDone = false
        var voskDone = voskManager.isModelReady(sourceLang.code)

        fun checkAndStart() {
            if (sourceMlDone && targetMlDone && voskDone) {
                progressBar.visibility = View.INVISIBLE
                btnStart.isEnabled = true
                val intent = Intent(this, TranslatorActivity::class.java)
                intent.putExtra("from_lang", sourceLang.code)
                intent.putExtra("to_lang", targetLang.code)
                startActivity(intent)
                finish()
            }
        }

        // Download Vosk if needed
        if (!voskDone) {
            btnStart.text = "Скачиваю модель распознавания ${sourceLang.code.uppercase()}..."
            lifecycleScope.launch {
                try {
                    voskManager.downloadAndExtractModel(sourceLang.code) { progress ->
                        btnStart.text = "Скачиваю модель распознавания ${sourceLang.code.uppercase()}... $progress%"
                    }
                    voskDone = true
                    checkModelsStatus()
                    checkAndStart()
                } catch (e: Exception) {
                    progressBar.visibility = View.INVISIBLE
                    btnStart.isEnabled = true
                    btnStart.text = "Начать"
                    Toast.makeText(this@LanguageSelectActivity, "Ошибка Vosk: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Download Source ML
        mlModelManager.download(sourceMlModel, conditions).addOnSuccessListener {
            sourceMlDone = true
            checkModelsStatus()
            if (voskDone && targetMlDone && sourceMlDone) {
                checkAndStart()
            } else if (!sourceMlDone || !targetMlDone) {
                btnStart.text = "Скачиваю модель перевода..."
            }
        }.addOnFailureListener { e ->
            progressBar.visibility = View.INVISIBLE
            btnStart.isEnabled = true
            btnStart.text = "Начать"
            Toast.makeText(this, "Ошибка загрузки ML", Toast.LENGTH_SHORT).show()
        }

        // Download Target ML
        mlModelManager.download(targetMlModel, conditions).addOnSuccessListener {
            targetMlDone = true
            checkModelsStatus()
            if (voskDone && targetMlDone && sourceMlDone) {
                checkAndStart()
            } else if (!sourceMlDone || !targetMlDone) {
                btnStart.text = "Скачиваю модель перевода..."
            }
        }.addOnFailureListener { e ->
            progressBar.visibility = View.INVISIBLE
            btnStart.isEnabled = true
            btnStart.text = "Начать"
            Toast.makeText(this, "Ошибка загрузки ML", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class LanguageAdapter(
        private val list: List<LanguageItem>,
        private val onClick: (LanguageItem) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val emoji: TextView = view.findViewById(R.id.tvItemEmoji)
            val name: TextView = view.findViewById(R.id.tvItemName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.emoji.text = item.emoji
            holder.name.text = item.name
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
