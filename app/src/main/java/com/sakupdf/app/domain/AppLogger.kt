package com.sakupdf.app.domain

import android.util.Log

object AppLogger {
    private var isTestEnvironment: Boolean = false

    fun d(tag: String, msg: String) {
        if (isTestEnvironment) return
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            isTestEnvironment = true
        }
    }

    fun e(tag: String, msg: String) {
        if (isTestEnvironment) return
        try {
            Log.e(tag, msg)
        } catch (_: Throwable) {
            isTestEnvironment = true
        }
    }
}
