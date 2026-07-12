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
import android.app.AlertDialog
import android.app.ProgressDialog
import com.magne.translator.usb.UsbCommandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private lateinit var usbManager: UsbCommandManager
    private lateinit var systemUsbManager: UsbManager
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val updateManager by lazy { UpdateManager(this) }
    private var updateResult: UpdateResult? = null

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

        val tvSource = findViewById<TextView>(R.id.tvSource)
        tvSource.text = "Подключение к плате..."

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = "Версия: ${BuildConfig.VERSION_NAME}"

        val btnStart = findViewById<Button>(R.id.btnListen)
        btnStart.setOnClickListener {
            val intent = Intent(this, TranslatorActivity::class.java)
            startActivity(intent)
        }
        
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
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        if (usbManager.connect(device, systemUsbManager)) {
            findViewById<TextView>(R.id.tvSource).text = "Связь установлена!"
            
            usbManager.startListening { command ->
                Log.d("USB", "=> Обработка команды в UI потоке: [$command]")
                runOnUiThread {
                    when (command) {
                        "HELLO" -> {
                            Log.d("USB", "Получили HELLO, отвечаем APP_READY")
                            usbManager.send("APP_READY")
                        }
                        "CHECK_UPDATE" -> {
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
                            Log.d("USB", "Получили SHOW_UPDATE_DIALOG, запускаем тихое обновление")
                            updateResult?.let { result ->
                                @Suppress("DEPRECATION")
                                val progressDialog = ProgressDialog(this@MainActivity).apply {
                                    setTitle("Обновление")
                                    setMessage("Загрузка версии ${result.version}...")
                                    setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                                    max = 100
                                    setCancelable(false)
                                    show()
                                }
                                mainScope.launch {
                                    updateManager.downloadAndInstallSilent(result) { progress ->
                                        runOnUiThread { progressDialog.progress = progress }
                                    }
                                    runOnUiThread { progressDialog.dismiss() }
                                }
                            } ?: run {
                                usbManager.send("UPDATE_NONE")
                            }
                        }
                        "CHECK_MODELS" -> {
                            Log.d("USB", "Получили CHECK_MODELS, отвечаем MODELS_OK")
                            usbManager.send("MODELS_OK")
                        }
                        "START_TRANSLATOR" -> {
                            Log.d("USB", "Получили START_TRANSLATOR, запускаем Activity")
                            val intent = Intent(this, TranslatorActivity::class.java)
                            startActivity(intent)
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
