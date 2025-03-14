package com.example.myapplication

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.view.PreviewView
import androidx.activity.result.contract.ActivityResultContracts

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.graphics.BitmapFactory


import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream




class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageView: ImageView
    private lateinit var imageCapture: ImageCapture
    private lateinit var previewView: PreviewView

    companion object {
        private const val REQUEST_CODE_CAMERA = 1001
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        imageView = findViewById(R.id.imageView) // Fixed issue by adding back the ImageView
        val btnCapture = findViewById<Button>(R.id.btnCapture)

        setCircularPreview()//circular Preview

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnCapture.setOnClickListener {
            captureImage()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),  // Use MainExecutor for UI updates
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    runOnUiThread {

                        Toast.makeText(this@MainActivity, "Image Saved to Gallery!", Toast.LENGTH_SHORT).show()

                        // Return to MainActivity directly
                        val intent = Intent(this@MainActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish() // Close current activity

                        savedUri?.let {
                            uri->imageView.setImageURI(uri)
                            imageView.visibility = ImageView.VISIBLE
                            // Delay to ensure the image is saved before processing
                            imageView.postDelayed({

                                calculateRGBValues(uri)
                            }, 1500)  // Delay of 1.5 seconds to ensure image saving
                        } ?: run {
                            Toast.makeText(this@MainActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                        }
                    }

                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to Save Image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
    private fun calculateRGBValues(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            if (bitmap != null) {
                var totalRed = 0
                var totalGreen = 0
                var totalBlue = 0
                var pixelCount = 0

                for (x in 0 until bitmap.width) {
                    for (y in 0 until bitmap.height) {
                        val pixel = bitmap.getPixel(x, y)
                        totalRed += (pixel shr 16) and 0xFF
                        totalGreen += (pixel shr 8) and 0xFF
                        totalBlue += pixel and 0xFF
                        pixelCount++
                    }
                }

                val avgRed = totalRed / pixelCount
                val avgGreen = totalGreen / pixelCount
                val avgBlue = totalBlue / pixelCount

                val imageName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                generatePDF(imageName, avgRed, avgGreen, avgBlue)

                Toast.makeText(
                    this,
                    "RGB Values - R: $avgRed, G: $avgGreen, B: $avgBlue",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Failed to load image for RGB calculation", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading image for RGB calculation", Toast.LENGTH_SHORT).show()
        }
    }





    private fun setCircularPreview() {
        previewView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        previewView.clipToOutline = true
    }

//    implementation 'com.itextpdf:itextg:5.5.10'

    private fun generatePDF(imageName: String, avgRed: Int, avgGreen: Int, avgBlue: Int) {
        try {
            val pdfFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "Image_RGB_Report_$imageName.pdf"
            )

            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            document.open()

            // Title
            val title = Paragraph("Image RGB Report\n\n", Font(Font.FontFamily.HELVETICA, 18f, Font.BOLD))
            title.alignment = Element.ALIGN_CENTER
            document.add(title)

            // Image Name
            val imageNameText = Paragraph("Name: $imageName\n\n", Font(Font.FontFamily.HELVETICA, 14f))
            document.add(imageNameText)

            // RGB Table with expanded details
            val table = PdfPTable(4)
            table.addCell("Color")
            table.addCell("Value")
            table.addCell("Normalized (%)")
            table.addCell("Intensity Level")

            val total = avgRed + avgGreen + avgBlue

            table.addCell("Red")
            table.addCell(avgRed.toString())
            table.addCell(String.format("%.2f", (avgRed.toFloat() / total) * 100) + "%")
            table.addCell(if (avgRed > avgGreen && avgRed > avgBlue) "High" else "Normal")

            table.addCell("Green")
            table.addCell(avgGreen.toString())
            table.addCell(String.format("%.2f", (avgGreen.toFloat() / total) * 100) + "%")
            table.addCell(if (avgGreen > avgRed && avgGreen > avgBlue) "High" else "Normal")

            table.addCell("Blue")
            table.addCell(avgBlue.toString())
            table.addCell(String.format("%.2f", (avgBlue.toFloat() / total) * 100) + "%")
            table.addCell(if (avgBlue > avgRed && avgBlue > avgGreen) "High" else "Normal")

            document.add(table)
            document.close()

            Toast.makeText(this, "PDF saved: ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
    }
}
