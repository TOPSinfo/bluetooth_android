package com.example.ble_app.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ble_app.ResponseActivity
import com.example.ble_app.databinding.ItemPairedDeviceBinding

/**
 *  Adapter class for recyclerview to show
 *  Paired and new device list.
 * */
class PairedDeviceListAdapter(
    private val context: Context,
    private val pairedDeviceList: ArrayList<BluetoothDevice>,
    private var connectedDeviceName: String?,
    private val connectListener: PairedConnectDeviceListener
) : RecyclerView.Adapter<PairedDeviceListAdapter.PairedDeviceViewHolder>() {

    private var connectedDeviceMacAddress: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PairedDeviceViewHolder {
        val binding = ItemPairedDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PairedDeviceViewHolder(binding)
    }

    fun addDevice(device: BluetoothDevice) {
        if (!pairedDeviceList.contains(device)) {
            pairedDeviceList.add(device)
            notifyDataSetChanged()
        }

    }

    fun connectedDeviceMacAddress(address: String?){
        connectedDeviceMacAddress = address
        notifyDataSetChanged()
    }

    fun connectAddress(address: String?) {
        connectedDeviceName = address
        notifyDataSetChanged()
    }


    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: PairedDeviceViewHolder, position: Int) {

        if (!TextUtils.isEmpty(pairedDeviceList[position].name)) {
            holder.viewBinding.txtDeviceName.text = pairedDeviceList[position].name
        } else {
            holder.viewBinding.txtDeviceName.text = pairedDeviceList[position].address
        }



        if (!TextUtils.isEmpty(connectedDeviceMacAddress)) {
            if (pairedDeviceList[position].address == connectedDeviceName) {
                holder.viewBinding.txtConnected.visibility = View.GONE
                holder.viewBinding.txtDisconnect.visibility = View.VISIBLE
            } else {
                holder.viewBinding.txtConnected.visibility = View.VISIBLE
                holder.viewBinding.txtDisconnect.visibility = View.GONE
            }
            holder.viewBinding.loader.visibility = View.GONE
        } else {
            holder.viewBinding.loader.visibility = View.GONE
            holder.viewBinding.txtConnected.visibility = View.VISIBLE
            holder.viewBinding.txtDisconnect.visibility = View.GONE
        }

//        holder.viewBinding.txtDeviceName.setOnClickListener {
//            connectedDeviceMacAddress(pairedDeviceList[position].address)
////            connectListener.onItemClick(pairedDeviceList[position])
//        }

        holder.viewBinding.txtConnected.setOnClickListener {
            holder.viewBinding.loader.visibility = View.VISIBLE
            connectedDeviceMacAddress(pairedDeviceList[position].address)
//            connectListener.onItemClick(pairedDeviceList[position])
//            notifyDataSetChanged()
            if (!TextUtils.isEmpty(connectedDeviceMacAddress)) {
                if (pairedDeviceList[position].address == connectedDeviceMacAddress) {
                    context.startActivity(
                        Intent(context,ResponseActivity::class.java)
                            .putExtra("device",connectedDeviceMacAddress)
                    )
                }
            }
        }

        holder.viewBinding.txtDisconnect.setOnClickListener {
            connectedDeviceMacAddress(null)
            connectListener.onItemDisconnect()
            notifyDataSetChanged()
        }

        holder.viewBinding.txtDeviceName.setOnClickListener {
            if (!TextUtils.isEmpty(connectedDeviceMacAddress)) {
                if (pairedDeviceList[position].address == connectedDeviceMacAddress) {
                    context.startActivity(
                        Intent(context,ResponseActivity::class.java)
                            .putExtra("device",connectedDeviceMacAddress)
                    )
                }
            }
        }

    }

    class PairedDeviceViewHolder(var viewBinding: ItemPairedDeviceBinding) :
        RecyclerView.ViewHolder(viewBinding.root)

    override fun getItemCount(): Int {
        return pairedDeviceList.size
    }

    interface PairedConnectDeviceListener {
        fun onItemClick(device: BluetoothDevice)
        fun onItemDisconnect()
    }
}