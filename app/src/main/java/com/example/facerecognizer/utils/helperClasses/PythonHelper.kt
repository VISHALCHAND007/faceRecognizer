package com.example.facerecognizer.utils.helperClasses

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class PythonHelper(private val constants: Constants) {
    private lateinit var py: Python
    private lateinit var module: PyObject

    private fun setUpPython(mContext: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(mContext))
            py = Python.getInstance()
            module = py.getModule("deepface_android_helper_functions")
        }
    }

    //meant for training the model
    fun generateEmbeddings(
        mContext: Context,
        personFolderPath: ArrayList<String>
    ) {
        setUpPython(mContext)
//        val isModelInitialized = module["facenet_model.model_loaded"]
//        val facenetModel = module["get_facenet_model"]
//        facenetModel?.call()
        val isModelInitialized = module["get_facenet_model.model_loaded"]
//        isModelInitialized?.call()
        CoroutineScope(Dispatchers.IO).launch {
            var returnedValue: Boolean = true
            async {
                returnedValue = isModelInitialized?.toBoolean() == true
            }.await()
            constants.log(returnedValue.toString())
        }
        if (false) {
            val result = module["generate_person_embeddings_pickle_file"]
            result?.call(personFolderPath)
        }
    }

    fun trainModelOnGeneratedEmbeddings(
        mContext: Context,
        personsPickleFilePaths: ArrayList<String>
    ) {
        setUpPython(mContext)
        val result = module["train_svm_classifier_on_person_embeddings"]
        result?.call(personsPickleFilePaths)
    }

    //to verify person face
    fun runInference(mContext: Context) {
        setUpPython(mContext)

    }
}

interface OnComplete {
    fun executionComplete()
}