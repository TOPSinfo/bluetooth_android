package com.essence.commincation.api.maintanance.layer

import android.bluetooth.BluetoothDevice

interface CCommunicationLayer {
    fun init()
    val communicationLayerInfo: Any?
    val isCommunicationLayerEnabled: Boolean
    fun openConnection(connectionData: Array<Any?>?): Boolean
    fun closeConnection()
    val isConnectionOpen: Boolean
    fun read(): Int
    fun read(bytes: ByteArray?, offset: Int, len: Int): Int
    fun write(data: ByteArray?): Boolean
    fun write(data: IntArray?): Boolean
    fun write(data: IntArray?, off: Int, len: Int): Boolean
    fun available(): Int
    fun onPairedDeviceList(): ArrayList<BluetoothDevice>
}