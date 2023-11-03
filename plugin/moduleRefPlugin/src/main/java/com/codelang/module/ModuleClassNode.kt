package com.codelang.module

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode


class ModuleClassNode() : ClassNode(Opcodes.ASM9) {
    override fun visitEnd() {
        println("className = $name $superName $interfaces")

        methods.forEach {
            println("methodNode:name=${it.name} desc=${it.desc}")
            it.instructions.filterIsInstance(MethodInsnNode::class.java)
                .forEach {
                    println("MethodInsnNode owner=" + it.owner + " name=" + it.name + " value=")

                }
            it.instructions.filterIsInstance(FieldInsnNode::class.java)
                .forEach {
                    println("FieldInsnNode owner=" + it.owner + " name=" + it.name + " desc=" + it.desc + " opcode=" + it.opcode)
                }
        }
    }
}
