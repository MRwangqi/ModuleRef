package com.codelang.module.extension

open class ModuleRefExtension {
    /**
     * 设置需要分析的依赖，如果不设置则默认分析所有依赖，支持正则
     */
    var entryModule = arrayListOf<String>()

    /**
     * 设置忽略的类，支持正则，忽略的类不会出现在 unsolved 中
     */
    var ignoreClazz = arrayListOf<String>()

}

