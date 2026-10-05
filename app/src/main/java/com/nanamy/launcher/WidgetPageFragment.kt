package com.nanamy.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nanamy.launcher.databinding.FragmentWidgetPageBinding

class WidgetPageFragment : Fragment() {

    private var _binding: FragmentWidgetPageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWidgetPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        arguments?.let {
            binding.tvWidgetTitle.text = it.getString(ARG_TITLE)
            binding.widgetPageRoot.setBackgroundColor(it.getInt(ARG_COLOR))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_COLOR = "arg_color"

        fun newInstance(title: String, color: Int): WidgetPageFragment {
            return WidgetPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putInt(ARG_COLOR, color)
                }
            }
        }
    }
}
