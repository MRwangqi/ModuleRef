package com.codelang.module

import com.codelang.module.bean.AnalysisData
import com.codelang.module.bean.Clazz
import com.codelang.module.bean.Collect
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

object AnalysisModule {

    /**
     * key:dep
     * value:AnalysisData
     */
    private val analysisMap = hashMapOf<String, AnalysisData>()

    fun analysis(collect: Collect): Map<String, AnalysisData> {
        // 将 list 转成 map,方便后续查找 class
        val clazzMap = hashMapOf<String, Clazz>()

        collect.nList.forEach {
            clazzMap[it.className!!] = it
        }
        collect.aList.forEach {
            if (clazzMap.contains(it.className)) {
                throw RuntimeException("Duplicate class ${it.className} :" + it.moduleData?.dep + " and " + clazzMap[it.className]?.moduleData?.dep)
            }
            clazzMap[it.className!!] = it
        }

        // 分析 class 引用
        collect.aList.forEach {
            analysisClazz(it, clazzMap)
            analysisField(it, clazzMap)
            analysisMethod(it, clazzMap)
        }
        return analysisMap
    }

    /**
     * clazz、super、interface
     */
    private fun analysisClazz(clazz: Clazz, clazzMap: Map<String, Clazz>) {
        // 1、父类检查
        if (clazz.superName != null) {
            if (clazzMap.contains(clazz.superName)) {
                // 记录当前类引用与父类的关系
                depRefRecord(clazz, clazzMap[clazz.superName]!!)
            } else {
                unsolvedClazzRecord(clazz, clazz.superName!!)
            }
        }


        // 2、接口检查
        clazz.interfaces?.forEach {
            if (clazzMap.contains(it)) {
                // 记录当前类引用与接口的关系
                depRefRecord(clazz, clazzMap[it]!!)
            } else {
                unsolvedClazzRecord(clazz, it)
            }
        }

        // 3、注解检查
        clazz.visibleAnnotations?.forEach {
            val clzName = getClassName(it.desc)
            if (clzName != null) {
                if (clazzMap.contains(clzName)) {
                    // 记录当前类引用与注解的关系
                    depRefRecord(clazz, clazzMap[clzName]!!)
                } else {
                    unsolvedClazzRecord(clazz, clzName)
                }
            }
        }
    }

    private fun analysisField(clazz: Clazz, clazzMap: Map<String, Clazz>) {
        // 检查字段是否有引用外部模块情况(基础类型需要忽略)
        clazz.fields?.forEach {
            val clzName = getClassName(it.desc)
            if (clzName != null) {
                if (clazzMap.contains(clzName)) {
                    // 记录当前类引用与注解的关系
                    depRefRecord(clazz, clazzMap[clzName]!!)
                } else {
                    // 没找到该字段
                    unsolvedFieldRecord(clazz, clzName + "." + it.name)
                }
            }
        }
    }

