package com.nanamy.launcher

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Adaptador para el ViewPager2 con las 3 páginas principales.
 */
class LauncherPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WidgetsFragment()
            1 -> EyesFragment()
            2 -> AppsFragment()
            else -> throw IllegalArgumentException("Posición inválida")
        }
    }
}
