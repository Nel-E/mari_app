package com.mari.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner that replaces the application class with [HiltTestApplication]
 * so Hilt dependency injection works correctly in instrumented tests.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
