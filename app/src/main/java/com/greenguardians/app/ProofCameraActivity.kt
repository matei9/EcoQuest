package com.greenguardians.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.greenguardians.app.model.L
import com.greenguardians.app.ui.LocalIsEn
import com.greenguardians.app.ui.t
import com.greenguardians.app.ui.theme.GreenGuardiansTheme
import java.io.File

/** In-app camera: does not depend on a system IMAGE_CAPTURE activity being installed. */
class ProofCameraActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val en = AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("en")
        setContent {
            GreenGuardiansTheme {
                CompositionLocalProvider(LocalIsEn provides en) {
                    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
                    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
                    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
                    var capture by remember { mutableStateOf<ImageCapture?>(null) }
                    var error by remember { mutableStateOf(false) }
                    var saving by remember { mutableStateOf(false) }
                    val previewView = remember { PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
                    if (granted) DisposableEffect(previewView) {
                        var disposed = false
                        var provider: ProcessCameraProvider? = null
                        val future = ProcessCameraProvider.getInstance(this@ProofCameraActivity)
                        future.addListener({
                            if (!disposed) try {
                                val p = future.get()
                                provider = p
                                val selector = if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                                val photo = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                p.bindToLifecycle(this@ProofCameraActivity, selector, preview, photo)
                                capture = photo
                            } catch (e: Exception) {
                                Log.w("ProofCamera", "Camera initialization failed", e)
                                error = true
                            }
                        }, ContextCompat.getMainExecutor(this@ProofCameraActivity))
                        onDispose { disposed = true; provider?.unbindAll(); capture = null }
                    }
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(L("Fotografiază dovada", "Photograph your proof").t(), style = MaterialTheme.typography.titleLarge)
                            Text(L("Include bonul întreg și ține telefonul drept. Data și ora trebuie să se vadă clar.", "Include the whole receipt and hold the phone straight. Keep the date and time readable.").t())
                            if (granted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().weight(1f))
                            else {
                                Text(L("Permite accesul la cameră pentru fotografiere. Galeria rămâne disponibilă fără această permisiune.", "Allow camera access to take photos. Gallery selection remains available without it.").t())
                                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(L("Permite camera", "Allow camera").t()) }
                            }
                            if (error) Text(L("Camera nu a putut fi deschisă sau fotografia nu a putut fi salvată. Revino și folosește galeria.", "Couldn't open the camera or save the photo. Go back and use the gallery.").t(), color = MaterialTheme.colorScheme.error)
                            if (saving) CircularProgressIndicator()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !saving, onClick = { finish() }) { Text(L("Înapoi", "Back").t()) }
                                Button(enabled = granted && capture != null && !saving, onClick = {
                                    val photo = capture ?: return@Button
                                    saving = true
                                    error = false
                                    try {
                                        val folder = File(cacheDir, "proof_photos").apply { mkdirs() }
                                        val file = File.createTempFile("proof_", ".jpg", folder)
                                        photo.targetRotation = previewView.display.rotation
                                        photo.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(this@ProofCameraActivity),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                    val uri = FileProvider.getUriForFile(this@ProofCameraActivity, "$packageName.proofs", file)
                                                    setResult(RESULT_OK, Intent().setData(uri))
                                                    finish()
                                                }
                                                override fun onError(exception: ImageCaptureException) {
                                                    file.delete()
                                                    Log.w("ProofCamera", "Capture failed", exception)
                                                    saving = false
                                                    error = true
                                                }
                                            })
                                    } catch (e: Exception) {
                                        Log.w("ProofCamera", "Capture setup failed", e)
                                        saving = false
                                        error = true
                                    }
                                }) { Text(L("Fă poza", "Take photo").t()) }
                            }
                        }
                    }
                }
            }
        }
    }
}
