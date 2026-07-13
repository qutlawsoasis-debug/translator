package com.magne.translator

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

data class LanguageItem(val name: String, val code: String, val emoji: String)

class LanguageSelectActivity : AppCompatActivity() {

    private val languages = listOf(
        LanguageItem("Русский", "ru", "🇷🇺"),
        LanguageItem("Английский", "en", "🇬🇧"),
        LanguageItem("Немецкий", "de", "🇩🇪"),
        LanguageItem("Французский", "fr", "🇫🇷"),
        LanguageItem("Испанский", "es", "🇪🇸"),
        LanguageItem("Итальянский", "it", "🇮🇹"),
        LanguageItem("Китайский", "zh", "🇨🇳"),
        LanguageItem("Корейский", "ko", "🇰🇷"),
        LanguageItem("Японский", "ja", "🇯🇵"),
        LanguageItem("Португальский", "pt", "🇵🇹")
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
    private lateinit var btnCancel: Button
    private lateinit var progressBar: LinearProgressIndicator

    private var sourceLang: LanguageItem = languages[0]
    private var targetLang: LanguageItem = languages[1]

    private val modelManager by lazy { ModelDownloadManager(this) }

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
        btnCancel = findViewById(R.id.btnCancel)
        progressBar = findViewById(R.id.progressBar)

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedSource = prefs.getString("pref_from_lang", "ru")
        val savedTarget = prefs.getString("pref_to_lang", "en")

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

            if (modelManager.areAllModelsReady()) {
                startTranslator()
            } else {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Загрузка моделей")
                    .setMessage("Для оффлайн работы необходимо скачать модели (~1.3GB).\nРекомендуем использовать Wi-Fi.\nНачать загрузку?")
                    .setPositiveButton("Скачать") { _, _ -> startDownload() }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }

        btnCancel.setOnClickListener {
            modelManager.cancelDownloads()
            progressBar.visibility = View.INVISIBLE
            btnCancel.visibility = View.GONE
            btnStart.isEnabled = true
            btnStart.text = "Начать"
            updateUI()
        }
    }

    private fun updateUI() {
        tvSourceEmoji.text = sourceLang.emoji
        tvSourceName.text = sourceLang.name
        tvTargetEmoji.text = targetLang.emoji
        tvTargetName.text = targetLang.name
        
        val isReady = modelManager.areAllModelsReady()
        if (isReady) {
            tvSourceStatus.text = "✅ Нейросети загружены"
            tvSourceStatus.setTextColor(Color.parseColor("#4CAF50"))
            tvTargetStatus.text = "Готово к переводу"
            tvTargetStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvSourceStatus.text = "⬇ Требуется загрузка"
            tvSourceStatus.setTextColor(Color.parseColor("#FFC107"))
            tvTargetStatus.text = "~1.3GB моделей"
            tvTargetStatus.setTextColor(Color.parseColor("#FFC107"))
        }
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

    private fun startDownload() {
        btnStart.isEnabled = false
        btnCancel.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        modelManager.startDownload(
            onProgress = { progressMsg ->
                btnStart.text = progressMsg
            },
            onSuccess = {
                progressBar.visibility = View.INVISIBLE
                btnCancel.visibility = View.GONE
                btnStart.isEnabled = true
                btnStart.text = "Начать"
                startTranslator()
            },
            onError = { errorMsg ->
                progressBar.visibility = View.INVISIBLE
                btnCancel.visibility = View.GONE
                btnStart.isEnabled = true
                btnStart.text = "Начать"
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                updateUI()
            }
        )
    }

    private fun startTranslator() {
        val intent = Intent(this, TranslatorActivity::class.java)
        intent.putExtra("from_lang", sourceLang.code)
        intent.putExtra("to_lang", targetLang.code)
        startActivity(intent)
        finish()
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
