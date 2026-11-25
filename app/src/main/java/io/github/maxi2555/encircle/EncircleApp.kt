// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package io.github.maxi2555.encircle

import android.app.Application
import io.github.maxi2555.encircle.analysis.ScreenAnalyzer

class EncircleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load the OCR model and the barcode library right away. The process stays alive while the
        // app is the default assistant, so the first long-press already hits warm engines.
        ScreenAnalyzer.warmUp()
    }
}
