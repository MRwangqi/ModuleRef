package com.codelang.module.bean

class AnalysisData {
    val dependencies = arrayListOf<String>()
    val unsolved = UnsolvedData()
}


class UnsolvedData {
    val clazz = arrayListOf<String>()
    val fields = arrayListOf<String>()
    val methods = arrayListOf<String>()
}