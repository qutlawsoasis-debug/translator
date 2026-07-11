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
import com.magne.translator.usb.UsbCommandManager

class MainActivity : Activity() {

    private lateinit var usbManager: UsbCommandManager
    private lateinit var systemUsbManager: UsbManager

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

        val btnStart = findViewById<Button>(R.id.btnListen)
        btnStart.setOnClickListener {
            val intent = Intent(this, TranslatorActivity::class.java)
            startActivity(intent)
        }

        usbManager.startListening { command ->
            Log.d("USB", "Received: $command")
            when (command) {
                "CHECK_UPDATE" -> usbManager.send("UPDATE_NONE")
                "CHECK_MODELS" -> usbManager.send("MODELS_OK")
                "START_TRANSLATOR" -> {
                    val intent = Intent(this, TranslatorActivity::class.java)
                    startActivity(intent)
                }
                "CLOSE" -> finishAffinity()
            }
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
            Log.d("USB", "Sending APP_READY")
            usbManager.send("APP_READY")
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
