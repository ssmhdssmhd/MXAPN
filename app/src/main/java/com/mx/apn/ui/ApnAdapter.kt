package com.mx.apn.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mx.apn.R
import com.mx.apn.data.Apn

/**
 * APN 列表适配器
 */
class ApnAdapter(
    private val onItemClick: (Apn) -> Unit,
    private val onImportClick: (Apn) -> Unit,
    private val onCopyClick: (Apn) -> Unit
) : ListAdapter<Apn, ApnAdapter.ApnViewHolder>(ApnDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApnViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_apn, parent, false)
        return ApnViewHolder(view)
    }

    override fun onBindViewHolder(holder: ApnViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ApnViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvApnName)
        private val tvApn: TextView = itemView.findViewById(R.id.tvApnValue)
        private val tvType: TextView = itemView.findViewById(R.id.tvApnType)
        private val tvNote: TextView = itemView.findViewById(R.id.tvApnNote)
        private val ivRecommend: ImageView = itemView.findViewById(R.id.ivRecommend)
        private val btnImport: TextView = itemView.findViewById(R.id.btnImport)
        private val btnCopy: TextView = itemView.findViewById(R.id.btnCopy)

        fun bind(apn: Apn) {
            tvName.text = apn.name
            tvApn.text = apn.apn
            tvType.text = "类型: ${apn.type}"
            tvNote.text = apn.note

            // 推荐标记
            if (apn.recommended) {
                ivRecommend.visibility = View.VISIBLE
                itemView.setBackgroundColor(Color.parseColor("#F0F8FF"))
            } else {
                ivRecommend.visibility = View.GONE
                itemView.setBackgroundColor(Color.WHITE)
            }

            // 点击事件
            itemView.setOnClickListener { onItemClick(apn) }
            btnImport.setOnClickListener { onImportClick(apn) }
            btnCopy.setOnClickListener { onCopyClick(apn) }
        }
    }

    class ApnDiffCallback : DiffUtil.ItemCallback<Apn>() {
        override fun areItemsTheSame(oldItem: Apn, newItem: Apn): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Apn, newItem: Apn): Boolean {
            return oldItem == newItem
        }
    }
}
