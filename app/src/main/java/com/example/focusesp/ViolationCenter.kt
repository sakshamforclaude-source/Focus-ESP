package com.example.focusesp

object ViolationCenter {

    fun report(reason: String) {
        FocusState.onViolation(reason)
        if (!EspConfig.enabled.value) return
        AppScope.launch {
            val result = EspClient.buzz(force = false)
            EspConfig.logEvent(
                if (result.success) "Auto buzz sent" else "Auto buzz failed: ${result.message}"
            )
        }
    }
}
