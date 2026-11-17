package io.paritytech.polkadotapp.feature_device_sync_debug_ui

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

class DeviceTransferDebugButtonInstaller : ContentProvider() {
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        application.registerActivityLifecycleCallbacks(ButtonCallbacks)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private object ButtonCallbacks : Application.ActivityLifecycleCallbacks {
        private const val BUTTON_TAG = "device_transfer_debug_button"
        private const val ROOT_ACTIVITY_CLASS_NAME = "io.paritytech.polkadotapp.app.root.presentation.root.RootActivity"

        override fun onActivityStarted(activity: Activity) {
            if (activity.javaClass.name != ROOT_ACTIVITY_CLASS_NAME) return

            val decorView = activity.window.decorView as ViewGroup
            if (decorView.findViewWithTag<TextView>(BUTTON_TAG) != null) return

            val layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = activity.statusBarHeightPx() }

            decorView.addView(createButton(activity), layoutParams)
        }

        private fun createButton(activity: Activity) = TextView(activity).apply {
            tag = BUTTON_TAG
            text = "Device transfer (debug)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(activity.dpToPx(12), activity.dpToPx(6), activity.dpToPx(12), activity.dpToPx(6))
            background = GradientDrawable().apply {
                cornerRadius = activity.dpToPx(16).toFloat()
                setColor(Color.argb(220, 103, 80, 164))
            }
            elevation = activity.dpToPx(8).toFloat()
            setOnClickListener { activity.startActivity(Intent(activity, DeviceTransferDebugActivity::class.java)) }
        }

        private fun Activity.statusBarHeightPx(): Int {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id != 0) resources.getDimensionPixelSize(id) else dpToPx(24)
        }

        private fun Activity.dpToPx(dp: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
