package com.example.facerecognizer.utils.helperClasses

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class ImageHelper @Inject constructor() {

    fun rotateImageIfRequired(bitmap: Bitmap, uri: Uri, mContext: Context): Bitmap {
        val inputStream: InputStream? = mContext.contentResolver.openInputStream(uri)
        inputStream?.use {
            val exif = ExifInterface(it)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            }

            return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        }

        return bitmap
    }

    fun deleteFileWithUri(uri: Uri, mContext: Context) {
        try {
            val contentResolver: ContentResolver = mContext.contentResolver

            // Delete the file using the content resolver
            contentResolver.delete(uri, null, null)

        } catch (e: Exception) {
            Log.e("Delete File Error", e.toString())
        }
    }

    fun getBitmapFromView(bitmap: Bitmap?, overlay: View): Bitmap? {
        var combinedBitmap: Bitmap? = null
        try {
            if (bitmap != null) {
                val overlayBitmap =
                    Bitmap.createBitmap(overlay.width, overlay.height, Bitmap.Config.ARGB_8888)
                val overlayCanvas = Canvas(overlayBitmap)
                flipCanvasHorizontally(overlayCanvas)
                overlay.draw(overlayCanvas)

                // Create a new bitmap with the same size as the original bitmap
                combinedBitmap =
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(combinedBitmap)

                // Draw the original bitmap
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                val overlayX = (bitmap.width - overlay.width) / 2f
                val overlayY = (bitmap.height - overlay.height) / 2f

                canvas.drawBitmap(overlayBitmap, overlayX, overlayY, null)
            }
        } catch (e: Exception) {
            Log.e("Bitmap can't be generated", e.toString())
        }
        return combinedBitmap
    }

    private fun flipCanvasHorizontally(canvas: Canvas) {
        val matrix = Matrix()
        matrix.setScale(-1f, 1f, canvas.width / 2f, 0f) // Flip horizontally around the center
        canvas.concat(matrix)
    }

    suspend fun saveMediaToLocalStorageAndGetUri(
        list: ArrayList<Bitmap>,
        mContext: Context,
        folderName: String
    ): ArrayList<String> {
        // List to store the URIs of saved images
        val savedImageUris = ArrayList<String>()

        // Using CoroutineScope to launch a coroutine in the IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            // Iterating through the list of Bitmaps
            list.forEach { listBitmap ->
                // Generating a unique file name using the current timestamp
                val filename = "${System.currentTimeMillis()}.jpeg"

                // Output stream for writing the Bitmap data to internal storage
                var fos: FileOutputStream? = null

                try {
                    // Obtaining the app's internal storage directory
                    val internalStorageDir = mContext.applicationContext.filesDir

                    if (folderName.isNotEmpty()) {
                        val folderPath = "$internalStorageDir/$folderName"
                        val folder = File(folderPath)
                        if (!folder.exists()) {
                            folder.mkdir()
                        }
//                        Constants().log(folderPath+"=====>")

                        //creating imageFolder
                        val imageFolder = File(folderPath)

                        // Creating a File object with the specified directory and filename
                        val imageFile = File(imageFolder, filename)

                        // Opening a FileOutputStream using the File object
                        fos = FileOutputStream(imageFile)

                        // Using the output stream to compress and write the Bitmap data
                        listBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)

                        // Obtaining the URI of the saved image
                        val imageUri = FileProvider.getUriForFile(
                            mContext.applicationContext,
                            "com.pluxai.fileprovider",
                            imageFile
                        )
                        // Adding the URI to the list
                        savedImageUris.add(imageUri.toString())
                    }
                } catch (e: IOException) {
                    // Handle exceptions appropriately
                    e.printStackTrace()
                } finally {
                    // Close the output stream in the finally block
                    fos?.close()
                }
            }
        }.join() // Wait for the coroutine to complete before returning

        // Return the list of URIs
        return savedImageUris
    }

    suspend fun saveImgAndGetUri(
        bitmap: Bitmap,
        mContext: Context
    ): String? {
        // List to store the URIs of saved images
        var savedImageUri: String? = null

        // Using CoroutineScope to launch a coroutine in the IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            // Iterating through the list of Bitmaps

            // Generating a unique file name using the current timestamp
            val filename = "${System.currentTimeMillis()}.jpeg"

            // Output stream for writing the Bitmap data to internal storage
            var fos: FileOutputStream? = null

            try {
                // Obtaining the app's internal storage directory
                val internalStorageDir = mContext.applicationContext.filesDir

                // Creating a File object with the specified directory and filename
                val imageFile = File(internalStorageDir, filename)

                // Opening a FileOutputStream using the File object
                fos = FileOutputStream(imageFile)

                // Using the output stream to compress and write the Bitmap data
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)

                // Obtaining the URI of the saved image
                val imageUri = FileProvider.getUriForFile(
                    mContext.applicationContext,
                    "com.pluxai.fileprovider",
                    imageFile
                )

                // Adding the URI to the list
                savedImageUri = imageUri.toString()

            } catch (e: IOException) {
                // Handle exceptions appropriately
                e.printStackTrace()
            } finally {
                // Close the output stream in the finally block
                fos?.close()
            }
        }.join()

        // Return the list of URIs
        return savedImageUri
    }

    fun getBitmapFromUri(mContext: Context, uri: Uri): Bitmap? {
        val inputStream: InputStream?
        return try {

            // Use content resolver to open an input stream from the URI
            inputStream = mContext.contentResolver.openInputStream(uri)

            // Decode the input stream into a Bitmap
            val bitmap = BitmapFactory.decodeStream(inputStream)

//             Close the input stream
            inputStream?.close()

            bitmap // Return the decoded Bitmap
        } catch (e: Exception) {
            Log.e("Error Occurred==", e.toString())
            null
        }
    }

    fun flipBitmapHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.setScale(-1f, 1f)

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun cropBitmap(
        originalBitmap: Bitmap,
        boundingBox: RectF,
    ): Bitmap? {
        var croppedBitmap: Bitmap? = null
        // Create a cropped bitmap
        try {
            withContext(Dispatchers.IO) {
                async {
                    originalBitmap.let {
                        croppedBitmap = Bitmap.createBitmap(
                            it,
                            boundingBox.left.toInt(),
                            boundingBox.top.toInt(),
                            boundingBox.width().toInt(),
                            boundingBox.height().toInt()
                        )
                    }
                }.await()
            }
        } catch (e: Exception) {
            Log.e("here==", e.toString())
        }
        return croppedBitmap
    }

    suspend fun downloadImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Create a URL object from the provided image URL
            val url = URL(imageUrl)

            // Open a connection to the URL
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()

            // Get the InputStream from the connection
            val input: InputStream = connection.inputStream

            // Decode the InputStream into a Bitmap using BitmapFactory
            val bitmap: Bitmap? = BitmapFactory.decodeStream(input)

            // Close the InputStream
            input.close()

            return@withContext bitmap
        } catch (e: IOException) {
            // Handle errors, e.g., network issues or invalid URL
            e.printStackTrace()
            return@withContext null
        }
    }

    @SuppressLint("FileEndsWithExt")
    fun clearDataInsideFolder(folder: File) {
        val files = folder.listFiles()
        if (files != null) {
            for (file in files) {
                // Delete files
                if (file.isFile && file.name.endsWith(".jpeg"))
                    file.delete()
            }
        }
    }
}