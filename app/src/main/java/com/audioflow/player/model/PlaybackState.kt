package com.audioflow.player.model

/**
 * Represents the current playback state
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1
) {
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    
    val hasNext: Boolean
        get() = currentQueueIndex < queue.size - 1 || repeatMode == RepeatMode.ALL
    
    val hasPrevious: Boolean
        get() = currentQueueIndex > 0 || repeatMode == RepeatMode.ALL
}

/**
 * Repeat modes for playback
 */
enum class RepeatMode {
    OFF,    // No repeat
    ALL,    // Repeat the entire queue
    ONE     // Repeat the current track
}

/**
 * Player events for UI updates
 */
sealed class PlayerEvent {
    data object PlaybackStarted : PlayerEvent()
    data object PlaybackPaused : PlayerEvent()
    data object PlaybackStopped : PlayerEvent()
    data class TrackChanged(val track: Track) : PlayerEvent()
    data class PositionChanged(val position: Long) : PlayerEvent()
    data class Error(val message: String) : PlayerEvent()
    data class QueueUpdated(val queue: List<Track>) : PlayerEvent()
}

/**
 * Commands to control the player
 */
sealed class PlayerCommand {
    data class Play(val track: Track? = null) : PlayerCommand()
    data object Pause : PlayerCommand()
    data object Stop : PlayerCommand()
    data object Next : PlayerCommand()
    data object Previous : PlayerCommand()
    data object TogglePlayPause : PlayerCommand()
    data object ToggleShuffle : PlayerCommand()
    data object CycleRepeatMode : PlayerCommand()
    data class SeekTo(val position: Long) : PlayerCommand()
    data class SetQueue(val tracks: List<Track>, val startIndex: Int = 0) : PlayerCommand()
    data class AddToQueue(val track: Track) : PlayerCommand()
}
