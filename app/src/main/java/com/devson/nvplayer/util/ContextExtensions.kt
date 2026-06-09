package com.devson.nvplayer.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Helper extension function to traverse ContextWrapper chain and find the nearest Activity.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
