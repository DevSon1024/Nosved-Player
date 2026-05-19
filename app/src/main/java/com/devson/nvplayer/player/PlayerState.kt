package com.devson.nvplayer.player

sealed interface PlayerState {
    object Idle : PlayerState
    object Loading : PlayerState
    object Playing : PlayerState
    object Paused : PlayerState
    object Ended : PlayerState
    data class Error(val message: String) : PlayerState
}
