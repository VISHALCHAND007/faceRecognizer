package com.example.facerecognizer.utils.helperClasses

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

class Constants @Inject constructor() {
    private lateinit var bottomSheetDialog: BottomSheetDialog

    companion object {
        //changeable values
        const val IMAGE_MIN = 20
        const val IMAGE_MAX = 24
        const val SPAN_COUNT = 2
        const val LOW_BATTERY_PERCENTAGE = 20
        const val SYNC_TIMER = 20
        const val DB_SYNC_TIMER = (3 * 60 * 60 * 1000).toLong() //3 hours in milli-seconds
        const val TOTAL_PROFILE_PIC_COUNT = 4
        const val PUNCH_DIALOG_TIMER = 10000L
        const val EMPLOYEE_ID = "emp_id"
        const val INSUFFICIENT_IMG = "insufficient_images"
        const val RETAKE_IMG = "retake_images"
        const val IMG_COUNT = "image_count"

        val DEVICE_NAME: String = android.os.Build.MODEL
        val DEVICE_ID: String = android.os.Build.ID
        const val color = Color.GREEN
        const val PEEK_HEIGHT = 220
        const val BOTTOM_SHEET_DURATION = 3
        const val PREF_FILE_NAME = "KioskPrefs"
        const val DOUBLE_BACK_PRESSED_INTERVAL = 3000
        const val TOKEN = "token"

        //        const val VIDEO_URL = "https://www.youtube.com/embed/8eZln3Go0qo?si=Q7veyBryhrgdxp1I"
        const val TITLE = "title"
    }

    fun log(str: String) {
        Log.e("Check here==", str)
    }

    @SuppressLint("SuspiciousIndentation")
    fun changeScreen(mContext: Context, destination: Class<*>) {
        val intent = Intent(mContext, destination)
        mContext.startActivity(intent)
    }

    fun changeScreenWithTitle(mContext: Context, destination: Class<*>, title: String) {
        val intent = Intent(mContext, destination)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra(TITLE, title)
        mContext.startActivity(intent)
    }

    @SuppressLint("SimpleDateFormat")
    fun formatDate(date: Date): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
        return dateFormatter.format(date).toString()
    }

    @SuppressLint("SimpleDateFormat")
    fun formatFullDateNow(): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd-HH:mm")
        return dateFormatter.format(Calendar.getInstance().time)
    }

    @SuppressLint("SimpleDateFormat")
    fun formatFullDateShortNow(): String {
        val dateFormatter = SimpleDateFormat("dd-MM-yy-HH:mm")
        return dateFormatter.format(Calendar.getInstance().time)
    }

    fun showToast(str: String, mContext: Context) {
        Toast.makeText(mContext, str, Toast.LENGTH_SHORT).show()
    }
}