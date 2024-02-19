package com.example.facerecognizer.utils

import android.content.Context
import android.content.res.AssetManager
import com.example.facerecognizer.utils.helperClasses.Constants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.sqrt


class TemplateMatching {

    fun copyEmbFileToInternalStorage(context: Context, fileName: String?): String? {
        val internalFile = File(context.filesDir, fileName)
        try {
            val inputStream = context.assets.open(fileName!!)
            val outputStream = FileOutputStream(internalFile)
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            inputStream.close()
            outputStream.close()
            return internalFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun readEmbeddingsFromAssets(context: Context, fileName: String): FloatArray? {
        try {
            val assetManager: AssetManager = context.assets
            val inputStream = assetManager.open(fileName)
            val reader = inputStream.bufferedReader()

            val content = reader.readLine()
            val embeddings = content.split(" ").map { it.toFloatOrNull() }

            reader.close()

            if (embeddings.all { it != null }) {
                return embeddings.requireNoNulls().toFloatArray()
            } else {
                throw NumberFormatException("Invalid float values in the file.")
            }
        } catch (e: IOException) {
            Constants().log("====> $e")
        } catch (e: NumberFormatException) {
            Constants().log("====> $e")
        }
        return null
    }

    @Throws(IOException::class)
    fun readFile(file: File): ByteArray? {
        val f = RandomAccessFile(file, "r")
        return try {
            val longLength = f.length()
            val length = longLength.toInt()
            if (length.toLong() != longLength) {
                IOException("File size >= 2 GB")
            }
            val data = ByteArray(length)
            f.readFully(data)
            data
        } finally {
            f.close()
        }
    }

    // Function to calculate Euclidean distance
    fun euclideanDistance(bytes1: ByteArray, bytes2: ByteArray): Float {
        // Convert byte arrays to float arrays
        val floatArray1 = byteArrayToFloatArray(bytes1)
        val floatArray2 = byteArrayToFloatArray(bytes2)

        var sum = 0.0

        for (i in floatArray1.indices) {
            sum += Math.pow((floatArray1[i] - floatArray2[i]).toDouble(), 2.0)
        }

        return Math.sqrt(sum).toFloat()
    }

    fun euclideanDistanceFromDouble(vector1: DoubleArray, vector2: DoubleArray): Double {
        require(vector1.size == vector2.size) {
            "Vectors must have the same dimensionality"
        }

        var sum = 0.0
        for (i in vector1.indices) {
            sum += (vector1[i] - vector2[i]).pow(2)
        }

        return sqrt(sum)
    }

    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val floatBuffer = ByteBuffer.wrap(bytes).asFloatBuffer()
        val floatArray = FloatArray(floatBuffer.limit())
        floatBuffer.get(floatArray)
        return floatArray
    }


    private val dataset: Map<String, FloatBuffer> = HashMap()
//    fun match(emb: FloatBuffer): Pair {
//        var similarity = 99f
//        var label: String? = null
//        val it: Iterator<*> = dataset.entries.iterator()
//        val embArray = FloatArray(emb.limit())
//        emb[embArray]
//        while (it.hasNext()) {
//            val (key, value) = it.next() as Map.Entry<*, *>
//            val e = value as FloatArray
//            val norm = l2norm(e, embArray)
//            if (label == null) {
//                similarity = norm
//                label = key as String?
//            } else if (norm < similarity) {
//                similarity = norm
//                label = key as String
//            }
//            //            logger.d("match: " + pair.getKey() + " " + norm + " " + similarity);
//        }
//        similarity = round(similarity)
//        return Pair(label, similarity)
//    }

    private fun l2norm(e1: FloatArray, e2: FloatArray): Float {
        var sum = 0f
        for (i in e1.indices) {
            sum += Math.pow((e1[i] - e2[i]).toDouble(), 2.0).toFloat()
        }
        return sqrt(sum.toDouble()).toFloat()
    }
//    private fun round(num: Float): Float {
//        val decimal = BigDecimal(num.toDouble())
//        return decimal.setScale(2, BigDecimal.ROUND_HALF_DOWN).floatValue()
//    }
}