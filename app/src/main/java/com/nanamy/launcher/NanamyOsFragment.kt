package com.nanamy.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nanamy.launcher.databinding.FragmentNanamyOsBinding
import java.net.Inet4Address
import java.net.NetworkInterface

class NanamyOsFragment : Fragment() {

    private var _binding: FragmentNanamyOsBinding? = null
    private val binding get() = _binding!!

    private var server: NanamyOsServer? = null
    private var isServerRunning = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.hardware.usb.action.USB_STATE") {
                val connected = intent.getBooleanExtra("connected", false)
                val configured = intent.getBooleanExtra("configured", false)
                // configured usually means data connection is ready
                updateUsbStatus(connected && configured)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNanamyOsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartUsb.setOnClickListener { startNanamyOs("usb") }
        binding.btnStartWifi.setOnClickListener { startNanamyOs("wifi") }
        binding.btnStartHotspot.setOnClickListener { startNanamyOs("hotspot") }
        
        binding.btnStopOS.setOnClickListener { stopNanamyOs() }

        binding.btnTetheringSettings.setOnClickListener {
            openTetheringSettings()
        }

        binding.btnHotspotSettings.setOnClickListener {
            openHotspotSettings()
        }

        binding.tvUrl.setOnClickListener {
            if (isServerRunning) {
                val ips = getAvailableIps()
                binding.tvUrl.text = ips.joinToString("\n") { "http://$it:8080" }
            }
        }

        // Initial check
        checkUsbConnectionManually()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("android.hardware.usb.action.USB_STATE")
        requireContext().registerReceiver(usbReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(usbReceiver)
    }

    private fun updateUsbStatus(isDataConnected: Boolean) {
        if (isServerRunning) {
            val ips = getAvailableIps()
            binding.tvUrl.text = ips.joinToString("\n") { "http://$it:8080" }
            return
        }

        if (isDataConnected) {
            binding.tvStatus.text = "USB Connection detected"
            binding.tvStatus.setTextColor(android.graphics.Color.GREEN)
        } else {
            binding.tvStatus.text = "Select connection method"
            binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#00E5FF"))
        }
    }

    private fun checkUsbConnectionManually() {
        val intent = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val isUsb = plugged == BatteryManager.BATTERY_PLUGGED_USB
        updateUsbStatus(isUsb) 
    }

    private fun startNanamyOs(method: String) {
        try {
            server = NanamyOsServer(requireContext())
            server?.start()
            isServerRunning = true
            
            binding.llStartButtons.visibility = View.GONE
            binding.btnStopOS.visibility = View.VISIBLE
            binding.llUrlContainer.visibility = View.VISIBLE
            
            val ips = getAvailableIps()
            binding.tvUrl.text = ips.joinToString("\n") { "http://$it:8080" }

            when (method) {
                "usb" -> {
                    binding.tvStatus.text = "USB Mode: Enable Tethering if needed"
                    binding.btnTetheringSettings.visibility = View.VISIBLE
                    binding.btnHotspotSettings.visibility = View.GONE
                }
                "hotspot" -> {
                    binding.tvStatus.text = "Hotspot Mode: Configure your Hotspot"
                    binding.btnHotspotSettings.visibility = View.VISIBLE
                    binding.btnTetheringSettings.visibility = View.GONE
                    openHotspotSettings()
                }
                "wifi" -> {
                    binding.tvStatus.text = "WiFi Mode: Connected to local network"
                    binding.btnTetheringSettings.visibility = View.GONE
                    binding.btnHotspotSettings.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            binding.tvStatus.text = "Error starting server: ${e.message}"
        }
    }

    private fun stopNanamyOs() {
        server?.stop()
        server = null
        isServerRunning = false
        
        binding.llStartButtons.visibility = View.VISIBLE
        binding.btnStopOS.visibility = View.GONE
        binding.llUrlContainer.visibility = View.GONE
        binding.btnTetheringSettings.visibility = View.GONE
        binding.btnHotspotSettings.visibility = View.GONE
        
        checkUsbConnectionManually() 
    }

    private fun openTetheringSettings() {
        val intent = Intent().apply {
            action = "android.settings.TETHER_SETTINGS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    private fun openHotspotSettings() {
        val intent = Intent().apply {
            action = "android.settings.WIFI_AP_SETTINGS" // Hotspot settings
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to tethering settings if direct Hotspot settings fail
            openTetheringSettings()
        }
    }

    private fun getAvailableIps(): List<String> {
        val ips = mutableSetOf<String>()
        
        // Method 1: ConnectivityManager (Modern Android way)
        try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks: Array<Network> = cm.allNetworks
            for (network in networks) {
                val lp: LinkProperties? = cm.getLinkProperties(network)
                lp?.linkAddresses?.forEach { linkAddr ->
                    val addr = linkAddr.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && host != "127.0.0.1") {
                            ips.add(host)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("NanamyOS", "ConnectivityManager IP detection failed", e)
        }

        // Method 2: NetworkInterface (Fallback for older versions or virtual interfaces)
        if (ips.isEmpty()) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback) continue
                    
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: ""
                            if (host.isNotEmpty() && host != "127.0.0.1") {
                                ips.add(host)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NanamyOS", "NetworkInterface IP detection failed", e)
            }
        }

        val sortedIps = ips.toList().sortedWith(compareByDescending<String> {
            // Priority 1: USB Tethering (usually 192.168.42.x)
            it.startsWith("192.168.42.")
        }.thenByDescending {
            // Priority 2: WiFi Hotspot (usually 192.168.43.x)
            it.startsWith("192.168.43.")
        }.thenByDescending {
            // Priority 3: Common Local Network (192.168.x.x)
            it.startsWith("192.168.")
        }.thenByDescending {
            // Priority 4: Other Class C / Private
            it.startsWith("10.") || it.startsWith("172.")
        })
        
        return if (sortedIps.isEmpty()) listOf("127.0.0.1") else sortedIps
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isServerRunning) stopNanamyOs()
        _binding = null
    }
}
