package com.example.facerecognizer.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.utils.faceDetection.data.FaceDetectorHelper
import com.example.facerecognizer.caching.MyLRUCache
import com.example.facerecognizer.caching.OnBitmapAddedListener
import com.example.facerecognizer.databinding.ActivityMainBinding
import com.example.facerecognizer.utils.SharedPrefs
import com.example.facerecognizer.utils.customEmpIdDialog
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
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private lateinit var imageHelper: ImageHelper

    private lateinit var cameraSelector: CameraSelector
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var faceDetectorHelper: FaceDetectorHelper
    private val cameraxViewModel = viewModels<CameraXViewModel>()
    private lateinit var imageCapture: ImageCapture
    private lateinit var boundingBox: RectF
    private var imgProxy: ImageProxy? = null
    private lateinit var myLRUCache: MyLRUCache
    private var cameraPermission = android.Manifest.permission.CAMERA
    private var isDialogVisible = false
    private lateinit var constants: Constants
    private var clickImg: Boolean = true
    private lateinit var uri: Uri
    private lateinit var overlayBitmap: Bitmap
    private lateinit var sharedPrefs: SharedPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(binding?.root)
        TfLite.initialize(this@MainActivity)
//        init()
    }

    private fun init() {
        initElements()
        initTasks()
        initListeners()
    }

    private fun initElements() {
        constants = Constants()
        imageHelper = ImageHelper()
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of the available memory
        myLRUCache = MyLRUCache(cacheSize)
        sharedPrefs = SharedPrefs(this@MainActivity)
    }

    private fun initTasks() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        takePermission()
        initCamera()
    }

    private fun initCamera() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        cameraxViewModel.value.processCameraProvider.observe(this@MainActivity) { provider ->
            processCameraProvider = provider
            bindCameraPreview()
            bindInputAnalyzer()
        }
    }

    private fun bindInputAnalyzer() {
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
        val rotation = binding?.cameraPreview?.display?.rotation
        if (rotation != null) {
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            createImageProxy()
        }
        try {
            processCameraProvider.unbindAll()

            // Add cameraPreview and imageAnalysis to the use case list
            processCameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imageCapture,
                cameraPreview,  // Add this line to include cameraPreview
                imageAnalysis
            )
            binding?.progressLayout?.visibility = View.GONE
//            startCounter()
        } catch (e: Exception) {
            e.printStackTrace()
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
            //to measure the inactivity properly setting the timer to 0 everytime the face is detected

            clickImage()
            clickImg = false
        }
    }

    private fun clickImage() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            val name =
                "${Environment.getExternalStorageDirectory()} + ${System.currentTimeMillis()}"
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

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
                ContextCompat.getMainExecutor(this@MainActivity),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        CoroutineScope(Dispatchers.IO).launch {
                            cacheImageWithOverlay(outputFileResults)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Saving error==", exception.toString())
                    }
                })
        }
    }

    private fun cacheImageWithOverlay(outputFileResults: ImageCapture.OutputFileResults) {
        try {
            uri = outputFileResults.savedUri!!
            val bitmap = imageHelper.getBitmapFromUri(this@MainActivity, uri) // saved image
            val rotatedBitmap = imageHelper.rotateImageIfRequired(bitmap!!, uri, this@MainActivity)
            val flippedBitmap = imageHelper.flipBitmapHorizontally(rotatedBitmap)
            myLRUCache.addBitmapToMemoryCache(OVERLAY_IMG, flippedBitmap)
        } catch (e: Exception) {
            Log.e("Error saving: ", e.toString())
        }
    }

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

    private fun checkEmpId(empId: String, dialog: Dialog) {
        if (empId.isNotEmpty()) {
            dialog.dismiss()
            sharedPrefs.saveString(Constants.EMPLOYEE_ID, empId)
            constants.changeScreen(this@MainActivity, CameraScreen::class.java)
        }
    }

    override fun onResume() {
        binding?.progressLayout?.visibility = View.VISIBLE
        init()
        super.onResume()
    }

    private fun openEmpIdDialog() {
        isDialogVisible = true
        customEmpIdDialog(positive = { empId, dialog ->
            checkEmpId(empId, dialog)
        }
        ) {
            isDialogVisible = false
        }
    }

    private fun createImageProxy() {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            imgProxy = imageProxy
            detectFace(imageProxy)
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        try {
            faceDetectorHelper.detectLivestreamFrame(
                imageProxy = imageProxy,
            )
        } catch (e: Exception) {
            createImageProxy()
        }
    }

    companion object {
        const val DEFAULT_WIDTH = 640
        const val DEFAULT_HEIGHT = 480
        const val OVERLAY_IMG = "overlayImg"
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

    private fun takePermission() {
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                cameraPermission
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(cameraPermission), 1)
        }
    }

    private fun initListeners() {
        binding?.addEmpCv?.setOnClickListener {
            openEmpIdDialog()
        }

        binding?.startTrainingCv?.setOnClickListener {
            val folderNames: ArrayList<String> = ArrayList()
            val folderPaths: ArrayList<String> = ArrayList()

            //getting all the employees by getting all the folder names
            val internalDir = "$filesDir"
            val mainFileLocation = File(internalDir)
            if (mainFileLocation.exists() && mainFileLocation.isDirectory) {
                mainFileLocation.listFiles { file ->
                    file.isDirectory
                }?.map {
                    folderNames.add(it.toString().split("files/")[1])
                    folderPaths.add(it.toString())
                }
            }
            if (folderNames.isNotEmpty()) {
                constants.showToast("$folderNames", this@MainActivity)
                constants.log("$folderNames")
            }
            if (folderPaths.isNotEmpty())
                constants.log("$folderPaths")
        }

        myLRUCache.setOnBitmapAddedListener(object : OnBitmapAddedListener {
            override fun onBitmapAdded(key: String, bitmap: Bitmap) {
                //here bitmap is the cached bitmap
                constants.log("Image Cached")
                overlayBitmap = bitmap

                //clearing cache
                myLRUCache.removeBitmapFromMemoryCache(OVERLAY_IMG)

                imageHelper.deleteFileWithUri(uri, this@MainActivity)
                saveImage()
            }
        })
    }

    private fun saveImage() {
        //cropping and saving face as well
        CoroutineScope(Dispatchers.IO).launch {
            cropAndSave(overlayBitmap)
        }
    }

    private suspend fun cropAndSave(originalBitmap: Bitmap) = coroutineScope {
//        Log.e("result ====>", "Cropped Img started: $timer")

        // Create cropped bitmap
        try {
            var croppedFace: Bitmap? = null
            async {
                croppedFace = imageHelper.cropBitmap(originalBitmap, boundingBox)
            }.await()

            //add this cropped image to the model given by ancil from this point
            if (croppedFace != null) {
//                Log.e("result ====>", "Cropped Img Saved: $timer")
                constants.log("Image Cropped successfully.")
                recognizeFace(croppedFace!!)
            } else {
                clickImg = true
            }
        } catch (e: Exception) {
            constants.log("exception: ${e.message}")
            clickImg = true
        }
    }

    private fun recognizeFace(croppedFace: Bitmap) {
        binding?.notYouTv?.visibility = View.VISIBLE
//        Log.e("result ====>", "Recognition started: $timer")

//        val results = faceRecognizerHelper.recognize(croppedFace, this@PunchScreen)
//        Log.e("result ====>", "Recognition results received: $timer")
//        constants.log("$results======> Recognizer result")
//        clickImg =
////            if () { //compare it with: weather the face is recognized or not
//            if (true) {
//                time = constants.formatTime(mCalendar.time)
//                date = constants.formatDate(mCalendar.time)
//                if (results != null) {
//                    setDataOnDialogAndSave()
//                }
//                false
//            } else {
//                //repeating the whole process again
//                true
//            }
    }
}