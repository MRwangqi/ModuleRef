package com.codelang.module.bean

import org.objectweb.asm.ClassReader
import java.io.File

data class ModuleData(val dep:String, val classJar:File,val classReaders:List<ClassReader>)