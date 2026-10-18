package com.greenguardians.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.greenguardians.app.MainActivity
import com.greenguardians.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Started only by the user's Start GPS action while the activity is visible. */
class TripTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store by lazy { TripStore.get(this) }
    private var recording: Job? = null
    private var recordingId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            store.pause()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!store.state.value.running || intent?.getStringExtra("tripId") != store.state.value.id) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (recording?.isActive == true) return START_NOT_STICKY
        recordingId = store.state.value.id
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.trip_channel), NotificationManager.IMPORTANCE_LOW))
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            recording = scope.launch {
                try {
                    OnDevice.track(this@TripTrackingService).collect { location ->
                        store.append(location)
                        if (!store.state.value.running) stopSelf()
                        else manager.notify(NOTIFICATION_ID, notification())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    store.pause("gps_failed")
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            store.pause("gps_failed")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, TripTrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.trip_recording))
            .setContentText(getString(R.string.trip_distance, store.state.value.km))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.trip_stop_save), stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        if (recordingId == store.state.value.id) store.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.greenguardians.app.STOP_TRIP"
        private const val CHANNEL = "gps_trip"
        private const val NOTIFICATION_ID = 1001
    }
}
