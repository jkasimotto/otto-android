package com.otto.launcher.domain.policy

sealed interface AppGate {
    data object Allowed : AppGate
    data object Blocked : AppGate
    data object AdminHidden : AppGate
    data class WorkWindowClosed(val label: String) : AppGate
    data class Distraction(
        val challengeRequired: Boolean,
        val reasonRequired: Boolean,
        val defaultTimeboxMinutes: Int
    ) : AppGate
}

