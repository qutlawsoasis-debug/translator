package com.magne.translator

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.ImageView
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.magne.translator.usb.UsbCommandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.progressindicator.CircularProgressIndicator

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbCommandManager
    private lateinit var systemUsbManager: UsbManager
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val updateManager by lazy { UpdateManager(this) }
    private var updateResult: UpdateResult? = null

    private lateinit var tvStatus: TextView
    private lateinit var ivLogo: ImageView
    private lateinit var tvAppName: TextView
    private lateinit var tvVersion: TextView
    private lateinit var progressBar: CircularProgressIndicator
    
    private var isTranslatorRunning = false

    companion object {
        private const val ACTION_USB_PERMISSION = "com.magne.translator.USB_PERMISSION"
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d("USB", "Устройство отключено")
                    usbManager.disconnect()
                    finishAffinity()
                }
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        Log.d("USB", "Permission granted: $granted")
                        
                        if (granted) {
                            device?.let {
                                connectToDevice(it)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        systemUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        usbManager = UsbCommandManager(this) { msg -> Log.d("USB", msg) }

        tvStatus = findViewById(R.id.tvStatus)
        ivLogo = findViewById(R.id.ivLogo)
        tvAppName = findViewById(R.id.tvAppName)
        tvVersion = findViewById(R.id.tvVersion)
        progressBar = findViewById(R.id.progressBar)

        tvVersion.text = "Версия ${BuildConfig.VERSION_NAME}"
        tvStatus.text = "Подключение к плате..."

        // Animate elements (fade-in)
        ivLogo.animate().alpha(1f).setDuration(800).start()
        tvAppName.animate().alpha(1f).setDuration(800).setStartDelay(200).start()
        tvVersion.animate().alpha(1f).setDuration(800).setStartDelay(400).start()
        progressBar.animate().alpha(1f).setDuration(800).setStartDelay(600).start()
        tvStatus.animate().alpha(1f).setDuration(800).setStartDelay(800).start()
        
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun checkUsbDevices() {
        val deviceList = systemUsbManager.deviceList
        val targetDevice = deviceList.values.find { it.vendorId == 4919 && it.productId == 2 }
        
        if (targetDevice != null) {
            Log.d("USB", "Device found: $targetDevice")
            if (systemUsbManager.hasPermission(targetDevice)) {
                Log.d("USB", "Разрешение уже есть")
                connectToDevice(targetDevice)
            } else {
                Log.d("USB", "Запрос разрешения...")
                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flag)
                systemUsbManager.requestPermission(targetDevice, permissionIntent)
            }
        } else {
            Log.d("USB", "Плата не найдена в системе")
            tvStatus.text = "Ожидание подключения..."
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        if (usbManager.connect(device, systemUsbManager)) {
            tvStatus.text = "Связь установлена!"
            
            usbManager.startListening { command ->
                Log.d("USB", "=> Обработка команды в UI потоке: [$command]")
                runOnUiThread {
                    when (command) {
                        "HELLO" -> {
                            if (!isTranslatorRunning) {
                                Log.d("USB", "Получили HELLO, отвечаем APP_READY")
                                usbManager.send("APP_READY")
                            } else {
                                Log.d("USB", "Получили HELLO, но переводчик уже запущен. Игнорируем.")
                            }
                        }
                        "CHECK_UPDATE" -> {
                            tvStatus.text = "Проверка обновлений..."
                            Log.d("USB", "Получили CHECK_UPDATE, проверяем обновления")
                            mainScope.launch {
                                updateResult = updateManager.checkUpdate()
                                if (updateResult != null) {
                                    usbManager.send("UPDATE_AVAILABLE")
                                } else {
                                    usbManager.send("UPDATE_NONE")
                                }
                            }
                        }
                        "SHOW_UPDATE_DIALOG" -> {
                            Log.d("USB", "Получили SHOW_UPDATE_DIALOG, показываем кастомный диалог")
                            updateResult?.let { result ->
                                val dialogView = layoutInflater.inflate(R.layout.dialog_update, null)
                                val tvUpdateTitle = dialogView.findViewById<TextView>(R.id.tvUpdateTitle)
                                val pbUpdate = dialogView.findViewById<android.widget.ProgressBar>(R.id.pbUpdate)
                                val tvUpdateProgress = dialogView.findViewById<TextView>(R.id.tvUpdateProgress)
                                
                                tvUpdateTitle.text = "Загрузка версии ${result.version}..."
                                
                                val updateDialog = AlertDialog.Builder(this@MainActivity)
                                    .setView(dialogView)
                                    .setCancelable(false)
                                    .create()
                                
                                updateDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                                updateDialog.show()
                                
                                mainScope.launch {
                                    updateManager.downloadAndInstallSilent(result) { progress ->
                                        runOnUiThread { 
                                            pbUpdate.progress = progress
                                            tvUpdateProgress.text = "$progress%" 
                                        }
                                    }
                                    runOnUiThread { updateDialog.dismiss() }
                                }
                            } ?: run {
                                usbManager.send("UPDATE_NONE")
                            }
                        }
                        "CHECK_MODELS" -> {
                            tvStatus.text = "Проверка моделей..."
                            Log.d("USB", "Получили CHECK_MODELS, отвечаем MODELS_OK")
                            usbManager.send("MODELS_OK")
                        }
                        "START_TRANSLATOR" -> {
                            tvStatus.text = "Запуск переводчика..."
                            Log.d("USB", "Получили START_TRANSLATOR, запускаем активити")
                            isTranslatorRunning = true
                            val prefs = getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
                            if (prefs.contains("pref_from_lang") && prefs.contains("pref_to_lang")) {
                                val intent = Intent(this@MainActivity, TranslatorActivity::class.java)
                                intent.putExtra("from_lang", prefs.getString("pref_from_lang", ""))
                                intent.putExtra("to_lang", prefs.getString("pref_to_lang", ""))
                                startActivity(intent)
                            } else {
                                val intent = Intent(this@MainActivity, LanguageSelectActivity::class.java)
                                startActivity(intent)
                            }
                        }
                        "CLOSE" -> {
                            Log.d("USB", "Получили CLOSE, закрываем приложение")
                            finishAffinity()
                        }
                        else -> {
                            Log.d("USB", "Неизвестная команда: [$command]")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkUsbDevices()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}
        usbManager.disconnect()
    }
}
