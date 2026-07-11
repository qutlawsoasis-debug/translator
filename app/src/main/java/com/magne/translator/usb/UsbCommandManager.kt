package com.magne.translator.usb

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsbCommandManager(private val context: Context) : SerialInputOutputManager.Listener {

    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var commandCallback: ((String) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var buffer = StringBuilder()

    fun connect(): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        
        if (availableDrivers.isEmpty()) {
            return false
        }

        // Берем первый доступный драйвер (наша плата)
        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device) ?: return false

        serialPort = driver.ports[0]
        try {
            serialPort?.open(connection)
            serialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            ioManager = SerialInputOutputManager(serialPort, this)
            ioManager?.start()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun startListening(onCommand: (String) -> Unit) {
        commandCallback = onCommand
    }

    fun send(response: String) {
        val data = "$response\n".toByteArray()
        try {
            serialPort?.write(data, 1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewData(data: ByteArray?) {
        data?.let {
            val str = String(it)
            buffer.append(str)
            
            var newlineIndex = buffer.indexOf('\n')
            while (newlineIndex != -1) {
                val command = buffer.substring(0, newlineIndex).trim()
                buffer.delete(0, newlineIndex + 1)
                
                if (command.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.Main) {
                            commandCallback?.invoke(command)
                        }
                    }
                }
                newlineIndex = buffer.indexOf('\n')
            }
        }
    }

    override fun onRunError(e: Exception?) {
        e?.printStackTrace()
    }

    fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try {
            serialPort?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serialPort = null
    }
}
