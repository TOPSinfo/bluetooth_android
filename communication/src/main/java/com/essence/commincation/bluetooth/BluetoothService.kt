/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.essence.commincation.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.widget.Toast
import com.essence.commincation.api.maintanance.layer.CCommunicationLayer
import com.essence.commincation.utilis.Constants
import com.essence.commincation.utilis.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
class BluetoothService(context: Context, handler: Handler?, listener: BluetoothServiceListener?) :
    CCommunicationLayer {
    // Member fields
    private var mAdapter: BluetoothAdapter? = null
    private var mHandler: Handler? = handler
    private var mSecureAcceptThread: AcceptThread? = null
    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    var mState = 0
    private var mNewState = 0
    private var mContext: Context? = context
    private val discoverArrayList: ArrayList<BluetoothDevice?> = ArrayList()
    private var bluetoothServiceListener: BluetoothServiceListener? = listener


    private var _btSocket: BluetoothSocket? = null
    private var _inputstream: InputStream? = null
    private var _outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null

    fun updateListener(listener: BluetoothServiceListener?) {
        bluetoothServiceListener = listener
    }

    fun updateHandler(handler: Handler?) {
        mHandler = handler
    }


    init {
        mAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        mState = STATE_NONE
        mNewState = mState
        if (instance == null) {
            instance = this
        }
        init()
    }


    /**
     * Return the current connection state.
     */
    @Synchronized
    fun getState(): Int {
        return mState
    }


    /**
     * Update UI title according to the current state of the chat connection
     */
    @Synchronized
    private fun updateUserInterfaceTitle() {
        mState = getState()
        Log.d(TAG, "updateUserInterfaceTitle() $mNewState -> $mState")
        mNewState = mState

        // Give the new state to the Handler so the UI Activity can update
        mHandler!!.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget()
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start() {
        Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = AcceptThread(true)
            mSecureAcceptThread!!.start()
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = AcceptThread(false)
            mInsecureAcceptThread!!.start()
        }

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    @Synchronized
    fun connect(device: BluetoothDevice, secure: Boolean) {
        Log.d(TAG, "connect to: $device")

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device, secure)
        mConnectThread!!.start()
        // Update UI title
        updateUserInterfaceTitle()
    }


    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice, socketType: String) {
        Log.d(TAG, "connected, Socket Type:$socketType")

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket, socketType)
        mConnectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = mHandler!!.obtainMessage(Constants.MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(Constants.DEVICE_NAME, device.name)
        bundle.putString(Constants.DEVICE_ADDRESS, device.address)
        msg.data = bundle
        mHandler!!.sendMessage(msg)
        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }
        mState = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Write to the ConnectedThread in an Synchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    /* fun write(out: ByteArray?) {
         // Create temporary object
         var r: ConnectedThread?
         // Synchronize a copy of the ConnectedThread
         synchronized(this) {
             if (mState != STATE_CONNECTED) return
             r = mConnectedThread
         }
         // Perform the write Synchronized
         r!!.write(out)
     }
 */
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        // Send a failure message back to the Activity
        val msg = mHandler!!.obtainMessage(Constants.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(Constants.TOAST, "Unable to connect device")
        msg.data = bundle
        mHandler!!.sendMessage(msg)
        mState = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        start()
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        // Send a failure message back to the Activity
        val msg = mHandler!!.obtainMessage(Constants.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(Constants.TOAST, "Device connection was lost")
        msg.data = bundle
        mHandler!!.sendMessage(msg)
        mState = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        start()
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private inner class AcceptThread @SuppressLint("MissingPermission") constructor(secure: Boolean) :
        Thread() {
        // The local server socket
        private val mmServerSocket: BluetoothServerSocket?
        private val mSocketType: String
        override fun run() {
            Log.d(
                TAG, "Socket Type: " + mSocketType +
                        "BEGIN mAcceptThread" + this
            )
            name = "AcceptThread$mSocketType"
            var socket: BluetoothSocket?

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                socket = try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmServerSocket!!.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e)
                    break
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@BluetoothService) {
                        when (mState) {
                            STATE_LISTEN, STATE_CONNECTING ->                                 // Situation normal. Start the connected thread.
                                connected(
                                    socket, socket.remoteDevice,
                                    mSocketType
                                )
                            STATE_NONE, STATE_CONNECTED ->                                 // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: $mSocketType")
        }

        fun cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this)
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e)
            }
        }

        init {
            var tmp: BluetoothServerSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            // Create a new listening server socket
            try {
                tmp = if (secure) {
                    mAdapter!!.listenUsingRfcommWithServiceRecord(
                        NAME_SECURE,
                        MY_UUID_SECURE
                    )
                } else {
                    mAdapter!!.listenUsingInsecureRfcommWithServiceRecord(
                        NAME_INSECURE, MY_UUID_INSECURE
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e)
            }
            mmServerSocket = tmp
            mState = STATE_LISTEN
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread @SuppressLint("MissingPermission") constructor(
        private val mmDevice: BluetoothDevice,
        secure: Boolean,
    ) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mSocketType: String

        @SuppressLint("MissingPermission")
        override fun run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:$mSocketType")
            name = "ConnectThread$mSocketType"

            // Always cancel discovery because it will slow down a connection
            mAdapter!!.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket!!.connect()
            } catch (e: IOException) {
                // Close the socket
                Log.e("eeeeeee", e.toString())
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) {
                    Log.e(
                        TAG, "unable to close() " + mSocketType +
                                " socket during connection failure", e2
                    )
                }
                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothService) { mConnectThread = null }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect $mSocketType socket failed", e)
            }
        }

        init {
            var tmp: BluetoothSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = if (secure) {
                    mmDevice.createRfcommSocketToServiceRecord(
                        MY_UUID_SECURE
                    )
                } else {
                    mmDevice.createInsecureRfcommSocketToServiceRecord(
                        MY_UUID_INSECURE
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e)
            }
            mmSocket = tmp
            mState = STATE_CONNECTING
            connectedDevice = mmDevice
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(socket: BluetoothSocket?, socketType: String) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            Log.i(TAG, "BEGIN mConnectedThread")

            var arr_byte = ArrayList<Int>()
            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    val data = mmInStream!!.read()
                     if (data == 0x0D) {//13
                        val buffer = ByteArray(1024)
                        for (i in arr_byte.indices) {
                            buffer[i] = arr_byte[i].toByte()
                        }
                        // Send the obtained bytes to the UI Activity
                        mHandler?.obtainMessage(Constants.MESSAGE_READ, buffer.size, -1, buffer)
                            ?.sendToTarget()
                        arr_byte = ArrayList<Int>()
                    } else {
                        arr_byte.add(data)
                    }


                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        fun write(buffer: ByteArray?) {
            try {
                mmOutStream!!.write(buffer)

                // Share the sent message back to the UI Activity
                mHandler!!.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }

        init {
            Log.d(TAG, "create ConnectedThread: $socketType")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket!!.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
            mState = STATE_CONNECTED

            _inputstream = mmInStream
            _outputStream = mmOutStream
            _btSocket = mmSocket
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverDevices() {
        if (mAdapter != null) {
            if (mAdapter!!.isDiscovering) {
                mAdapter!!.cancelDiscovery()
                Toast.makeText(mContext, "Discovery stopped", Toast.LENGTH_SHORT).show()
            } else {
                if (mAdapter!!.isEnabled) {
                    // Toast.makeText(mContext, "Discovery started", Toast.LENGTH_SHORT).show()
                    discoverArrayList.clear()
                    bluetoothServiceListener!!.onDiscoverStarted()
                    val intent = IntentFilter()
                    intent.addAction(BluetoothDevice.ACTION_FOUND)
                    mContext!!.registerReceiver(discoverDeviceReceiver, intent)
                    mAdapter!!.startDiscovery()
                } else {
                    Toast.makeText(mContext, "Bluetooth not on", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscoveries(){
        if (mAdapter != null) {
            if (mAdapter!!.isDiscovering) {
                mAdapter!!.cancelDiscovery()
                //Toast.makeText(mContext, "Discovery stopped", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var discoverDeviceReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Get the BluetoothDevice object from the Intent
                val device =
                    intent.getParcelableExtra<Parcelable>(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                // If it's already paired, skip it, because it's been listed already
                if (device != null) {
                    discoverArrayList.add(device)
                    bluetoothServiceListener!!.onDiscoverFinished(device)
                }
            }
        }
    }

    interface BluetoothServiceListener {
        fun onDiscoverStarted()
        fun onDiscoverFinished(device: BluetoothDevice?)
    }

    companion object {
        // Debugging
        private const val TAG = "BluetoothChatService"

        // Name for the SDP record when creating server socket
        private const val NAME_SECURE = "BluetoothChatSecure"
        private const val NAME_INSECURE = "BluetoothChatInsecure"

        // Unique UUID for this application
        private val MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

        // Constants that indicate the current connection state
        const val STATE_NONE = 0 // we're doing nothing
        const val STATE_LISTEN = 1 // now listening for incoming connections
        const val STATE_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED = 3 // now connected to a remote device

        @SuppressLint("StaticFieldLeak")
        private var instance: BluetoothService? = null
        fun getInstance(): BluetoothService? {
            if (instance == null) {
                synchronized(BluetoothService::class.java) {

                }
            }
            return instance
        }
    }


    override fun init() {
        if (isCommunicationLayerEnabled) {
            onPairedDeviceList()
        }
    }

    override val communicationLayerInfo: BluetoothDevice?
        get() = connectedDevice


    override val isCommunicationLayerEnabled: Boolean get() = mAdapter != null


    override fun openConnection(connectionData: Array<Any?>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun closeConnection() {
        stop()
    }

    override val isConnectionOpen: Boolean
        get() = this.mState == STATE_CONNECTED


    override fun read(): Int {
        return _inputstream!!.read()
    }

    override fun read(bytes: ByteArray?, offset: Int, len: Int): Int {
        return _inputstream!!.read(bytes, offset, len)
    }

    override fun write(data: ByteArray?): Boolean {
        if (mState != STATE_CONNECTED) return false
        _outputStream!!.write(data)
        mHandler!!.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, data)
            .sendToTarget()
        return true
    }

    override fun write(data: IntArray?): Boolean {
        val ret: Int
        return try {
            val bytes = ByteArray(data!!.size)
            for (i in data.indices) {
                bytes[i] = data[i].toByte()
            }
            try {
                _outputStream!!.write(bytes)
                ret = 1
                mHandler!!.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, bytes)
                    .sendToTarget()
                ret > 0
            } catch (ex: java.lang.Exception) {
                android.util.Log.e("Exception", "ex : " + ex.message)
                closeConnection()
                false
            }
        } catch (e: java.lang.Exception) {
            closeConnection()
            false
        }
    }

    override fun write(data: IntArray?, off: Int, len: Int): Boolean {

        var ret: Int

        return try {
            var bytes: ByteArray? = null
            bytes = ByteArray(data!!.size)
            for (i in data.indices) {
                bytes[i] = data[i].toByte()
            }
            try {
                val bytesToSend = data.size
                var offset = 0
                var length = len
                while (offset < bytesToSend) {
                    _outputStream!!.write(bytes, offset, length)
                    ret = 1

                    //	Thread.sleep(50);
                    offset += len
                    if (offset + len > bytesToSend) {
                        length = bytes.size - offset
                    }
                    if (ret < 0) {
                        return false
                    }
                }
                true
            } catch (e: Exception) {
                //connectedDeviceName = ""
                closeConnection()
                false
            }
        } catch (e: Exception) {
            //connectedDeviceName = ""
            closeConnection()
            false
        }
    }

    override fun available(): Int {
        return try {
            _inputstream!!.available()
        } catch (ex: IOException) {
            android.util.Log.e("Exception", "ex : " + ex.message)
            -1
        }
    }


    @SuppressLint("MissingPermission")
    override fun onPairedDeviceList(): ArrayList<BluetoothDevice> {
        val fireflyBluetoothDeviceList = ArrayList<BluetoothDevice>()
        if (mAdapter != null) {
            if (mAdapter!!.isEnabled) {
                val bluetoothDeviceList = ArrayList<BluetoothDevice>()

                bluetoothDeviceList.addAll(mAdapter!!.bondedDevices)

                for (i in bluetoothDeviceList.indices) {
                    if (bluetoothDeviceList[i].name.lowercase().contains("firefly")) {
                        fireflyBluetoothDeviceList.add(bluetoothDeviceList[i])
                    }
                }
                return fireflyBluetoothDeviceList
            } else {
                Toast.makeText(mContext, "Bluetooth not on", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        return fireflyBluetoothDeviceList
    }
}