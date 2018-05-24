package com.example.samplemlkittextrecognition

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.media.ExifInterface
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import java.io.File

private const val REQUEST_CODE_TAKE_PICTURE = 1
private const val PICTURE_FILE_NAME = "for_text_recognition.jpg"
private const val AUTHORITY = BuildConfig.APPLICATION_ID

class MainActivity : AppCompatActivity() {

    private var pictureFilePath = ""
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var textsListView: ListView
    private var arrayAdapter: ArrayAdapter<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fab = findViewById<FloatingActionButton>(R.id.main_fab)
        fab.setOnClickListener {
            takePictureForResult()
        }

        coordinatorLayout = findViewById(R.id.main_coordinator_layout)
        textsListView = findViewById(R.id.main_texts_recognized)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_TAKE_PICTURE -> {
                if (resultCode == Activity.RESULT_CANCELED) return

                val bitmap = getBitmapRotated()
                findViewById<ImageView>(R.id.main_thumbnail).setImageBitmap(bitmap)

                arrayAdapter?.run {
                    clear()
                    notifyDataSetChanged()
                }

                bitmap?.run {
                    runTextRecognition(bitmap)
                } ?: showSnackbar("Failed to take picture")
            }
        }
    }

    private fun takePictureForResult() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(packageManager) ?: return

        val pictureFile = getImageFilePath() ?: return
        pictureFilePath = pictureFile.absolutePath

        val pictureUri = FileProvider.getUriForFile(this, AUTHORITY, pictureFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pictureUri)

        startActivityForResult(intent, REQUEST_CODE_TAKE_PICTURE)
    }

    private fun getImageFilePath(): File? {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        dir ?: return null

        return File(dir, PICTURE_FILE_NAME)
    }

    private fun getBitmapRotated(): Bitmap? {
        val exif = ExifInterface(pictureFilePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED)

        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }

        val bitmap = BitmapFactory.decodeFile(pictureFilePath) ?: return null

        val transformMatrix = Matrix().apply { setRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transformMatrix, true)
    }

    private fun runTextRecognition(bitmap: Bitmap) {
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().visionTextDetector
        detector.detectInImage(image).addOnSuccessListener {
            processTextRecognitionResult(it)
        } .addOnFailureListener {
            it.printStackTrace()
            showSnackbar("Recognition failed")
        }
    }

    private fun processTextRecognitionResult(texts: FirebaseVisionText) {
        val blocks = texts.blocks
        if (blocks.isEmpty()) {
            showSnackbar("No texts recognized")
            return
        }

        val textBlocks = blocks.map { it.text }
        arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, textBlocks)
        textsListView.adapter = arrayAdapter
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(coordinatorLayout, msg, Snackbar.LENGTH_LONG).show()
    }
}
