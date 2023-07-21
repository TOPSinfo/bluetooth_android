package com.example.ble_app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.essence.commincation.bluetooth.BluetoothService
import com.essence.commincation.utilis.Constants
import com.example.ble_app.databinding.ActivityResponseBinding


/**
 *  This activity write and received data from
 *  Peripherals
 * */
class ResponseActivity : AppCompatActivity() {

    private val mBluetoothService by lazy {
        BluetoothService.getInstance()
    }

    private lateinit var binding: ActivityResponseBinding

    private var readList = ArrayList<String>()

    private  lateinit var mBluetoothAdapter: BluetoothAdapter

    private var deviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityResponseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mBluetoothAdapter =
            (getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        val deviceAddress = intent.getStringExtra("device")
        deviceAddress?.let {
            val device = mBluetoothAdapter.getRemoteDevice(deviceAddress)
            mBluetoothService?.connect(device, true)
        }
        mBluetoothService?.updateHandler(mHandler)


        binding.sendTextIV.setOnClickListener {
            mBluetoothService?.write(
                binding.sendTextET.text.toString().toByteArray()
            )
        }

        binding.txtDisconnect.setOnClickListener {
            onBackPressed()
        }


    }

    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {

            try {
                when (msg.what) {

                    Constants.MESSAGE_READ -> {

                        val readBuf = msg.obj as ByteArray
                        var readMessage = String(readBuf)
                        readList.add(readMessage.filter { it.isLetterOrDigit() })
                        binding.receiveTextET.append(
                            "${readList[readList.size - 1]} \n"
                        )
                    }
                    Constants.MESSAGE_TOAST -> {

                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }

            readList.clear()
        }


    }

    companion object {
        private const val TAG = "ResponseClass"
    }

    override fun onBackPressed() {
        if (mBluetoothService != null){
            mBluetoothService?.stop()
            Toast.makeText(this,
                "Device disconnected...", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBluetoothService != null){
            mBluetoothService?.stop()
        }
    }
}