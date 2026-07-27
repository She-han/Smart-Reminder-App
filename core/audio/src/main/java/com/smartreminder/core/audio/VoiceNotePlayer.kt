package com.smartreminder.core.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

/**
 * Where the voice note comes out.
 *
 * [SPEAKER] is used on the confirm screen (review the note like any recording). [EARPIECE]
 * is used on the in-call screen: routing through the voice-communication stream sends audio
 * to the earpiece, which — with the proximity wake lock in the call module — is what makes
 * playback feel like a phone call rather than a media clip.
 */
enum class PlaybackRoute { SPEAKER, EARPIECE }

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Playing(val positionMs: Long, val durationMs: Long) : PlaybackState
    data object Ended : PlaybackState
    data class Failed(val reason: String) : PlaybackState
}

class VoiceNotePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: ExoPlayer? = null

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun play(file: File, route: PlaybackRoute) {
        release()
        val attrs = AudioAttributes.Builder()
            .setUsage(if (route == PlaybackRoute.EARPIECE) C.USAGE_VOICE_COMMUNICATION else C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        player = ExoPlayer.Builder(context).build().apply {
            // handleAudioFocus=false: the call screen manages focus itself around the ringtone.
            setAudioAttributes(attrs, /* handleAudioFocus = */ route == PlaybackRoute.SPEAKER)
            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) _state.value = PlaybackState.Ended
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) pushPlaying()
                }
            })
            prepare()
            playWhenReady = true
        }
        pushPlaying()
    }

    fun stop() {
        player?.pause()
        _state.value = PlaybackState.Idle
    }

    fun release() {
        player?.release()
        player = null
    }

    /** Current position for the in-call elapsed timer. Safe to poll from the UI. */
    fun positionMs(): Long = player?.currentPosition ?: 0L

    private fun pushPlaying() {
        val p = player ?: return
        val duration = if (p.duration == C.TIME_UNSET) 0L else p.duration
        _state.value = PlaybackState.Playing(p.currentPosition, duration)
    }
}
