package com.magne.translator

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                // Если плата вытащена, немедленно закрываем приложение
                finishAffinity()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Меняем текст, чтобы пользователь понял, что мы перешли на Stage 2
        val tvSource = findViewById<TextView>(R.id.tvSource)
        tvSource.text = "Приложение успешно перехвачено!"

        // Обработчик для кнопки "Начать"
        val btnStart = findViewById<android.widget.Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            android.widget.Toast.makeText(this, "Переход на экран перевода...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Регистрируем слушатель отключения USB (начиная с Android 13 нужно указывать флаг, но тут просто)
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(usbReceiver)
    }
}
