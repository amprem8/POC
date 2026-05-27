package com.example.poc

import android.util.Log

object PassKeyTrace {
    const val TAG = "PK_TRACE"

    fun d(source: String, message: String) {
        Log.d(TAG, "[$source] $message")
    }

    fun i(source: String, message: String) {
        Log.i(TAG, "[$source] $message")
    }

    fun w(source: String, message: String) {
        Log.w(TAG, "[$source] $message")
    }

    fun e(source: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$source] $message", throwable)
        } else {
            Log.e(TAG, "[$source] $message")
        }
    }
}

