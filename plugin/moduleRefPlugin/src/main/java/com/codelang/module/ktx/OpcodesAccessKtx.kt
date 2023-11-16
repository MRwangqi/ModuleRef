package com.codelang.module.ktx

import org.objectweb.asm.Opcodes


fun Int.isPublic(): Boolean {
    return this and Opcodes.ACC_PUBLIC != 0
}

fun Int.isPrivate(): Boolean {
    return this and Opcodes.ACC_PRIVATE != 0
}

fun Int.isAbstract(): Boolean {
    return this and Opcodes.ACC_ABSTRACT != 0
}

fun Int.isInterface(): Boolean {
    return this and Opcodes.ACC_INTERFACE != 0
}