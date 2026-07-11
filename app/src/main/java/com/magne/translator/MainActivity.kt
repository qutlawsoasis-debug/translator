package com.magne.translator

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.TextView
import com.magne.translator.usb.UsbCommandManager

class MainActivity : Activity() {

    private lateinit var usbManager: UsbCommandManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                usbManager.disconnect()
                finishAffinity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = UsbCommandManager(this)

        val tvSource = findViewById<TextView>(R.id.tvSource)
        tvSource.text = "Подключение к плате..."

        // Обработчик для кнопки "Начать" (Fallback)
        val btnStart = findViewById<android.widget.Button>(R.id.btnListen)
        btnStart.setOnClickListener {
            val intent = Intent(this, TranslatorActivity::class.java)
            startActivity(intent)
        }

        usbManager.startListening { command ->
            when (command) {
                "CHECK_UPDATE" -> {
                    // Пока нет логики обновлений, сразу говорим, что их нет
                    usbManager.send("UPDATE_NONE")
                }
                "CHECK_MODELS" -> {
                    // Пока нет проверки моделей, говорим, что всё ок
                    usbManager.send("MODELS_OK")
                }
                "START_TRANSLATOR" -> {
                    val intent = Intent(this, TranslatorActivity::class.java)
                    startActivity(intent)
                }
                "CLOSE" -> {
                    finishAffinity()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)

        // Пытаемся подключиться к плате и отправляем APP_READY
        if (usbManager.connect()) {
            val tvSource = findViewById<TextView>(R.id.tvSource)
            tvSource.text = "Связь установлена!"
            usbManager.send("APP_READY")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(usbReceiver)
        usbManager.disconnect()
    }
}
