package com.example.facerecognizer.utils

import android.content.Context
import com.example.facerecognizer.utils.helperClasses.Constants

class SharedPrefs(context: Context) {
    private val pref = context.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)

    fun saveString(key: String, value: String) {
        val editor = pref.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getString(key: String): String? {
        return pref.getString(key, null)
    }

    fun removeString(key: String) {
        val editor = pref.edit()
        editor.remove(key)
        editor.apply()
    }

    fun clearToken() {
        val editor = pref.edit()
        editor.remove(Constants.TOKEN)
        editor.apply()
    }

    fun saveBoolean(key: String, value: Boolean) {
        val editor = pref.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getBoolean(key: String): Boolean {
        return pref.getBoolean(key, false)
    }
}