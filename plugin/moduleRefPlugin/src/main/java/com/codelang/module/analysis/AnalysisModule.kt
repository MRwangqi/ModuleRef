package com.codelang.module.analysis

import com.codelang.module.Constants
import com.codelang.module.bean.AnalysisData
import com.codelang.module.bean.Clazz
import com.codelang.module.bean.Collect
import com.codelang.module.bean.UnsolvedData
import com.codelang.module.bean.XmlModuleData
import com.codelang.module.extension.ModuleRefExtension
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.regex.Pattern

object AnalysisModule {

    /**
     * key:dep
     * value:AnalysisData
     */
    private val analysisMap = hashMapOf<String, AnalysisData>()

    private var moduleRefExtension: ModuleRefExtension? = null
    fun analysis(
        collect: Collect,
        xmlCollectList: List<XmlModuleData>,
        moduleRefExtension: ModuleRefExtension
    ): Map<String, AnalysisData> {
        this.moduleRefExtension = moduleRefExtension

        // 将 list 转成 map,方便后续查找 class
        val clazzMap = hashMapOf<String, Clazz>()
        collect.nList.forEach {
            clazzMap[it.className!!] = it
        }
        collect.aList.forEach {
            if (clazzMap.contains(it.className)) {
                // 检查重复 class
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

        // 分析 xml 引用
        xmlCollectList.forEach { data ->
            val dep = data.dep
            data.classes.forEach { clazz ->
                if (clazzMap.contains(clazz)) {
                    depRefRecord(clazzMap[clazz]!!, dep)
                } else {
                    unsolvedClazzRecord(dep, clazz)
                }
            }
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
                depRefRecord(clazz, clazzMap[clazz.superName]!!.moduleData!!.dep)
            } else {
                unsolvedClazzRecord(clazz.moduleData!!.dep, clazz.superName!!)
            }
        }


        // 2、接口检查
        clazz.interfaces?.forEach {
            if (clazzMap.contains(it)) {
                // 记录当前类引用与接口的关系
                depRefRecord(clazz, clazzMap[it]!!.moduleData!!.dep)
            } else {
                unsolvedClazzRecord(clazz.moduleData!!.dep, it)
            }
        }

        // 3、注解检查
        clazz.visibleAnnotations?.forEach {
            val clzName = getClassName(it.desc)
            if (clzName != null) {
                if (clazzMap.contains(clzName)) {
                    // 记录当前类引用与注解的关系
                    depRefRecord(clazz, clazzMap[clzName]!!.moduleData!!.dep)
                } else {
                    unsolvedClazzRecord(clazz.moduleData!!.dep, clzName)
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
                    depRefRecord(clazz, clazzMap[clzName]!!.moduleData!!.dep)
                } else {
                    // 没找到该字段
                    unsolvedFieldRecord(clazz, clzName)
                }
            }
        }
    }

    private fun analysisMethod(clazz: Clazz, clazzMap: Map<String, Clazz>) {
        // 检查方法是否有引用外部模块情况
        clazz.methods?.forEach {
            it.instructions
                .filterIsInstance(MethodInsnNode::class.java)
                .forEach Continue@{ node ->
                    val ownerName = getClassName(node.owner)
                    if (ownerName == null) {
                        return@Continue
                    }
                    if (clazzMap.contains(ownerName)) {
                        var isFound = false
                        var clzName = ownerName
                        while (clzName != null) {
                            val clz = clazzMap[clzName] // 可能为 null，因为会存在父类也找不到的情况
                            if (clz == null) {
                                unsolvedClazzRecord(clazz.moduleData!!.dep, clzName)
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
                            depRefRecord(clazz, clazzMap[clzName]!!.moduleData!!.dep)
                        } else {
                            unsolvedMethodRecord(
                                clazz,
                                "${clazz.className}_${ownerName}.${node.name}(${node.desc})"
                            )
                        }
                    } else {
                        unsolvedClazzRecord(clazz.moduleData!!.dep, ownerName)
                    }
                }
            it.instructions
                .filterIsInstance(FieldInsnNode::class.java)
                .forEach Continue@{ node ->
                    val ownerName = getClassName(node.owner)
                    if (ownerName == null) {
                        return@Continue
                    }
                    if (clazzMap.contains(ownerName)) {
                        var isFound = false
                        var clzName = ownerName
                        while (clzName != null) {
                            val clz = clazzMap[clzName]  // 可能为 null，因为会存在父类也不存在的情况
                            if (clz == null) {
                                unsolvedClazzRecord(clazz.moduleData!!.dep, clzName ?: "")
                            }
                            //  遍历 clz 的 method 是否能匹配上
                            val f =
                                clz?.fields?.firstOrNull { it.name == node.name && it.desc == node.desc }
                            if (f == null) {
                                // 如果找不到，尝试从父类上面找，直到父类也找不到该方法
                                clzName = clz?.superName
                            } else {
                                isFound = true
                                break
                            }
                        }
                        if (isFound) {
                            // 记录当前类引用与方法的关系 clzName 与 clazz 的关系
                            depRefRecord(clazz, clazzMap[clzName]!!.moduleData!!.dep)
                        } else {
                            unsolvedMethodRecord(
                                clazz,
                                "${clazz.className}_${ownerName}.${node.name}(${node.desc})"
                            )
                        }
                    } else {
                        unsolvedClazzRecord(clazz.moduleData!!.dep, ownerName)
                    }
                }
        }
    }

    private fun depRefRecord(clazz: Clazz, refDep: String) {
        // 处于黑名单的依赖不记录
        if (Constants.blackList.contains(refDep)) {
            return
        }
        // dep 节点不在白名单里面的话，不记录
        val findDep = moduleRefExtension?.entryModule?.find {
            Pattern.compile(it).matcher(clazz.moduleData!!.dep).matches()
        }
        if (findDep != null) {
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

    private fun unsolvedClazzRecord(dep: String, clazzError: String) {
        // dep 节点不在白名单里面的话，不记录
        val findDep = moduleRefExtension?.entryModule?.find {
            Pattern.compile(it).matcher(dep).matches()
        }
        if (findDep != null) {
            return
        }

        // 忽略的类不记录
        val findClazz = moduleRefExtension?.ignoreClazz?.find {
            Pattern.compile(it).matcher(clazzError.replace("/", ".")).matches()
        }
        if (findClazz != null) {
            return
        }

        var analysisData = analysisMap[dep]
        if (analysisData == null) {
            analysisData = AnalysisData()
            analysisMap[dep] = analysisData
        }

        if (analysisData.unsolved == null) {
            analysisData.unsolved = UnsolvedData()
        }
        if (analysisData.unsolved!!.clazz.contains(clazzError)) {
            return
        }
        analysisData.unsolved!!.clazz.add(clazzError.replace("/", "."))
    }

    private fun unsolvedFieldRecord(clazz: Clazz, filedError: String) {
        // dep 节点不在白名单里面的话，不记录
        val findDep = moduleRefExtension?.entryModule?.find {
            Pattern.compile(it).matcher(clazz.moduleData?.dep!!).matches()
        }
        if (findDep != null) {
            return
        }

        // 忽略的类不记录
        val findClazz = moduleRefExtension?.ignoreClazz?.find {
            Pattern.compile(it).matcher(filedError.replace("/", ".")).matches()
        }
        if (findClazz != null) {
            return
        }

        var analysisData = analysisMap[clazz.moduleData?.dep]
        if (analysisData == null) {
            analysisData = AnalysisData()
            analysisMap[clazz.moduleData?.dep!!] = analysisData
        }
        if (analysisData.unsolved == null) {
            analysisData.unsolved = UnsolvedData()
        }
        if (analysisData.unsolved!!.fields.contains(filedError)) {
            return
        }

        analysisData.unsolved!!.fields.add(filedError.replace("/", "."))
    }

    private fun unsolvedMethodRecord(clazz: Clazz, methodError: String) {
        // dep 节点不在白名单里面的话，不记录
        val findDep = moduleRefExtension?.entryModule?.find {
            Pattern.compile(it).matcher(clazz.moduleData?.dep!!).matches()
        }
        if (findDep != null) {
            return
        }

        // 忽略的类不记录
        val findClazz = moduleRefExtension?.ignoreClazz?.find {
            Pattern.compile(it).matcher(methodError.replace("/", ".")).matches()
        }
        if (findClazz != null) {
            return
        }

        var analysisData = analysisMap[clazz.moduleData?.dep]
        if (analysisData == null) {
            analysisData = AnalysisData()
            analysisMap[clazz.moduleData?.dep!!] = analysisData
        }
        if (analysisData.unsolved == null) {
            analysisData.unsolved = UnsolvedData()
        }
        if (analysisData.unsolved!!.methods.contains(methodError)) {
            return
        }
        analysisData.unsolved!!.methods.add(methodError.replace("/", "."))
    }

    private fun getClassName(desc: String): String? {
        var clazzName = desc
        // [java/util/ArrayList;  数组对象，也有可能是 [[java/util/ArrayList;
        if (clazzName.startsWith("[")) {
            clazzName = clazzName.substring(clazzName.lastIndexOf("[") + 1, clazzName.length)
        }
        // Ljava/util/ArrayList;  对象
        if (clazzName.startsWith("L")) {
            return clazzName.substring(1, clazzName.length - 1)
        }
        // 基础类型不关心，直接返回 null
        return null
    }
}