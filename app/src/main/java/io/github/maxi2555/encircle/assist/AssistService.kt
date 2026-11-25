package io.github.maxi2555.encircle.assist

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import java.lang.ref.WeakReference

/**
 * Entry point the system binds while the app is the default digital assistant.
 * Long-pressing home or the navigation handle makes the system open a session from
 * [AssistSessionService], exactly the hook that Circle to Search uses.
 */
class AssistService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        current = WeakReference(this)
    }

    override fun onShutdown() {
        current = null
        super.onShutdown()
    }

    companion object {
        @Volatile
        private var current: WeakReference<AssistService>? = null

        fun isActive(context: Context): Boolean =
            isActiveService(context, ComponentName(context, AssistService::class.java))

        /** Opens the overlay programmatically, used by the "try it" button in the setup screen. */
        fun launchOverlay(): Boolean {
            val service = current?.get() ?: return false
            service.showSession(
                Bundle(),
                VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT,
            )
            return true
        }
    }
}
