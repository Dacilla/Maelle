package com.maelle.core.logging

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedactingLogger @Inject constructor(
    private val authTokenRedactor: AuthTokenRedactor,
) {

    fun d(component: String, message: String) {
        Log.d(component, authTokenRedactor.redact(message))
    }

    fun i(component: String, message: String) {
        Log.i(component, authTokenRedactor.redact(message))
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        Log.w(component, authTokenRedactor.redact(message), throwable)
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        Log.e(component, authTokenRedactor.redact(message), throwable)
    }
}
