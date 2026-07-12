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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel

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

    private val modelManager = RemoteModelManager.getInstance()

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
        checkSingleModelStatus(sourceLang.code, tvSourceStatus)
        checkSingleModelStatus(targetLang.code, tvTargetStatus)
    }

    private fun checkSingleModelStatus(langCode: String, statusView: TextView) {
        statusView.text = "⏳ Проверка..."
        statusView.setTextColor(Color.parseColor("#AAAAAA"))
        val model = TranslateRemoteModel.Builder(langCode).build()
        modelManager.isModelDownloaded(model).addOnSuccessListener { isDownloaded ->
            if (isDownloaded) {
                statusView.text = "✅ Готово"
                statusView.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                statusView.text = "⬇ ~30MB"
                statusView.setTextColor(Color.parseColor("#FFC107"))
            }
        }.addOnFailureListener {
            statusView.text = "❌ Ошибка"
            statusView.setTextColor(Color.parseColor("#F44336"))
        }
    }

    private fun downloadModelsAndStart() {
        btnStart.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val sourceModel = TranslateRemoteModel.Builder(sourceLang.code).build()
        val targetModel = TranslateRemoteModel.Builder(targetLang.code).build()
        val conditions = DownloadConditions.Builder().build()

        var sourceDone = false
        var targetDone = false

        fun checkAndStart() {
            if (sourceDone && targetDone) {
                progressBar.visibility = View.INVISIBLE
                btnStart.isEnabled = true
                val intent = Intent(this, TranslatorActivity::class.java)
                intent.putExtra("from_lang", sourceLang.code)
                intent.putExtra("to_lang", targetLang.code)
                startActivity(intent)
                finish()
            }
        }

        modelManager.download(sourceModel, conditions).addOnSuccessListener {
            sourceDone = true
            checkSingleModelStatus(sourceLang.code, tvSourceStatus)
            checkAndStart()
        }.addOnFailureListener { e ->
            progressBar.visibility = View.INVISIBLE
            btnStart.isEnabled = true
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
        }

        modelManager.download(targetModel, conditions).addOnSuccessListener {
            targetDone = true
            checkSingleModelStatus(targetLang.code, tvTargetStatus)
            checkAndStart()
        }.addOnFailureListener { e ->
            progressBar.visibility = View.INVISIBLE
            btnStart.isEnabled = true
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
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
