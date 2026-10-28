package com.javarhol.murren

import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Android 15 draws apps edge to edge; keep content clear of the status and nav bars. */
fun applySystemBarPadding(view: View) {
    val base = view.paddingTop
    view.setOnApplyWindowInsetsListener { v, insets ->
        val (top, bottom) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            bars.top to bars.bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetTop to insets.systemWindowInsetBottom
        }
        v.setPadding(v.paddingLeft, base + top, v.paddingRight, bottom)
        insets
    }
}
