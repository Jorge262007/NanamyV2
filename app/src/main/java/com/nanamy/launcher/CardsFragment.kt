package com.nanamy.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nanamy.launcher.databinding.FragmentCardsBinding

/**
 * Fragmento de la izquierda: Muestra tarjetas modulares.
 */
class CardsFragment : Fragment() {

    private var _binding: FragmentCardsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Datos de ejemplo para el esqueleto
        val sampleCards = listOf(
            CardModel("Reproductor"),
            CardModel("Calendario")
        )

        binding.rvCards.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = CardsAdapter(sampleCards)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
