package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemAccountDashboardBinding

data class AccountDashboardItem(
    val id: String,
    val title: String,
    val status: String,
    val statusColor: Int,
    val user: String,
    val remaining: String,
    val dates: String,
    val details: String,
    val telegramUrl: String?,
)

class AccountDashboardAdapter(
    private val onAccountSelected: (position: Int, id: String) -> Unit,
    private val onTelegramSelected: (url: String) -> Unit,
) : RecyclerView.Adapter<AccountDashboardAdapter.AccountViewHolder>() {
    private var items: List<AccountDashboardItem> = emptyList()
    private var selectedId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountDashboardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return AccountViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(items[position], items[position].id == selectedId)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitItems(newItems: List<AccountDashboardItem>, newSelectedId: String?) {
        items = newItems
        selectedId = newSelectedId
        notifyDataSetChanged()
    }

    fun selectAccount(id: String?): Int {
        if (selectedId == id) return items.indexOfFirst { it.id == id }

        val oldPosition = items.indexOfFirst { it.id == selectedId }
        selectedId = id
        val newPosition = items.indexOfFirst { it.id == id }
        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        if (newPosition >= 0) notifyItemChanged(newPosition)
        return newPosition
    }

    inner class AccountViewHolder(
        private val binding: ItemAccountDashboardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AccountDashboardItem, selected: Boolean) {
            val context = binding.root.context
            binding.accountCard.strokeWidth = if (selected) 2.dp(context) else 1.dp(context)
            binding.accountCard.strokeColor = ContextCompat.getColor(
                context,
                if (selected) R.color.color_fab_active else R.color.md_theme_outlineVariant,
            )
            binding.accountCard.cardElevation = if (selected) 2.dp(context).toFloat() else 0f
            binding.accountCard.contentDescription = context.getString(
                if (selected) R.string.simple_selected_account_description
                else R.string.simple_account_description,
                item.title,
            )

            binding.tvAccountName.text = item.title
            binding.tvAccountStatus.text = item.status
            binding.accountStatusCard.setCardBackgroundColor(item.statusColor)
            binding.tvAccountUser.text = item.user
            binding.tvAccountRemaining.text = item.remaining
            binding.tvAccountDates.text = item.dates
            binding.tvAccountDetails.text = item.details

            binding.buttonAccountTelegram.isVisible = item.telegramUrl != null
            binding.buttonAccountTelegram.setOnClickListener(
                item.telegramUrl?.let { url -> android.view.View.OnClickListener { onTelegramSelected(url) } },
            )
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAccountSelected(position, items[position].id)
                }
            }
        }
    }

    private fun Int.dp(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
