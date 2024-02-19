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
import com.example.facerecognizer.utils.TemplateMatching
import com.example.facerecognizer.utils.customEmpIdDialog
import com.example.facerecognizer.utils.helperClasses.Constants
import com.example.facerecognizer.utils.helperClasses.ImageHelper
import com.example.facerecognizer.utils.helperClasses.OnComplete
import com.example.facerecognizer.utils.helperClasses.PythonHelper
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
import java.nio.ByteBuffer
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
    private lateinit var pythonHelper: PythonHelper
    private lateinit var templateMatching: TemplateMatching

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
        templateMatching = TemplateMatching()
        imageHelper = ImageHelper()
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of the available memory
        myLRUCache = MyLRUCache(cacheSize)
        sharedPrefs = SharedPrefs(this@MainActivity)
        pythonHelper = PythonHelper(
            constants = constants
        )
    }

    private fun initTasks() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        takePermission()
        initCamera()
        calculateDistance()
    }

    private fun calculateDistance() {
        val embFile1 = "SQ1001-03.emb"
        val embFile2 = "SQ1001-08.emb"
        val embFile3 = "SQ1002-03.emb"

        CoroutineScope(Dispatchers.IO).launch {
            var embPath1: String? = ""
            var embPath2: String? = ""
            var embPath3: String? = ""
            async {
                //first move the files form assets to files directory
                embPath1 = templateMatching.copyEmbFileToInternalStorage(
                    context = this@MainActivity,
                    fileName = embFile1
                )
                embPath2 = templateMatching.copyEmbFileToInternalStorage(
                    context = this@MainActivity,
                    fileName = embFile2
                )
                embPath3 = templateMatching.copyEmbFileToInternalStorage(
                    context = this@MainActivity,
                    fileName = embFile3
                )
            }.join()
            if (embPath1?.isNotEmpty() == true && embPath2?.isNotEmpty() == true && embPath3?.isNotEmpty() == true) {
                val embeddingsFile1 = embPath1?.let { File(it) }
                val embeddingsFile2 = embPath2?.let { File(it) }
                val embeddingsFile3 = embPath3?.let { File(it) }

                val embeddings1 = embeddingsFile1?.let { templateMatching.readFile(it) }
                val embeddings2 = embeddingsFile2?.let { templateMatching.readFile(it) }
                val embeddings3 = embeddingsFile3?.let { templateMatching.readFile(it) }

                val embeddingAncil = doubleArrayOf(
                    -0.03907752, 0.04662498, 0.03661705, -0.03241163, 0.0325419, -0.05006482,
                    0.04295967, -0.05351035, -0.05320838, 0.02980076, -0.04466035, -0.05205459,
                    -0.03981203, 0.03979319, -0.0402626, -0.04956175, -0.04808801, -0.05370852,
                    0.04170756, 0.04942545, -0.05056556, -0.04112625, -0.04785421, 0.03822713,
                    -0.05048643, 0.03950521, 0.05124611, -0.05169573, -0.04492895, 0.03823616,
                    0.05475267, 0.06044894, -0.0457947, 0.04774347, 0.02923133, 0.03007946,
                    -0.04917415, 0.05408973, -0.04999632, 0.03583841, 0.04232995, -0.04231192,
                    -0.04747618, 0.0454551, -0.04848816, -0.04949627, 0.03746596, -0.05497975,
                    -0.046361, -0.05028993, -0.05247815, -0.04585716, 0.04016914, -0.04448242,
                    0.03344505, 0.03599306, 0.04597923, 0.04099194, -0.04847064, 0.04834904,
                    0.04001923, 0.04348984, -0.04292668, 0.04879037, -0.0407107, 0.04343339,
                    -0.0404552, 0.03900788, -0.0455945, -0.04697786, 0.04463395, 0.04409931,
                    -0.04578034, 0.04387911, -0.04859884, 0.04139702, 0.04035718, 0.04054197,
                    0.04470433, -0.04754908, 0.04455233, -0.0423056, 0.04487411, -0.04679025,
                    0.04895915, -0.04659316, -0.04410005, 0.03945887, 0.04283721, -0.0456351,
                    0.04284558, -0.04765543, 0.04068203, 0.03929212, 0.04277702, -0.04523964,
                    0.04553356, -0.04622968, 0.03917929, 0.04015009, -0.04661144, 0.04473043,
                    0.04307429, 0.04824791, 0.04444466, -0.04589014, -0.04299864, -0.0419016,
                    0.04124931, -0.04471977, -0.04792661, 0.04381693, 0.04025216, 0.04353015,
                    0.0437477, 0.04521543, -0.04640334, 0.04482815, 0.04847183, 0.04392267,
                    0.03829962, -0.04427291, 0.04184552, -0.04929812, 0.04028318, 0.04502224,
                    -0.04489505, 0.0373099, 0.04026031, -0.04643809, 0.04039278, -0.04660065,
                    -0.04677462, 0.04625544, 0.04298817, -0.04585228, 0.03997608, -0.04405792,
                    -0.0461959, -0.04551096, -0.04164233, -0.04093911, -0.04510363, -0.04446927,
                    0.04068261, -0.04267003, 0.04041989, -0.04373494, 0.03917699, 0.04741545,
                    -0.04114515, -0.04367566, 0.03942266, 0.04204428, 0.04364268, 0.04785902,
                    0.03882711, -0.04546866, 0.04122745, -0.04453325, -0.04592539, -0.04373354,
                    -0.04299577, -0.04786372, 0.03987021, -0.04642559, 0.04322709, 0.04403246,
                    0.04838931, -0.04214095, 0.0435961, 0.04595806, -0.04385968, 0.04077932,
                    0.04559281, -0.04518094, -0.04472409, 0.04046113, -0.04127389, 0.0388936,
                    0.04555101, 0.04438771, 0.04510979, 0.04096895, 0.0444799, 0.04799319,
                    -0.0430204, -0.04462714, 0.04457061, 0.04105021, 0.04449255, 0.04315939,
                    -0.04585943, 0.04345465, -0.04207546, 0.04495849, 0.04419361, 0.04424025,
                    0.04465417, 0.04054961, -0.0448564, -0.04294284, -0.04475233, 0.04588363,
                    -0.04442916, -0.04177762, 0.04002212, 0.0438602, -0.04424671, -0.0429175,
                    0.04529247, -0.04586658, 0.04043199, 0.04312774, 0.04509728, 0.04055246,
                    0.04817222, 0.0435755, 0.04519051, -0.03916046, -0.04477419, -0.04263949,
                    -0.04934871, -0.04556104, -0.04450991, -0.04708428, -0.04036424, 0.04692967,
                    0.03845374, 0.04392675, 0.04586199, 0.04450425, -0.04517023, 0.03807968,
                    0.03815164, -0.04514144, -0.04485187, -0.04267498, 0.04361185, -0.04921734,
                    -0.03748607, 0.04518602, -0.04800837, 0.04184259, 0.04025743, 0.04311403,
                    0.04278437, 0.0414643, -0.04703187, 0.04210835, -0.04818443, -0.0474534,
                    0.04425834, -0.04155978, -0.04714488, -0.04265407, 0.04179464, -0.04633741,
                    -0.04753365, -0.04153654, -0.04312729, -0.0465273, -0.04588012, 0.0384097,
                    0.04157324, -0.04315975, -0.04413843, -0.04386725, -0.0436327, -0.04977291,
                    0.04701187, 0.04656826, 0.04750489, -0.04492132, 0.04367238, 0.04831877,
                    0.04239423, 0.03788293, -0.04452338, 0.0439451, -0.04866663, -0.04082462,
                    0.04130058, 0.0413114, 0.0416329, -0.04327046, 0.04681042, -0.04407482,
                    0.04356972, -0.04591145, -0.0477132, 0.04374393, -0.0445087, 0.04774468,
                    0.04623584, 0.03743158, -0.04492844, -0.04463143, -0.04481933, 0.04141298,
                    0.04352952, -0.04967547, 0.04224574, 0.04026164, 0.03759279, 0.04371729,
                    -0.04596011, 0.04391897, -0.04375268, 0.04779098, -0.04508404, 0.04232962,
                    -0.04479869, -0.04413074, -0.04739625, 0.03962434, -0.0462622, 0.04221592,
                    0.04490636, 0.04366314, 0.04133369, -0.04679637, -0.04433874, 0.04206097,
                    -0.04031486, 0.03873746, -0.04549551, 0.04507609, -0.04528403, -0.04821177,
                    -0.04183266, -0.04676722, 0.04356282, -0.04737033, 0.04581982, -0.04108503,
                    -0.04883461, 0.03745103, -0.04721932, 0.04373967, -0.04549085, 0.0413659,
                    0.0442023, -0.05067382, 0.04396223, 0.03999207, 0.03990594, -0.04828862,
                    0.041421, -0.04097815, 0.04256382, -0.04495353, 0.0431824, -0.04199211,
                    0.03786221, -0.04625617, -0.04411332, -0.04409782, 0.04507696, 0.0482068,
                    0.0407987, -0.04591984, 0.0448635, -0.04400348, 0.04090291, -0.04473701,
                    0.04289783, -0.0422567, -0.04437323, 0.04499108, 0.0447057, 0.04492801,
                    -0.05001102, -0.04452299, -0.04697845, -0.04673884, 0.0478403, -0.04619109,
                    0.04243409, -0.0469927, -0.04601869, -0.04609914, 0.04215341, -0.05096313,
                    -0.04582897, 0.04205693, -0.04101437, -0.04340144, 0.04506962, 0.04328651,
                    -0.04116879, 0.04366479, 0.04024811, -0.04742544, 0.04611587, 0.04378526,
                    0.04299634, 0.04374255, -0.04576352, -0.04684732, 0.04467578, -0.04121953,
                    0.04640976, 0.04653851, -0.04261921, -0.04750355, 0.0402003, 0.0397541,
                    -0.04889283, 0.04196887, 0.04267812, -0.04533067, 0.04366903, -0.04533453,
                    -0.04581934, 0.04059181, 0.04405687, -0.04531466, 0.04029796, 0.04340657,
                    -0.04676198, -0.04333705, -0.04527321, -0.04324145, -0.04548093, -0.04604024,
                    0.04166004, 0.0429338, -0.04515322, 0.04357721, 0.04413637, -0.04697264,
                    -0.04705004, 0.04064848, 0.04449074, 0.04644563, 0.04057986, 0.04144675,
                    0.04685403, 0.04315994, 0.04381246, 0.04354781, -0.0421458, -0.04652609,
                    0.04321802, -0.04609112, -0.04115133, 0.04230978, 0.03672514, 0.0453049,
                    0.03952432, -0.04842854, 0.0446003, -0.04328979, 0.04155049, 0.04500266,
                    -0.0424394, -0.04795505, -0.04807831, 0.04413975, 0.04457435, -0.0451168,
                    0.05012129, -0.0475133, 0.04033918, -0.04408691, 0.04788733, -0.04651976,
                    -0.04829163, 0.04106594, -0.04046036, 0.03863179, -0.04089505, 0.04348623,
                    0.04459494, 0.04447874, -0.04430779, -0.04436798, -0.04538095, 0.04231393,
                    0.04603336, -0.04547649, 0.04532253, 0.04080027, 0.04288544, -0.04714978,
                    0.04227969, -0.04582293, -0.04900076, 0.04844627, 0.03832033, 0.04273737,
                    0.04627426, 0.03863288, 0.04225462, -0.04633646, -0.04491195, 0.04043937,
                    0.04314643, 0.03904142, 0.04799259, -0.04597965, 0.03785567, -0.04353124,
                    0.04342642, -0.04819304, -0.0468284, 0.04180833, 0.04306053, 0.04515668,
                    0.0412929, 0.04531517
                )
                val embeddingSakthi = doubleArrayOf(
                    -0.03892426, 0.04680273, 0.03644022, -0.03227749, 0.03259758, -0.05035943,
                    0.04269271, -0.05386303, -0.0533204, 0.02951881, -0.04492916, -0.05243194,
                    -0.03976002, 0.03996904, -0.04028908, -0.04995857, -0.04814384, -0.05400523,
                    0.0413968, 0.04903652, -0.05056298, -0.04164273, -0.04813888, 0.03873574,
                    -0.05088453, 0.03917481, 0.05167469, -0.05189816, -0.04493109, 0.03802136,
                    0.05474642, 0.06063219, -0.04563488, 0.04758603, 0.02913098, 0.03000574,
                    -0.04909357, 0.05399309, -0.05006624, 0.03583291, 0.04211023, -0.04250453,
                    -0.04771455, 0.04563838, -0.04858607, -0.04991062, 0.03801835, -0.05515934,
                    -0.04606028, -0.05022985, -0.05272157, -0.04523329, 0.03997609, -0.04440575,
                    0.03310468, 0.03581896, 0.04555332, 0.04152627, -0.04821684, 0.04838361,
                    0.03994147, 0.04348801, -0.04290454, 0.04883184, -0.04069668, 0.04339925,
                    -0.04040694, 0.03895803, -0.0456062, -0.04702863, 0.04461085, 0.04408425,
                    -0.04582326, 0.0438569, -0.04864527, 0.04136969, 0.04032335, 0.04049524,
                    0.04469946, -0.04759336, 0.04455841, -0.04230024, 0.04490609, -0.04683086,
                    0.04900208, -0.04662655, -0.0441185, 0.03936496, 0.04281597, -0.04565387,
                    0.04283123, -0.0476771, 0.04064471, 0.03921285, 0.0427748, -0.04521841,
                    0.04551865, -0.04626438, 0.03911705, 0.04008981, -0.0466046, 0.04472663,
                    0.04306882, 0.04827448, 0.04441739, -0.04587485, -0.04298927, -0.04188986,
                    0.04118348, -0.04472094, -0.04796521, 0.04382681, 0.04021912, 0.0435022,
                    0.04369243, 0.04523814, -0.0464236, 0.04485987, 0.04854658, 0.04392758,
                    0.03821117, -0.04427675, 0.04182295, -0.04936817, 0.040228, 0.04504395,
                    -0.04488399, 0.03721001, 0.0402052, -0.04647321, 0.04035127, -0.04656895,
                    -0.0468001, 0.04625231, 0.04299602, -0.04585455, 0.03991833, -0.04407495,
                    -0.04622525, -0.04553906, -0.04164329, -0.04087052, -0.04514218, -0.04449239,
                    0.04064912, -0.04261416, 0.04037423, -0.04372926, 0.03908648, 0.04745068,
                    -0.04110581, -0.04369144, 0.03934608, 0.04198592, 0.04364261, 0.04792628,
                    0.03874117, -0.04546883, 0.04120323, -0.04453807, -0.04593714, -0.04373128,
                    -0.04296994, -0.04789484, 0.0398032, -0.04643868, 0.04321136, 0.04400887,
                    0.04838526, -0.04210901, 0.04359748, 0.04597907, -0.04383661, 0.04074368,
                    0.04556883, -0.04519784, -0.04471976, 0.04039289, -0.04125972, 0.03879336,
                    0.04559085, 0.04436983, 0.045121, 0.04093775, 0.04444981, 0.04801834,
                    -0.04300682, -0.04460148, 0.04458402, 0.04100531, 0.04449283, 0.04315008,
                    -0.04586777, 0.04347293, -0.04204619, 0.0449597, 0.0441691, 0.04425129,
                    0.04461475, 0.04048838, -0.04488011, -0.0429333, -0.04471991, 0.04589266,
                    -0.04443359, -0.04175432, 0.0399751, 0.043841, -0.0442431, -0.04290696,
                    0.04532372, -0.04586213, 0.04039347, 0.04312483, 0.04510958, 0.04047261,
                    0.04820197, 0.04353555, 0.04519308, -0.03909853, -0.0447418, -0.0425997,
                    -0.04942245, -0.04555672, -0.04450594, -0.0471054, -0.04028882, 0.04690573,
                    0.03835887, 0.04392011, 0.045874, 0.0445266, -0.04519486, 0.0380112,
                    0.03807164, -0.04514012, -0.04484188, -0.04266899, 0.04358654, -0.04926803,
                    -0.03742303, 0.04519619, -0.04807132, 0.04180656, 0.04017489, 0.04309224,
                    0.0427818, 0.04142142, -0.04705868, 0.04206615, -0.04826081, -0.04754378,
                    0.04424618, -0.04153696, -0.04720202, -0.04264617, 0.04177746, -0.04637213,
                    -0.047567, -0.0415154, -0.04309711, -0.04654656, -0.04585065, 0.03834967,
                    0.04152957, -0.04312965, -0.04414958, -0.04386677, -0.04360779, -0.04983073,
                    0.04705838, 0.04658632, 0.04753541, -0.04492741, 0.04364559, 0.04833882,
                    0.04238288, 0.03780982, -0.0445253, 0.04391453, -0.04871117, -0.04078646,
                    0.04126935, 0.04127587, 0.04158245, -0.04324513, 0.04685279, -0.04410241,
                    0.04355164, -0.04596142, -0.04776508, 0.04372301, -0.04451876, 0.04775311,
                    0.04622037, 0.03733572, -0.04493048, -0.04465479, -0.0447995, 0.04138885,
                    0.04349271, -0.04974844, 0.04220692, 0.0402043, 0.0375284, 0.04371851,
                    -0.04597057, 0.04392698, -0.04374108, 0.04783009, -0.04511099, 0.04232306,
                    -0.04479211, -0.04413271, -0.04744025, 0.03955491, -0.04629358, 0.04218663,
                    0.0449014, 0.04365354, 0.04129749, -0.04681351, -0.04434357, 0.04200288,
                    -0.04025535, 0.03865845, -0.04548269, 0.04506056, -0.0452773, -0.04823961,
                    -0.04182064, -0.04678383, 0.04353002, -0.04740255, 0.04576895, -0.04104776,
                    -0.04890655, 0.03734474, -0.04725326, 0.04372388, -0.04551219, 0.04132323,
                    0.04419253, -0.05072473, 0.04395582, 0.03993963, 0.03985481, -0.04831953,
                    0.04137338, -0.04097912, 0.04251115, -0.04495844, 0.04315945, -0.04199242,
                    0.0377858, -0.04628078, -0.04409963, -0.04409117, 0.04504477, 0.04822605,
                    0.04075518, -0.04592187, 0.04482199, -0.04398304, 0.04087799, -0.04472834,
                    0.04286219, -0.04221953, -0.04437367, 0.04501752, 0.04470147, 0.04493967,
                    -0.05006033, -0.04451367, -0.04702266, -0.04679111, 0.04786585, -0.04624026,
                    0.04239952, -0.04702731, -0.04602787, -0.0461374, 0.0421376, -0.0510691,
                    -0.0458618, 0.04203397, -0.04095398, -0.04339288, 0.045051, 0.04325531,
                    -0.04114697, 0.04366884, 0.04021206, -0.04745644, 0.04615471, 0.04377811,
                    0.04296299, 0.04375355, -0.04574462, -0.04687326, 0.04466737, -0.04117604,
                    0.04639829, 0.04656947, -0.04258761, -0.04754642, 0.0401284, 0.039705,
                    -0.04893656, 0.0419306, 0.04260645, -0.04534582, 0.04364191, -0.04532152,
                    -0.04583901, 0.04055157, 0.04405113, -0.0453426, 0.04027671, 0.0433729,
                    -0.0468014, -0.04333233, -0.04525765, -0.04322957, -0.04547184, -0.04606791,
                    0.04161096, 0.04292509, -0.04517415, 0.04357599, 0.04411057, -0.04703977,
                    -0.04708285, 0.04060451, 0.04451735, 0.04644709, 0.04053927, 0.04139788,
                    0.04688717, 0.04311191, 0.04377023, 0.04348903, -0.04209334, -0.04655372,
                    0.04321688, -0.04607395, -0.04108914, 0.04229547, 0.03661915, 0.04531889,
                    0.03945345, -0.04844505, 0.04459756, -0.0433, 0.04150164, 0.04500798,
                    -0.04242171, -0.04799567, -0.04811547, 0.04415701, 0.04458251, -0.04510176,
                    0.05014189, -0.04756718, 0.04030593, -0.04407803, 0.0479233, -0.04654729,
                    -0.04833584, 0.04101495, -0.04041415, 0.03854479, -0.04086455, 0.04350457,
                    0.04459389, 0.04446726, -0.04430053, -0.04437155, -0.04538275, 0.04230275,
                    0.04606809, -0.04550375, 0.04532236, 0.04076083, 0.04284921, -0.04714295,
                    0.0422534, -0.04585279, -0.04905726, 0.04851765, 0.03824331, 0.04271583,
                    0.04630783, 0.03854586, 0.04222446, -0.04636123, -0.04488574, 0.04037526,
                    0.04313261, 0.03898078, 0.04800761, -0.04601377, 0.03780481, -0.04351754,
                    0.04343077, -0.04820172, -0.04686265, 0.04178379, 0.04304291, 0.04517012,
                    0.04123802, 0.04530805
                )
                val distanceAncilSakthi = templateMatching.euclideanDistanceFromDouble(embeddingAncil, embeddingSakthi)
                constants.log("====>Value=>$distanceAncilSakthi")


                constants.log("embeddings1 => $embeddings1\nembeddings2 => $embeddings2\nembeddings3 => $embeddings3")
                if (embeddings1 != null && embeddings2 != null && embeddings3 != null) {
                    val distance1 = templateMatching.euclideanDistance(embeddings1, embeddings2)
                    val distance2 = templateMatching.euclideanDistance(embeddings1, embeddings3)
                    binding?.apply {
                        resultsTv.visibility = View.VISIBLE

                        resultsTv.append("Distance b/w SQ1001-03 & SQ1001-08: $distance1\n")
                        resultsTv.append("Distance b/w SQ1001-03 & SQ1002-03: $distance2\n")
                    }
                }
            }
        }
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

//            clickImage()
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
            pythonHelper.generateEmbeddings(
                this@MainActivity, folderPaths
            )
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