package com.codelang.module

object Constants {

    const val ANDROID_DEP = "android.jar"

    val blackList = arrayListOf<String>().apply {
        add(ANDROID_DEP)
    }
}