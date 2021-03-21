package com.shyam.androidjssc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.tunjid.androidx.core.delegates.intentExtras
import jssc.SerialPort
import jssc.SerialPortEventListener
import jssc.SerialPortException

/**
 * Main activity.
 */
class MainActivity : AppCompatActivity() {

    private var serialPort: SerialPort? = null

    private val mSerialPortEventListener = SerialPortEventListener { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findUsbDriver()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let(Intent::usbDevice)?.let(::openSerialPort)
    }

    override fun onDestroy() {
        try {
            serialPort?.apply {
                synchronized(this) {
                    removeEventListener()
                    closePort()
                    serialPort = null
                }
                Log.i(TAG, "Serial port closed.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing serial port", e)
        }
        super.onDestroy()
    }

    /**
     * Write a sample string to serial port.
     */
    private fun write() {
        if (null != serialPort) {
            try {
                serialPort!!.writeBytes("Hello".toByteArray())
            } catch (e: SerialPortException) {
                Log.e(TAG, "Exception while writing", e)
            }
        }
    }

    /**
     * Open serial port.
     */
    private fun openSerialPort(device: UsbDevice) {
        try {
            serialPort = SerialPort(device.deviceName).apply {
                openPort()
                setParams(BAUD_RATE, 8, 1, 0)
                flowControlMode = SerialPort.FLOWCONTROL_XONXOFF_OUT
                addEventListener(mSerialPortEventListener)
            }
        } catch (e: SerialPortException) {
            Log.e(TAG, "Unable to open port", e)
        }
    }

    private fun findUsbDriver() {
        val manager = getSystemService<UsbManager>() ?: return
        val (name, device) = manager.deviceList
            .filterValues { device ->
                device.vendorId == ARDUINO_VENDOR_ID && device.productId == ARDUINO_PRODUCT_ID
            }
            .toList()
            .firstOrNull()
            ?: return


        if (manager.hasPermission(device)) openSerialPort(device)

        val pending = PendingIntent.getBroadcast(this, requestCode, Intent(ActionUsbPermission), PendingIntent.FLAG_CANCEL_CURRENT)
        manager.requestPermission(device, pending)
    }

    companion object {
        private val TAG = MainActivity::class.java.canonicalName
        private const val BAUD_RATE = 57600
        const val ARDUINO_VENDOR_ID = 0x2341
        const val ARDUINO_PRODUCT_ID = 0x0010
        const val requestCode = 2
    }
}

const val ActionUsbPermission = "ACTION_USB_PERMISSION"

private var Intent.usbDevice by intentExtras<UsbDevice?>()

class USBDeviceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = when (ActionUsbPermission) {
        intent.action -> synchronized(this) {
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                )
            }
        }
        else -> Unit
    }
}