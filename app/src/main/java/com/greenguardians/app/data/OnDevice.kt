package com.greenguardians.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** On-device ML Kit + location helpers. Only results (text, labels, hashes, points) go to the server. */
object OnDevice {
    suspend fun ocr(ctx: Context, uri: Uri): String =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromFilePath(ctx, uri)).await().text

    suspend fun labels(ctx: Context, uri: Uri): List<String> =
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromFilePath(ctx, uri)).await()
            .filter { it.confidence >= 0.6f }
            .map { it.text }

    /** SHA-256 of the image bytes: the server refuses a proof image used before. */
    suspend fun sha256(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Google code scanner UI; no camera permission needed. Null if cancelled. */
    suspend fun scanQr(ctx: Context): String? = GmsBarcodeScanning.getClient(ctx).startScan().await().rawValue

    @SuppressLint("MissingPermission") // callers request location permission first
    suspend fun currentLocation(ctx: Context): Location? =
        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()

    /**
     * GPS fixes every ~5 s while collected.
     * shortcut: stops when the screen closes; needs a foreground service for background tracking.
     */
    @SuppressLint("MissingPermission")
    fun track(ctx: Context): Flow<Location> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
