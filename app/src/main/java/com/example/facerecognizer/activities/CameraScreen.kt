package com.example.facerecognizer.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.facedetectionapp.utils.faceDetection.data.FaceDetectorHelper
import com.example.facerecognizer.R
import com.example.facerecognizer.caching.MyLRUCache
import com.example.facerecognizer.caching.OnBitmapAddedListener
import com.example.facerecognizer.databinding.ActivityCameraScreenBinding
import com.example.facerecognizer.utils.SharedPrefs
import com.example.facerecognizer.utils.helperClasses.Constants
import com.example.facerecognizer.utils.helperClasses.ImageHelper
import com.example.facerecognizer.viewModels.CameraXViewModel
import com.google.android.gms.tflite.java.TfLite
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.pluxai.utils.faceDetection.domain.FaceBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

class CameraScreen : AppCompatActivity() {
    private var binding: ActivityCameraScreenBinding? = null
    private lateinit var cameraSelector: CameraSelector
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private lateinit var imageAnalysis: ImageAnalysis
    private val cameraxViewModel = viewModels<CameraXViewModel>()
    private lateinit var faceDetectorHelper: FaceDetectorHelper
    private var imgProxy: ImageProxy? = null
    private lateinit var imageCapture: ImageCapture
    private lateinit var boundingBox: RectF
    private lateinit var cameraXViewModel: CameraXViewModel

    //    private var timer = ""
    var sec = 0
    private lateinit var statusText: String
    private lateinit var myLRUCache: MyLRUCache
    private var clickImg: Boolean = true
    private lateinit var uri: Uri
    private lateinit var overlayBitmap: Bitmap
    private lateinit var imgList: ArrayList<Bitmap>
    private lateinit var imgUriList: ArrayList<String>
    private var progress = 0
    private var retake: Boolean = false


    private lateinit var constants: Constants
    private lateinit var sharedPrefs: SharedPrefs
    private lateinit var imageHelper: ImageHelper

