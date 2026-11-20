package com.craxiom.networksurvey.ui.rootdiag

import android.content.Context

internal object RootDiagPrefs {
    const val NAME = "root_diag_prefs"
    const val BACKGROUND = "background_collection"
    const val OVERLAY = "overlay_wanted"
    const val OVERLAY_SHOW_RF = "overlay_show_rf"
    const val OVERLAY_SHOW_IMS = "overlay_show_ims"
    const val OVERLAY_SHOW_QUEUE = "overlay_show_queue"

    fun store(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun backgroundEnabled(context: Context) = store(context).getBoolean(BACKGROUND, true)
    fun setBackgroundEnabled(context: Context, value: Boolean) {
        store(context).edit().putBoolean(BACKGROUND, value).apply()
    }

    fun overlayWanted(context: Context) = store(context).getBoolean(OVERLAY, false)
    fun setOverlayWanted(context: Context, value: Boolean) {
        store(context).edit().putBoolean(OVERLAY, value).apply()
    }

    fun showRf(context: Context) = store(context).getBoolean(OVERLAY_SHOW_RF, true)
    fun showIms(context: Context) = store(context).getBoolean(OVERLAY_SHOW_IMS, true)
    fun showQueue(context: Context) = store(context).getBoolean(OVERLAY_SHOW_QUEUE, false)
}
