package com.cyplixlauncher.mc.ui.subassembly.chat

import android.annotation.SuppressLint
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.databinding.ItemChatMessageBinding

class ChatMessageAdapter(
    private val mData: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<ChatMessageAdapter.InnerHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        return InnerHolder(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        holder.setData(mData[position])
    }

    override fun getItemCount(): Int = mData.size

    fun addMessage(message: ChatMessage) {
        mData.add(message)
        notifyItemInserted(mData.size - 1)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        mData.clear()
        notifyDataSetChanged()
    }

    class InnerHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun setData(message: ChatMessage) {
            if (message.isUser) {
                binding.messageBubble.text = message.text
            } else {
                // Incoming messages may carry Markdown; render it so bold, lists and code read properly.
                val rendered = ChatMarkdown.render(message.text, binding.root.context)
                binding.messageBubble.text = rendered
                // Links need LinkMovementMethod, which drops text selection - only swap it in
                // for bubbles that actually contain a link.
                if (rendered is Spanned &&
                    rendered.getSpans(0, rendered.length, URLSpan::class.java).isNotEmpty()
                ) {
                    binding.messageBubble.movementMethod = LinkMovementMethod.getInstance()
                }
            }
            binding.messageBubble.visibility =
                if (message.text.isBlank()) View.GONE else View.VISIBLE

            val context = binding.root.context
            val params = binding.root.layoutParams as? LinearLayout.LayoutParams
            if (message.isUser) {
                binding.root.gravity = Gravity.END
                binding.messageBubble.setBackgroundResource(R.drawable.background_chat_bubble_user)
                binding.messageBubble.setTextColor(context.getColor(R.color.background_app))
            } else {
                binding.root.gravity = Gravity.START
                binding.messageBubble.setBackgroundResource(R.drawable.background_chat_bubble_other)
                binding.messageBubble.setTextColor(context.getColor(R.color.turtle_text_primary))
            }
            params?.let { binding.root.layoutParams = it }
        }
    }
}
