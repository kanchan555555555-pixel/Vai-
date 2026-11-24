package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.example.MainActivity
import com.example.R
import com.example.model.MirrorQuality
import com.example.network.MirrorFrameHub
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream

object MirrorServiceBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val stopEvents = _events.asSharedFlow()

    fun notifyStopped() {
        _events.tryEmit(Unit)
    }
}

class ScreenMirrorService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var currentQuality: MirrorQuality = MirrorQuality.BALANCED
    private var densityDpi: Int = DisplayMetrics.DENSITY_HIGH
    private var captureWidth: Int = 720
    private var captureHeight: Int = 1280
    private var lastFrameEmitMs: Long = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i("ScreenMirrorService", "MediaProjection stopped by system or user")
            stopMirroringResources()
            MirrorServiceBus.notifyStopped()
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            val (scaledW, scaledH) = scaleToMaxEdge(width, height, currentQuality.maxLongEdge)
            if (scaledW != captureWidth || scaledH != captureHeight) {
                captureHandler?.post {
                    recreateSurfaceAndResize(scaledW, scaledH)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMirroringResources()
                MirrorServiceBus.notifyStopped()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_QUALITY -> {
                val qName = intent.getStringExtra(EXTRA_QUALITY)
                currentQuality = MirrorQuality.entries.firstOrNull { it.name == qName }
                    ?: MirrorQuality.BALANCED
                val (rawW, rawH, _) = queryScreenMetrics()
                val (scaledW, scaledH) = scaleToMaxEdge(rawW, rawH, currentQuality.maxLongEdge)
                captureHandler?.post {
                    recreateSurfaceAndResize(scaledW, scaledH)
                }
                return START_STICKY
            }

            ACTION_START, null -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    ?: Activity.RESULT_CANCELED
                val dataIntent = intent?.let {
                    IntentCompat.getParcelableExtra(it, EXTRA_DATA_INTENT, Intent::class.java)
                }
                val qName = intent?.getStringExtra(EXTRA_QUALITY)
                currentQuality = MirrorQuality.entries.firstOrNull { it.name == qName }
                    ?: MirrorQuality.BALANCED
                val receiverUrl = intent?.getStringExtra(EXTRA_RECEIVER_URL) ?: "Hotspot TV"

                if (resultCode != Activity.RESULT_OK || dataIntent == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Android 14+ requires startForeground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                // BEFORE calling MediaProjectionManager.getMediaProjection().
                val notification = buildNotification(receiverUrl)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                acquireWakeLock()
                startProjection(resultCode, dataIntent)
            }
        }
        return START_STICKY
    }

    private fun startProjection(resultCode: Int, dataIntent: Intent) {
        stopMirroringResources()

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = runCatching { mgr.getMediaProjection(resultCode, dataIntent) }.getOrNull()
        if (projection == null) {
            MirrorServiceBus.notifyStopped()
            stopSelf()
            return
        }
        mediaProjection = projection

        val thread = HandlerThread("HotCastMirrorThread").apply { start() }
        captureThread = thread
        val handler = Handler(thread.looper)
        captureHandler = handler

        projection.registerCallback(projectionCallback, handler)

        val (rawW, rawH, dpi) = queryScreenMetrics()
        densityDpi = dpi
        val (scaledW, scaledH) = scaleToMaxEdge(rawW, rawH, currentQuality.maxLongEdge)
        captureWidth = scaledW
        captureHeight = scaledH

        val reader = createImageReader(scaledW, scaledH, handler)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "HotCastScreenMirror",
            scaledW,
            scaledH,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )

        MirrorFrameHub.setMirroring(true, scaledW, scaledH)
    }

    private fun createImageReader(width: Int, height: Int, handler: Handler): ImageReader {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            val minIntervalMs = (1000L / currentQuality.targetFps.coerceAtLeast(1))
            val now = System.currentTimeMillis()
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                if (now - lastFrameEmitMs < minIntervalMs) {
                    return@setOnImageAvailableListener
                }
                lastFrameEmitMs = now

                val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmapWidth = image.width + (if (pixelStride > 0) rowPadding / pixelStride else 0)

                val rawBitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
                rawBitmap.copyPixelsFromBuffer(buffer)

                val finalBitmap = if (bitmapWidth != image.width) {
                    Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height).also {
                        rawBitmap.recycle()
                    }
                } else {
                    rawBitmap
                }

                val baos = ByteArrayOutputStream(96 * 1024)
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality.jpegQuality, baos)
                finalBitmap.recycle()

                MirrorFrameHub.publishFrame(baos.toByteArray(), image.width, image.height)
            } catch (e: Exception) {
                Log.w("ScreenMirrorService", "Frame capture warning: ${e.message}")
            } finally {
                runCatching { image.close() }
            }
        }, handler)
        return reader
    }

    private fun recreateSurfaceAndResize(newW: Int, newH: Int) {
        val handler = captureHandler ?: return
        val vd = virtualDisplay ?: return
        captureWidth = newW
        captureHeight = newH
        val oldReader = imageReader
        val nextReader = createImageReader(newW, newH, handler)
        imageReader = nextReader
        vd.resize(newW, newH, densityDpi)
        vd.surface = nextReader.surface
        runCatching { oldReader?.close() }
        MirrorFrameHub.setMirroring(true, newW, newH)
    }

    private fun queryScreenMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dpi = resources.displayMetrics.densityDpi.coerceAtLeast(160)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Triple(bounds.width().coerceAtLeast(360), bounds.height().coerceAtLeast(640), dpi)
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            Triple(dm.widthPixels.coerceAtLeast(360), dm.heightPixels.coerceAtLeast(640), dpi)
        }
    }

    private fun scaleToMaxEdge(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
        val longEdge = maxOf(width, height).coerceAtLeast(1)
        val scale = if (longEdge > maxLongEdge) maxLongEdge.toFloat() / longEdge.toFloat() else 1f
        val w = ((width * scale).toInt() / 2) * 2
        val h = ((height * scale).toInt() / 2) * 2
        return w.coerceAtLeast(320) to h.coerceAtLeast(320)
    }

    private fun stopMirroringResources() {
        MirrorFrameHub.setMirroring(false)
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
        captureHandler = null
        releaseWakeLock()
    }

    override fun onDestroy() {
        stopMirroringResources()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HotCastTV::MirrorWakeLock").apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HotCast Screen Mirroring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live offline screen mirroring over Hotspot"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(receiverUrl: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            200,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenMirrorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            201,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Screen Mirroring Live • $receiverUrl")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hotcast_mirror_channel"
        private const val NOTIFICATION_ID = 4402

        const val ACTION_START = "com.example.hotcast.mirror.START"
        const val ACTION_UPDATE_QUALITY = "com.example.hotcast.mirror.QUALITY"
        const val ACTION_STOP = "com.example.hotcast.mirror.STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA_INTENT = "extra_data_intent"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_RECEIVER_URL = "extra_receiver_url"

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            quality: MirrorQuality,
            receiverUrl: String
        ) {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA_INTENT, data)
                putExtra(EXTRA_QUALITY, quality.name)
                putExtra(EXTRA_RECEIVER_URL, receiverUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateQuality(context: Context, quality: MirrorQuality) {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                action = ACTION_UPDATE_QUALITY
                putExtra(EXTRA_QUALITY, quality.name)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenMirrorService::class.java))
        }
    }
}
