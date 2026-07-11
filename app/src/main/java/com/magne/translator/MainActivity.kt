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
import android.widget.ScrollView
import android.widget.TextView
import com.magne.translator.usb.UsbCommandManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var usbManager: UsbCommandManager
    private lateinit var tvLogs: TextView
    private lateinit var svLogs: ScrollView
    private lateinit var systemUsbManager: UsbManager

    companion object {
        private const val ACTION_USB_PERMISSION = "com.magne.translator.USB_PERMISSION"
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    addLog("USB: устройство отключено")
                    usbManager.disconnect()
                    finishAffinity()
                }
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                addLog("USB: разрешение получено")
                                connectToDevice(it)
                            }
                        } else {
                            addLog("USB: разрешение отклонено")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLogs = findViewById(R.id.tvLogs)
        svLogs = findViewById(R.id.svLogs)
        systemUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        
        usbManager = UsbCommandManager(this) { msg -> addLog(msg) }

        val tvSource = findViewById<TextView>(R.id.tvSource)
        tvSource.text = "Подключение к плате..."

        val btnStart = findViewById<Button>(R.id.btnListen)
        btnStart.setOnClickListener {
            val intent = Intent(this, TranslatorActivity::class.java)
            startActivity(intent)
        }

        usbManager.startListening { command ->
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
        registerReceiver(usbReceiver, filter)
    }

    private fun checkUsbDevices() {
        val deviceList = systemUsbManager.deviceList
        val targetDevice = deviceList.values.find { it.vendorId == 4919 && it.productId == 2 }
        
        if (targetDevice != null) {
            addLog("USB: устройство найдено VID=${targetDevice.vendorId} PID=${targetDevice.productId}")
            if (systemUsbManager.hasPermission(targetDevice)) {
                addLog("USB: разрешение уже есть")
                connectToDevice(targetDevice)
            } else {
                addLog("USB: запрос разрешения...")
                val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flag)
                systemUsbManager.requestPermission(targetDevice, permissionIntent)
            }
        } else {
            addLog("USB: Плата не найдена в системе")
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        if (usbManager.connect(device, systemUsbManager)) {
            findViewById<TextView>(R.id.tvSource).text = "Связь установлена!"
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

    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMsg = "[$time] $message"
        Log.d("USB_DEBUG", logMsg)
        
        runOnUiThread {
            tvLogs.append(logMsg + "\n")
            svLogs.post { svLogs.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
}
