package com.virtualcouch.pucci.dev.ui.effect

import androidx.annotation.DrawableRes

sealed class VideoEffect

data class PlayerErrorEffect(val message: String, val code: Int): VideoEffect()

data class AnimationEffect(@DrawableRes val drawable: Int): VideoEffect()

object ResetAnimationEffect: VideoEffect()

data class LoadingEffect(val isLoading: Boolean, val message: String = ""): VideoEffect()

data class MessageEffect(val message: String): VideoEffect()

object LoginSuccessEffect: VideoEffect()

data class NeedOtpEffect(val phoneNumber: String): VideoEffect()
