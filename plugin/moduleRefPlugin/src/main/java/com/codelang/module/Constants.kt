package com.codelang.module

object Constants {

    const val ANDROID_DEP = "android.jar"
    const val JAVA_DEP = "rt.jar"

    val blackList = arrayListOf<String>().apply {
        add(ANDROID_DEP)
        add(JAVA_DEP)
    }
}