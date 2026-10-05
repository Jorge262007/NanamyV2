package com.nanamy.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.nanamy.launcher.databinding.FragmentAppsBinding

/**
 * Apps tab: Curated quick access list.
 */
class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appsManager: CuratedAppsManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appsManager = CuratedAppsManager(requireContext())
        loadCuratedApps()
    }

    private fun loadCuratedApps() {
        val pm = requireContext().packageManager
        val list = mutableListOf<AppModel>()

        // 1. Android Settings
        list.add(AppModel(
            label = "Settings",
            packageName = "com.android.settings",
            icon = pm.getDefaultActivityIcon(),
            type = AppItemType.SYSTEM_SETTINGS
        ))

        // 2. Nanamy Settings
        list.add(AppModel(
            label = "Nanamy",
            packageName = "com.nanamy.launcher.settings",
            icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_preferences),
            type = AppItemType.NANAMY_SETTINGS
        ))

        // 3. NanamyOS
        list.add(AppModel(
            label = "NanamyOS",
            packageName = "com.nanamy.launcher.os",
            icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_nanamy_os),
            type = AppItemType.NANAMY_OS
        ))

        // 4. User Curated Apps
        val savedPackages = appsManager.getCuratedPackages()
        savedPackages.forEach { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                list.add(AppModel(
                    label = pm.getApplicationLabel(appInfo).toString(),
                    packageName = pkg,
                    icon = pm.getApplicationIcon(appInfo),
                    type = AppItemType.USER_APP
                ))
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // 4. Add Button
        list.add(AppModel(
            label = "Add",
            packageName = "",
            icon = null,
            type = AppItemType.ADD_BUTTON
        ))

        // Update Android Settings icon
        try {
            val settingsInfo = pm.getApplicationInfo("com.android.settings", 0)
            val index = list.indexOfFirst { it.type == AppItemType.SYSTEM_SETTINGS }
            if (index != -1) {
                list[index] = list[index].copy(icon = pm.getApplicationIcon(settingsInfo))
            }
        } catch (e: Exception) {}

        binding.rvApps.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = AppsAdapter(list, 
                onAppClick = { app -> handleAppClick(app) },
                onAppLongClick = { app -> handleAppLongClick(app) }
            )
        }
    }

    private fun handleAppClick(app: AppModel) {
        when (app.type) {
            AppItemType.SYSTEM_SETTINGS -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
            AppItemType.NANAMY_SETTINGS -> {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
            AppItemType.NANAMY_OS -> {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.overlayContainer, NanamyOsFragment())
                    .addToBackStack(null)
                    .commit()
            }
            AppItemType.USER_APP -> {
                val intent = requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    startActivity(intent)
                }
            }
            AppItemType.ADD_BUTTON -> {
                showAppPicker()
            }
        }
    }

    private fun handleAppLongClick(app: AppModel) {
        if (app.type == AppItemType.USER_APP) {
            AlertDialog.Builder(requireContext())
                .setTitle("Remove shortcut")
                .setMessage("Do you want to remove ${app.label} from the list?")
                .setPositiveButton("Remove") { _, _ ->
                    appsManager.removeApp(app.packageName)
                    loadCuratedApps()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showAppPicker() {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val allApps = resolveInfos.map { info ->
            AppModel(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm),
                type = AppItemType.USER_APP
            )
        }.sortedBy { it.label.lowercase() }

        val pickerBinding = FragmentAppsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Choose application")
            .setView(pickerBinding.root)
            .create()

        pickerBinding.rvApps.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = AppsAdapter(allApps, onAppClick = { app ->
                appsManager.addApp(app.packageName)
                loadCuratedApps()
                dialog.dismiss()
            })
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
