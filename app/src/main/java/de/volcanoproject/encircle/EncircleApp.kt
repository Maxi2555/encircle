// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The Encircle contributors

package de.volcanoproject.encircle

import android.app.Application
import de.volcanoproject.encircle.analysis.ScreenAnalyzer

class EncircleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load the OCR model and the barcode library right away. The process stays alive while the
        // app is the default assistant, so the first long-press already hits warm engines.
        ScreenAnalyzer.warmUp()
    }
}
