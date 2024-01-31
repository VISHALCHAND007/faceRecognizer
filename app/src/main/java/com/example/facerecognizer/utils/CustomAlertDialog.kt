package com.example.facerecognizer.utils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import com.example.facerecognizer.R
import com.example.facerecognizer.utils.helperClasses.Constants

@SuppressLint("InflateParams", "MissingInflatedId")
inline fun Context.customEmpIdDialog(
    noinline positive: (String, Dialog) -> Unit,
    noinline negative: () -> Unit
) {
    val dialog = Dialog(this, R.style.CustomDialog)
    val view = LayoutInflater.from(this).inflate(R.layout.employee_id_dialog, null)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(view)
    dialog.setCancelable(false)

    // Set margins to the CardView
    val cardView = view.findViewById<CardView>(R.id.cardView) // Replace with the actual ID
    val cardViewLayoutParams = cardView.layoutParams as ViewGroup.MarginLayoutParams
//    val marginInPixels = resources.getDimensionPixelSize(R.dimen.dim_20) // Replace with the desired margin size
//    cardViewLayoutParams.setMargins(marginInPixels, marginInPixels, marginInPixels, marginInPixels)
    cardView.layoutParams = cardViewLayoutParams

    // Set layout parameters
    val layoutParams = WindowManager.LayoutParams()
    layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
    layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT

// Set the position to the top of the screen
    layoutParams.gravity = Gravity.TOP
    layoutParams.y = getToolbarHeight() + 10

    dialog.window?.attributes = layoutParams
    initEmpIdDialog(dialog, positive, negative)
    dialog.show()
}
// Function to get the height of the toolbar
fun Context.getToolbarHeight(): Int {
    val styledAttributes = theme.obtainStyledAttributes(
        intArrayOf(android.R.attr.actionBarSize)
    )
    val toolbarHeight = styledAttributes.getDimension(0, 0f).toInt()
    styledAttributes.recycle()
    return toolbarHeight
}
fun initEmpIdDialog(dialog: Dialog, positive: (String, Dialog) -> Unit, negative: () -> Unit) {
    val empIdEt = dialog.findViewById<EditText>(R.id.employeeIdEt)
    val cancelBtn = dialog.findViewById<AppCompatButton>(R.id.cancelBtn)
    val confirmBtn = dialog.findViewById<AppCompatButton>(R.id.confirmBtn)
    val deviceNameTv = dialog.findViewById<TextView>(R.id.deviceNameTv)

    deviceNameTv.text = Constants.DEVICE_NAME
    confirmBtn.setOnClickListener {
//        clueTv.visibility = View.VISIBLE

        val empId = empIdEt.text.toString()
        if (empId.isNotEmpty()) {
            positive.invoke(empId, dialog)


//            val result = Validator.validateEmployeeId(empId)
//            val result = Validator.validateEmployeeId(empId)
//            if (empId.length > 0) {
//                clueTv.visibility = View.GONE
//                positive.invoke(empId, dialog)
//            } else {
//                clueTv.visibility = View.VISIBLE
//                empIdEt.error = "Invalid Value"
//            }
        } else {
            empIdEt.error = "Invalid Value"
        }

    }
    cancelBtn.setOnClickListener {
        dialog.dismiss()
        negative.invoke()
    }
}
