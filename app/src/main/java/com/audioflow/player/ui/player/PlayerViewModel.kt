package com.audioflow.player.ui.player

import androidx.lifecycle.ViewModel
import com.audioflow.player.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController
) : ViewModel() {
    
    val playbackState = playerController.playbackState
    
    fun togglePlayPause() {
        playerController.togglePlayPause()
    }
    
    fun next() {
        playerController.next()
    }
    
    fun previous() {
        playerController.previous()
    }
    
    fun seekTo(position: Long) {
        playerController.seekTo(position)
    }
    
    fun seekToProgress(progress: Float) {
        playerController.seekToProgress(progress)
    }
    
    fun toggleShuffle() {
        playerController.toggleShuffle()
    }
    
    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }
}
