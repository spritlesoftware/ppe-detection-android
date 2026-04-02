package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Hosts the camera preview and streams frames to [Detector].
 *
 * Uses CameraX [Preview] plus [ImageAnalysis] (RGBA, keep-latest) on a background executor.
 * Detection results update the preview overlay and the helmet/vest checklist when class names
 * match `helmet` and `vest` (see asset path [Constants.LABELS_PATH]).
 */
class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

        binding.cameraContainer.clipToOutline = true

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /** Obtains [ProcessCameraProvider] and binds use cases on the main thread. */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    /** Configures back camera, preview, and analyzer; wires analyzer output to [detector]. */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            detector.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) { startCamera() }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    /** Clears overlay and resets checklist when no boxes pass thresholds. */
    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.invalidate()
            updateChecklist(helmetDetected = false, vestDetected = false, confidence = 0f)
        }
    }

    /** Updates checklist from class names and refreshes [binding.overlay] with [boundingBoxes]. */
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            val helmetDetected = boundingBoxes.any { it.clsName == "helmet" }
            val vestDetected = boundingBoxes.any { it.clsName == "vest" }
            val maxConfidence = boundingBoxes.maxOfOrNull { it.cnf } ?: 0f

            updateChecklist(helmetDetected, vestDetected, maxConfidence)

            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
        }
    }

    /** Reflects PPE detection state and a floor on the confidence percentage shown in the UI. */
    private fun updateChecklist(helmetDetected: Boolean, vestDetected: Boolean, confidence: Float) {
        if (confidence > 0f) {
            val displayPct = maxOf(80f, confidence * 100)
            binding.confidenceText.text = String.format("%.1f%%", displayPct)
        } else {
            binding.confidenceText.text = "--"
        }

        if (helmetDetected) {
            binding.helmetStatus.text = "DETECTED"
            binding.helmetStatus.setTextColor(Color.parseColor("#4A9EFF"))
            binding.helmetCheck.visibility = View.VISIBLE
        } else {
            binding.helmetStatus.text = "NOT DETECTED"
            binding.helmetStatus.setTextColor(Color.parseColor("#FF3B3B"))
            binding.helmetCheck.visibility = View.GONE
        }

        if (vestDetected) {
            binding.vestStatus.text = "DETECTED"
            binding.vestStatus.setTextColor(Color.parseColor("#4A9EFF"))
            binding.vestCheck.visibility = View.VISIBLE
        } else {
            binding.vestStatus.text = "NOT DETECTED"
            binding.vestStatus.setTextColor(Color.parseColor("#FF3B3B"))
            binding.vestCheck.visibility = View.GONE
        }

        val validCount = listOf(helmetDetected, vestDetected).count { it }
        binding.validBadge.text = "$validCount / 2 VALID"
    }
}
