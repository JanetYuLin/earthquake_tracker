package io.github.janetyulin.grounded.testing

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry

/** Resolves a string resource from the app under test. */
fun string(@StringRes resId: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, *args)

/** Resolves a plural resource from the app under test. */
fun plural(@PluralsRes resId: Int, count: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(resId, count, *args)
