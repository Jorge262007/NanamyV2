package com.nanamy.launcher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.databinding.ItemCardBinding

/**
 * Adaptador para las tarjetas modulares (esqueleto).
 */
class CardsAdapter(private val cards: List<CardModel>) : RecyclerView.Adapter<CardsAdapter.CardViewHolder>() {

    class CardViewHolder(val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.binding.tvCardTitle.text = cards[position].title
    }

    override fun getItemCount(): Int = cards.size
}