    private fun analysisMethod(clazz: Clazz, clazzMap: Map<String, Clazz>) {
        // 检查方法是否有引用外部模块情况
        clazz.methods?.forEach {
            it.instructions
                .filterIsInstance(MethodInsnNode::class.java)
                .forEach { node ->
                    if (clazzMap.contains(node.owner)) {
                        var isFound = false
                        var clzName = node.owner
                        while (clzName != null) {
                            val clz = clazzMap[clzName] // 可能为 null，因为会存在父类也找不到的情况
                            if (clz == null) {
                                unsolvedClazzRecord(clazz, clzName)
                            }
                            //  遍历 clz 的 method 是否能匹配上
                            val m =
                                clz?.methods?.firstOrNull { it.name == node.name && it.desc == node.desc }
                            if (m == null) {
                                // 找不到的话，尝试从父类上面找，直到父类也找不带该方法
                                clzName = clz?.superName
                            } else {
                                isFound = true
                                break
                            }
                        }

                        if (isFound) {
                            // 记录当前类引用与方法的关系 clzName 与 clazz 的关系
                            depRefRecord(clazz, clazzMap[clzName]!!)
                        } else {
                            unsolvedMethodRecord(
                                clazz,
                                "${clazz.className}_${node.owner}.${node.name}(${node.desc})"
                            )
                        }
                    } else {
                        unsolvedClazzRecord(clazz, node.owner)
                    }
                }
            it.instructions
                .filterIsInstance(FieldInsnNode::class.java)
                .forEach { node ->
                    if (clazzMap.contains(node.owner)) {
                        var isFound = false
                        var clzName = node.owner
                        while (clzName != null) {
                            val clz = clazzMap[clzName]  // 可能为 null，因为会存在父类也不存在的情况
                            if (clz == null) {
                                unsolvedClazzRecord(clazz, clzName)
                            }
                            //  遍历 clz 的 method 是否能匹配上
                            val f =
                                clz?.fields?.firstOrNull { it.name == node.name && it.desc == node.desc }
                            if (f == null) {
                                // 如果找不到，尝试从父类上面找，直到父类也找不带该方法
                                clzName = clz?.superName
                            } else {
                                isFound = true
                                break
                            }
                        }
                        if (isFound) {
                            // 记录当前类引用与方法的关系 clzName 与 clazz 的关系
                            depRefRecord(clazz, clazzMap[clzName]!!)
                        } else {
                            unsolvedMethodRecord(
                                clazz,
                                "${clazz.className}_${node.owner}.${node.name}(${node.desc})"
                            )
//                            println("FieldInsnNode unsolved class ${node.owner} method= " + node.name + " desc=" + node.desc)
                        }
                    } else {
                        unsolvedClazzRecord(clazz, node.owner)
                    }
                }
        }
    }


    private fun depRefRecord(clazz: Clazz, refClazz: Clazz) {
        val refDep = refClazz.moduleData?.dep!!

        // 处于黑名单的依赖不记录
        if (Constants.blackList.contains(refDep)) {
            return
        }

        // 不同依赖模块需要记录
        if (clazz.moduleData?.dep != refDep) {
            var analysisData = analysisMap.get(clazz.moduleData?.dep)
            if (analysisData == null) {
                analysisData = AnalysisData()
                analysisMap[clazz.moduleData?.dep!!] = analysisData
            }
            // 记录 dep 引用关系
            if (!analysisData.dependencies.contains(refDep)) {
                analysisData.dependencies.add(refDep)
            }
        }
    }

    private fun unsolvedClazzRecord(clazz: Clazz, clazzError: String) {
        var analysisData = analysisMap[clazz.moduleData?.dep]
        if (analysisData == null) {
            analysisData = AnalysisData()
            analysisMap[clazz.moduleData?.dep!!] = analysisData
        }

        if (analysisData.unsolved.clazz.contains(clazzError)) {
            return
        }
        analysisData.unsolved.clazz.add(clazzError)
    }

    private fun unsolvedFieldRecord(clazz: Clazz, filedError: String) {
        var analysisData = analysisMap[clazz.moduleData?.dep]
        if (analysisData == null) {
            analysisData = AnalysisData()
            analysisMap[clazz.moduleData?.dep!!] = analysisData
        }
        if (analysisData.unsolved.fields.contains(filedError)) {
            return
        }

        analysisData.unsolved.fields.add(filedError)
    }

    private fun unsolvedMethodRecord(clazz: Clazz, methodError: String) {
        var analysisData = analysisMap[clazz.moduleData?.dep]
        if (analysisData == null) {
            analysisData = AnalysisData()
            analysisMap[clazz.moduleData?.dep!!] = analysisData
        }
        if (analysisData.unsolved.methods.contains(methodError)) {
            return
        }
        analysisData.unsolved.methods.add(methodError)
    }

    private fun getClassName(desc: String): String? {
        // Ljava/util/ArrayList;
        if (desc.startsWith("L")) {
            return desc.substring(1, desc.length - 1)
        }
        // 基础类型不关心，直接返回 null
        return null
    }
}