package com.example.ble_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.essence.commincation.bluetooth.BluetoothService
import com.essence.commincation.utilis.Constants
import com.example.ble_app.adapter.PairedDeviceListAdapter
import com.example.ble_app.databinding.ActivityDeviceListBinding
import com.google.gson.Gson
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission


/**
*  This activity used to show device list and connect
* */
class DeviceListActivity : AppCompatActivity(), View.OnClickListener,
    BluetoothService.BluetoothServiceListener {

    private lateinit var binding: ActivityDeviceListBinding
    private lateinit var context: Context


    private var pairedDeviceList: ArrayList<BluetoothDevice> = ArrayList()
    private var newDeviceList: ArrayList<BluetoothDevice> = ArrayList()

    private var pairedDeviceListAdapter: PairedDeviceListAdapter? = null
    private var newDeviceListAdapter: PairedDeviceListAdapter? = null

    private var mConnectedDeviceName: String? = null
    private var mConnectedDeviceAddress: String? = null

    private var btService: BluetoothService? = null
    private var connectedDevice = false


    private lateinit var mBluetoothAdapter: BluetoothAdapter

    public val mBluetoothService by lazy {
        BluetoothService.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@DeviceListActivity

        askBluetoothPermission()


    }

    override fun onBackPressed() {
        mBluetoothService!!.stopDiscoveries()
        connectedDevice = mBluetoothService!!.isConnectionOpen
        super.onBackPressed()
    }

    /**
     * Declare handler for get state of connected device
     */
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            try {
                when (msg.what) {
                    Constants.MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                        BluetoothService.STATE_CONNECTED -> {
                            connectedDevice = true

                        }
                        BluetoothService.STATE_CONNECTING -> {

                        }
                        BluetoothService.STATE_LISTEN, BluetoothService.STATE_NONE -> {

                            if (mConnectedDeviceAddress != null) {
                                Toast.makeText(
                                    context, "Disconnect to device.", Toast.LENGTH_SHORT
                                ).show()
                            }

                            connectedDevice = false

                            mConnectedDeviceAddress = null
                            pairedDeviceListAdapter?.connectAddress(mConnectedDeviceAddress)
                            newDeviceListAdapter?.connectAddress(mConnectedDeviceAddress)
                        }
                    }

                    Constants.MESSAGE_DEVICE_NAME -> {
                        // save the connected device's name
                        mConnectedDeviceName = msg.data.getString(Constants.DEVICE_NAME)
                        mConnectedDeviceAddress = msg.data.getString(Constants.DEVICE_ADDRESS)
                        Toast.makeText(
                            context, "Connected to "
                                    + mConnectedDeviceName, Toast.LENGTH_SHORT
                        ).show()

                        connectedDevice = true
                        pairedDeviceListAdapter?.connectAddress(mConnectedDeviceAddress)
                        newDeviceListAdapter?.connectAddress(mConnectedDeviceAddress)

                    }
                    Constants.MESSAGE_WRITE -> {
                        val writeBuf = msg.obj as ByteArray
                        // construct a string from the buffer
                        val writeMessage = String(writeBuf)
                    }
                    Constants.MESSAGE_READ -> {
                        val readBuf = msg.obj as ByteArray
                        // construct a string from the valid bytes in the buffer
                        val readMessage = String(readBuf, 0, msg.arg1)
                    }
                    Constants.MESSAGE_TOAST -> {

                    }

                }
            } catch (e: Exception) {
                Log.e("errrorrr", e.toString())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesList() {
        pairedDeviceList.clear()

        val pairedArrayList = mBluetoothService?.onPairedDeviceList()
        Log.e("pairedArrayList", Gson().toJson(pairedArrayList))

        pairedArrayList.let {
            if (it != null) {
                pairedDeviceList.addAll(it)
            }
        }
        if (pairedDeviceList.isNotEmpty()) {
            pairedDeviceListAdapter?.notifyDataSetChanged()
        }

        if (pairedDeviceList.size > 0) {
            binding.txtNoDeviceFound.visibility = View.GONE
            binding.rvPairedDevices.visibility = View.VISIBLE
        } else {
            binding.txtNoDeviceFound.visibility = View.VISIBLE
            binding.rvPairedDevices.visibility = View.GONE
        }
        setPairedDeviceAdapter()

    }


    /**
     * Setup paired devices adapter
     */
    private fun setPairedDeviceAdapter() {

        binding.rvPairedDevices.layoutManager = LinearLayoutManager(context)
        pairedDeviceListAdapter = PairedDeviceListAdapter(
            context,
            pairedDeviceList, mConnectedDeviceAddress,
            object : PairedDeviceListAdapter.PairedConnectDeviceListener {
                override fun onItemClick(device: BluetoothDevice) {
                    connectDevice(device.address, true)
                }

                override fun onItemDisconnect() {
                    mBluetoothService?.stop()
                }
            })
        binding.rvPairedDevices.adapter = pairedDeviceListAdapter
        binding.rvPairedDevices.isNestedScrollingEnabled = false

    }


    private fun initUI() {

        btService = BluetoothService(context, mHandler, this)
        btService!!.discoverDevices()

        binding.txtDiscoverDevice.setOnClickListener(this)
        mBluetoothService?.updateListener(this)
        mBluetoothService?.updateHandler(mHandler)
        mBluetoothService?.onPairedDeviceList()

        mBluetoothAdapter =
            (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter


        if (!mBluetoothAdapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableIntent)
        } else {
            showPairedDevicesList()
            setNewDeviceAdapter()
        }
    }

    private var bluetoothEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                //  val data: Intent? = result.data
                initUI()
            }
        }

    private fun setNewDeviceAdapter() {
        binding.rvNewDevices.layoutManager = LinearLayoutManager(context)
        newDeviceListAdapter =
            PairedDeviceListAdapter(context, newDeviceList, mConnectedDeviceAddress,
                object : PairedDeviceListAdapter.PairedConnectDeviceListener {

                    override fun onItemClick(device: BluetoothDevice) {
                        connectDevice(device.address, true)
                    }

                    override fun onItemDisconnect() {
                        mBluetoothService?.stop()
                    }
                })
        binding.rvNewDevices.adapter = newDeviceListAdapter
        binding.rvNewDevices.isNestedScrollingEnabled = false


    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.txtDiscoverDevice -> {
                btService!!.discoverDevices()
            }
        }
    }


    override fun onDiscoverStarted() {

    }

    override fun onDiscoverFinished(device: BluetoothDevice?) {
        if (device != null) {
            Log.e("NewDeviceArrayList", Gson().toJson(device))
            newDeviceListAdapter?.addDevice(device)
            if (newDeviceList.size > 0) {
                binding.txtNoDeviceFoundNew.visibility = View.GONE
                binding.rvNewDevices.visibility = View.VISIBLE
            } else {
                binding.txtNoDeviceFoundNew.visibility = View.VISIBLE
                binding.rvNewDevices.visibility = View.GONE
            }
            Handler(Looper.getMainLooper()).postDelayed({

            }, 1500)
        }
    }


    /**
     * Connecting to other device
     */

    private fun connectDevice(deviceAddress: String?, secure: Boolean) {
        try {
            if (mConnectedDeviceAddress != null) {
                mConnectedDeviceAddress = null
            }
            mConnectedDeviceAddress = deviceAddress
            val device = mBluetoothAdapter.getRemoteDevice(deviceAddress)
            mBluetoothService?.connect(device, secure)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun askBluetoothPermission() {
        mBluetoothAdapter =
            (getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val permissionListener: PermissionListener = object : PermissionListener {
            override fun onPermissionGranted() {
                if (!mBluetoothAdapter.isEnabled) {
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bluetoothEnableLauncher.launch(enableIntent)
                } else {
                    initUI()
                }

            }

            override fun onPermissionDenied(deniedPermissions: List<String>) {
                Toast.makeText(
                    this@DeviceListActivity,
                    "Permission Denied\n$deniedPermissions",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            TedPermission.with(this)
                .setPermissionListener(permissionListener)
                .setDeniedMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Setting] > [Permission]")
                .setPermissions(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .check()
        else
            TedPermission.with(this)
                .setPermissionListener(permissionListener)
                .setDeniedMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Setting] > [Permission]")
                .setPermissions(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .check()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        if (mBluetoothService != null) {
            mBluetoothService?.stop()
        }
        mBluetoothAdapter.cancelDiscovery()

    }
}