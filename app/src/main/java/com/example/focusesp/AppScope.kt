package com.example.focusesp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }
}
