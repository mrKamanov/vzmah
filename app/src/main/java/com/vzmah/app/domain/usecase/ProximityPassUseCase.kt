package com.vzmah.app.domain.usecase

import com.vzmah.app.domain.model.PresentationCommand

data class ProximityPassConfig(
    val rearmMs: Long = 400L,
)

class ProximityPassUseCase(
    private val config: ProximityPassConfig = ProximityPassConfig(),
) {

    private var near: Boolean = false

    private var lastPassAtMs: Long? = null

    fun onProximity(nearNow: Boolean, nowMs: Long): PresentationCommand? {
        if (!nearNow) {
            near = false
            return null
        }
        if (near) return null

        near = true
        val lastPass = lastPassAtMs
        if (lastPass != null && nowMs - lastPass < config.rearmMs) return null

        lastPassAtMs = nowMs
        return PresentationCommand.NextSlide
    }

    fun reset() {
        near = true
        lastPassAtMs = null
    }

    fun sync(nearNow: Boolean) {
        near = nearNow
    }
}

