package com.nanamy.launcher.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.provider.Settings
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.nanamy.launcher.databinding.FragmentMusicWidgetBinding
import com.nanamy.launcher.services.NanamyNotificationListener

class MusicWidgetFragment : Fragment() {

    private var _binding: FragmentMusicWidgetBinding? = null
    private val binding get() = _binding!!

    private var mediaController: MediaControllerCompat? = null
    private var sessionManager: MediaSessionManager? = null
    private val componentName by lazy { ComponentName(requireContext(), NanamyNotificationListener::class.java) }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { 
        initializeController()
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updatePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            updateMetadata(metadata)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMusicWidgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = requireContext().getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        if (isNotificationServiceEnabled()) {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            initializeController()
        } else {
            showState(State.NO_PERMISSION)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            // Ignore if not registered or other issues
        }
        releaseController()
    }

    private fun setupClickListeners() {
        binding.btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnPlayPause.setOnClickListener {
            val state = mediaController?.playbackState?.state
            if (state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController?.transportControls?.pause()
            } else {
                mediaController?.transportControls?.play()
            }
        }

        binding.btnNext.setOnClickListener { mediaController?.transportControls?.skipToNext() }
        binding.btnPrev.setOnClickListener { mediaController?.transportControls?.skipToPrevious() }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)
    }

    private fun initializeController() {
        val sessions = try {
            sessionManager?.getActiveSessions(componentName)
        } catch (e: SecurityException) {
            showState(State.NO_PERMISSION)
            return
        }
        
        // If we already have a controller for the top session, do nothing
        if (!sessions.isNullOrEmpty() && mediaController?.sessionToken == MediaSessionCompat.Token.fromToken(sessions[0].sessionToken)) {
            return
        }

        releaseController()

        if (!sessions.isNullOrEmpty()) {
            val token = MediaSessionCompat.Token.fromToken(sessions[0].sessionToken)
            val controller = MediaControllerCompat(requireContext(), token)
            mediaController = controller
            controller.registerCallback(controllerCallback)
            
            updateMetadata(controller.metadata)
            updatePlaybackState(controller.playbackState)
            showState(State.PLAYER)
        } else {
            showState(State.EMPTY)
        }
    }

    private fun updateMetadata(metadata: MediaMetadataCompat?) {
        if (metadata == null) {
            binding.tvTitle.text = "Nada reproduciéndose"
            binding.tvArtist.text = ""
            binding.ivAlbumArt.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }
        binding.tvTitle.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Desconocido"
        binding.tvArtist.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Artista desconocido"
        
        val art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) 
            ?: metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
        
        if (art != null) {
            binding.ivAlbumArt.setImageBitmap(art)
        } else {
            binding.ivAlbumArt.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun updatePlaybackState(state: PlaybackStateCompat?) {
        if (state == null) return
        val isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun releaseController() {
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = null
    }

    private fun showState(state: State) {
        binding.llNoPermission.visibility = if (state == State.NO_PERMISSION) View.VISIBLE else View.GONE
        binding.llEmpty.visibility = if (state == State.EMPTY) View.VISIBLE else View.GONE
        binding.llPlayer.visibility = if (state == State.PLAYER) View.VISIBLE else View.GONE
    }

    private enum class State { NO_PERMISSION, EMPTY, PLAYER }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
