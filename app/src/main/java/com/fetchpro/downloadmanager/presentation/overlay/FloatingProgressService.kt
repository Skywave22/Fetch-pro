package com.fetchpro.downloadmanager.presentation.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import com.fetchpro.downloadmanager.R
import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Transparent progress bar on top of all windows - ADM feature
 * Requires SYSTEM_ALERT_WINDOW permission
 */
@AndroidEntryPoint
class FloatingProgressService : Service() {

    @Inject lateinit var downloadDao: DownloadDao

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showFloatingView()
        startMonitoring()
        return START_STICKY
    }

    @Suppress("ClickableViewAccessibility")
    private fun showFloatingView() {
        if (floatingView != null) return

        try {
            val inflater = LayoutInflater.from(this)
            floatingView = inflater.inflate(R.layout.overlay_progress, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

            // Make touchable to drag (optional)
            floatingView?.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View?, event: android.view.MotionEvent): Boolean {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager?.updateViewLayout(floatingView, params)
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val active = downloadDao.getDownloadsByStates(listOf(DownloadStateEntity.DOWNLOADING))
                    if (active.isEmpty()) {
                        updateView(0, "No active downloads")
                    } else {
                        val first = active.first()
                        val progress = if (first.totalBytes != null && first.totalBytes > 0) {
                            (first.downloadedBytes * 100 / first.totalBytes).toInt()
                        } else 0
                        updateView(progress, "${first.fileName}: $progress% • ${active.size} active")
                    }
                    delay(1000)
                } catch (_: Exception) {
                    delay(2000)
                }
            }
        }
    }

    private fun updateView(progress: Int, text: String) {
        try {
            floatingView?.findViewById<ProgressBar>(R.id.overlay_progress_bar)?.progress = progress
            floatingView?.findViewById<TextView>(R.id.overlay_text)?.text = text
        } catch (_: Exception) {}
    }

    private fun hideFloatingView() {
        try {
            floatingView?.let { windowManager?.removeView(it) }
            floatingView = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        serviceScope.cancel()
        hideFloatingView()
        super.onDestroy()
    }
}
