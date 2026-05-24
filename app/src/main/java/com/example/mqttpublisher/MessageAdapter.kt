package com.example.mqttpublisher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    private val items = mutableListOf<MessageItem>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvPayload: TextView = view.findViewById(R.id.tvPayload)
        val tvStatus: TextView = view.findViewById(R.id.tvMsgStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvIndex.text = "#${item.count}"
        holder.tvTime.text = item.time
        holder.tvPayload.text = item.payload
        if (item.success) {
            holder.tvStatus.text = "✓ 成功"
            holder.tvStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
            )
        } else {
            holder.tvStatus.text = "✗ 失败"
            holder.tvStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
            )
        }
    }

    override fun getItemCount() = items.size

    fun addMessage(item: MessageItem) {
        items.add(0, item)
        if (items.size > 20) items.removeAt(items.size - 1)
        notifyDataSetChanged()
    }

    fun setMessages(list: List<MessageItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun clearMessages() {
        items.clear()
        notifyDataSetChanged()
    }
}
