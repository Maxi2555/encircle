// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle.assist

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Required by the platform for every assistant. Speech recognition is not offered. */
class StubRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }

    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}