    private var imgCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraScreenBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        TfLite.initialize(this@CameraScreen)

//        CoroutineScope(Dispatchers.Main).launch {
//            while (true) {
//                val seconds = sec % 60
//                timer = String.format(Locale.getDefault(), "%02d", seconds)
//                sec++
//                delay(1000)
//            }
//        }
        init()
    }

    private fun init() {
        initElements()
        initTasks()
        initListeners()
        createFolderIfNotExists()
    }

    private fun createFolderIfNotExists() {
        try {
            // Obtaining the app's internal storage directory
            val internalStorageDir = applicationContext.filesDir
            val folderName = sharedPrefs.getString(Constants.EMPLOYEE_ID)

            if (!folderName.isNullOrEmpty()) {
                val folderPath = "$internalStorageDir/$folderName"
                //creating imageFolder
                val folder = File(folderPath)
                if (!folder.exists()) {
                    if (folder.mkdirs()) {
                        constants.log("Folder created successfully.")
                    } else {
                        constants.log("Failed to create folder.")
                    }
                }
            }
        } catch (e: Exception) {
            constants.log(e.toString())
        }
    }

    private fun checkIntent() {
        val result = intent.getBooleanExtra(Constants.INSUFFICIENT_IMG, false)
        retake = intent.getBooleanExtra(Constants.RETAKE_IMG, false)
        imgCounter = intent.getIntExtra(Constants.IMG_COUNT, -1)
        if (result || retake) {
            CoroutineScope(Dispatchers.IO).launch {
//                getImages()
            }
        }
    }

    override fun onRestart() {
        checkIntent()
        super.onRestart()
    }


    private fun changeProgress() {
        if (progress < 100) {
            progress += 100 / Constants.IMAGE_MAX
            if (retake)
                progress = 0
            seekProgressKnob(progress = progress, binding?.progressBar!!)
            retake = false
            binding?.progressBar?.progress = progress
        }
    }

    private fun moveBallToRandomPosition() {
        // Adjust the layout parameters as needed
        runOnUiThread {
            val layoutParams = binding?.ballIv?.layoutParams as RelativeLayout.LayoutParams

            val startX = binding?.ballIv?.x
            val startY = binding?.ballIv?.y

            // Generate random X and Y coordinates within the parent layout
            val maxX = (binding?.ballIv?.parent as View).width - layoutParams.width
            val maxY = (binding?.ballIv?.parent as View).height - layoutParams.height

            val newX = (0..maxX).random().toFloat()
            val newY = (0..maxY).random().toFloat()

            // Set the new position for the ball
            binding?.ballIv?.x = startX!!.toFloat()
            binding?.ballIv?.y = startY!!.toFloat()
            binding?.ballIv?.animate()?.x(newX)?.y(newY)?.duration = 1000
        }
    }

    override fun onResume() {
        checkIntent()
        super.onResume()
    }

    private fun initElements() {
        constants = Constants()
        sharedPrefs = SharedPrefs(this@CameraScreen)
        imageHelper = ImageHelper()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        imgList = ArrayList()
        imgUriList = ArrayList()
        //initializing elements used in caching
        // Create an instance of your custom LRU cache with a specified max size
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of the available memory
        myLRUCache = MyLRUCache(cacheSize)
    }

    @SuppressLint("SetTextI18n")
    private fun initListeners() {
        myLRUCache.setOnBitmapAddedListener(object : OnBitmapAddedListener {
            override fun onBitmapAdded(key: String, bitmap: Bitmap) {
                //here bitmap is the cached bitmap
                constants.log("Image Cached $sec")
                overlayBitmap = bitmap

                //clearing cache
                myLRUCache.removeBitmapFromMemoryCache(OVERLAY_IMG)

                imageHelper.deleteFileWithUri(uri, this@CameraScreen)
                saveImage()
            }

        })
    }

    private fun seekProgressKnob(progress: Int, progressBar: ProgressBar) {
        if (progress != 0) {
            val progressPercentage = progress.toFloat() / progressBar.max
            val knobX = progressBar.width * progressPercentage - 20

            binding?.knobFrame?.x = knobX
        }
    }

    private fun initTasks() {
        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        cameraxViewModel.value.processCameraProvider.observe(this) { provider ->
            processCameraProvider = provider
            bindCameraPreview()
            bindInputAnalyser()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun bindCameraPreview() {
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setDefaultResolution(Size(DEFAULT_WIDTH, DEFAULT_HEIGHT))
            .build()
        val rotation = binding?.cameraPreview?.display?.rotation
        if (rotation != null) {
            cameraPreview = Preview.Builder()
                .setTargetRotation(rotation)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
            cameraPreview.setSurfaceProvider(binding?.cameraPreview?.surfaceProvider)
        }
    }

    private fun bindInputAnalyser() {
        //initialize the face detector
        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute {
            faceDetectorHelper = FaceDetectorHelper(
                context = this,
                threshold = FaceDetectorHelper.PUNCH_THRESHOLD,
                currentDelegate = FaceDetectorHelper.DELEGATE_CPU,
                runningMode = RunningMode.LIVE_STREAM,
                faceDetectorListener = object : FaceDetectorHelper.DetectorListener {
                    override fun onError(error: String, errorCode: Int) {
                        Log.e("Error Saving", error)
                    }

                    override fun onResults(resultBundle: FaceDetectorHelper.ResultBundle) {
                        setFaceBoxesAndCapture(resultBundle)
                    }
                }
            )
        }
// ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(binding?.cameraPreview?.display!!.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        createImageProxy()

        try {
            processCameraProvider.unbindAll()
            processCameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imageCapture,
                cameraPreview,
                imageAnalysis,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createImageProxy() {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            imgProxy = imageProxy
            detectFace(imageProxy)
        }
    }

    private fun setFaceBoxesAndCapture(resultBundle: FaceDetectorHelper.ResultBundle) {
        //first clear the existing boxes
        binding?.faceBoxOverlay?.clear()

        //drawing the rectangles
        val detections = resultBundle.results[0].detections()
        setBoxes(detections)
        //capture
        if (!detections.isNullOrEmpty() && clickImg) {
            clickImage()
            clickImg = false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setBoxes(detections: MutableList<Detection>) {
        //drawing the rectangles
        if (detections.size == 1) {
            detections.forEach {
                val box = FaceBox(
                    binding!!.faceBoxOverlay,
                    imgProxy!!.cropRect,
                    it.boundingBox()
                )
                boundingBox = it.boundingBox()
                binding?.faceBoxOverlay?.add(box)
            }
        } else if (detections.size > 1) {
            detections.forEach {
                val box = FaceBox(
                    binding!!.faceBoxOverlay,
                    imgProxy!!.cropRect,
                    it.boundingBox()
                )
                binding?.faceBoxOverlay?.add(box)
            }
            getTheLargerFace(detections)
        }
    }

    private fun getTheLargerFace(detections: MutableList<Detection>) {
        val maxDetection =
            detections.maxByOrNull { it.boundingBox().height() * it.boundingBox().width() }
        val detectionList = maxDetection?.let { mutableListOf(it) } ?: mutableListOf()

        boundingBox = detectionList[0].boundingBox()
    }


    override fun onDestroy() {
        binding = null
        super.onDestroy()
    }

    private fun detectFace(imageProxy: ImageProxy) {
        faceDetectorHelper.detectLivestreamFrame(
            imageProxy = imageProxy,
        )
    }

    companion object {
        const val DEFAULT_WIDTH = 640
        const val DEFAULT_HEIGHT = 480
        const val OVERLAY_IMG = "overlayImg"
    }

    private fun clickImage() {
        statusText = "Clicking Image $sec"
        constants.log(statusText)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            val name =
                "${Environment.getExternalStorageDirectory()} + ${System.currentTimeMillis()}"
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                contentValues.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "pictures/FaceDetector"
                )

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
            //taking picture
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this@CameraScreen),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        CoroutineScope(Dispatchers.IO).launch {
//                            statusText = "Image Saved $sec"
                            constants.log(statusText)
                            cacheImageWithOverlay(outputFileResults)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Saving error==", exception.toString())
                    }
                })
        } else {
//            requestStoragePermission()
        }
    }

    private fun cacheImageWithOverlay(outputFileResults: ImageCapture.OutputFileResults) {
        statusText = "Caching with facebox $sec"
        constants.log(statusText)
        try {
            uri = outputFileResults.savedUri!!
            val bitmap = imageHelper.getBitmapFromUri(this@CameraScreen, uri) // saved image
            if (bitmap != null) {
                val rotatedBitmap =
                    imageHelper.rotateImageIfRequired(bitmap, uri, this@CameraScreen)
                val flippedBitmap = imageHelper.flipBitmapHorizontally(rotatedBitmap)

                //new flow save everything in cache until we get the non-blur image classified
                myLRUCache.addBitmapToMemoryCache(OVERLAY_IMG, flippedBitmap)
                //continue with the listener from here
            }
        } catch (e: Exception) {
            Log.e("Error saving: ", e.toString())
        }
    }

    private suspend fun cropAndSave(originalBitmap: Bitmap) = coroutineScope {
        statusText = "Cropping face $sec"
        constants.log(statusText)

        // Create cropped bitmap
        try {
            var croppedFace: Bitmap? = null
            async {
                croppedFace = imageHelper.cropBitmap(
                    originalBitmap,
                    boundingBox,
                )
            }.await()
            statusText = "Cropping finished $sec"
            constants.log(statusText)

            if (croppedFace != null) {
                imgList.add(croppedFace!!)
                setProgressAndProceed()
            } else {
                clickImg = true
            }
        } catch (e: Exception) {
            constants.log(e.toString())
            clickImg = true
        }
    }

    private suspend fun setProgressAndProceed() {
        CoroutineScope(Dispatchers.IO).launch {
            async {
                moveBallToRandomPosition()
            }.await()
            delay(1000)
            changeProgress()
            checkToProceed()
        }

    }

    private fun checkToProceed() {
        if (imgList.size < Constants.IMAGE_MAX)
            clickImg = true
        else
            saveImgAndChangeScreen()
    }

    private fun saveImgAndChangeScreen() {
        CoroutineScope(Dispatchers.IO).launch {
            imgUriList = imageHelper.saveMediaToLocalStorageAndGetUri(
                imgList, this@CameraScreen,
                sharedPrefs.getString(Constants.EMPLOYEE_ID)!!
            )
//            employeeViewModel.updateTrainingImagesOffline(mList = imgUriList)
            runOnUiThread {
//                val intent = Intent(this@CameraScreen, MainActivity::class.java)
//                startActivity(intent)
//                finish()
                constants.showToast("Faces added.", this@CameraScreen)
                onBackPressedDispatcher.onBackPressed()
                finish()
            }
        }
    }

    private fun saveImage() {
        //cropping and saving face as well
        CoroutineScope(Dispatchers.IO).launch {
            cropAndSave(overlayBitmap)
        }
    }
}
