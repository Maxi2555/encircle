// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package io.github.maxi2555.encircle.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = OverlaySession(this)
}
