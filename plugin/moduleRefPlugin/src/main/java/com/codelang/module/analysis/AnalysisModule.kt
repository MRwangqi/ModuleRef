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
        analysisMap.clear()

        // 将 list 转成 map,方便后续查找 class
        val clazzMap = hashMapOf<String, Clazz>()
        collect.nList.forEach {
            clazzMap[it.className!!] = it
        }
        collect.aList.forEach {
            if (clazzMap.contains(it.className)) {
                // 检查重复 class
//                throw RuntimeException("Duplicate class ${it.className} :" + it.moduleData?.dep + " and " + clazzMap[it.className]?.moduleData?.dep)
                println(("Duplicate class ${it.className} :" + it.moduleData?.dep + " and " + clazzMap[it.className]?.moduleData?.dep))
            }else{
                clazzMap[it.className!!] = it
            }
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
                    unsolvedFieldRecord(
                        clazz,
                        clzName ?: "",
                        "${clazz.className}_${clzName}"
                    )
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
                    val ownerName = getClassName(node.owner) ?: return@Continue
                    // 检查 owner 是否存在
                    getMethodRefClazz(clazzMap, clazz, ownerName, node.name, node.desc, true)
                        ?.let { clz ->
                            depRefRecord(clazz, clazzMap[clz]!!.moduleData!!.dep)
                        }
                }
            it.instructions
                .filterIsInstance(FieldInsnNode::class.java)
                .forEach Continue@{ node ->
                    val ownerName = getClassName(node.owner)?:return@Continue
                    // 检查 owner 是否存在
                    getMethodRefClazz(clazzMap, clazz, ownerName, node.name, node.desc, false)
                        ?.let { clz ->
                            depRefRecord(clazz, clazzMap[clz]!!.moduleData!!.dep)
                        }
                }
        }
    }

    private fun getMethodRefClazz(
        clazzMap: Map<String, Clazz>,
        clazz: Clazz,
        ownerName: String,
        name: String?,
        desc: String?,
        isMethod: Boolean
    ): String? {

        val clz = clazzMap[ownerName]
        // 因为会存在类也不存在的情况
        if (clz == null) {
            unsolvedClazzRecord(clazz.moduleData!!.dep, ownerName)
            return null
        }

        // 检查当前类是否能匹配上
        val found:Any? = if (isMethod) {
            clz.methods?.firstOrNull { it.name == name && it.desc == desc }
        }else{
            clz.fields?.firstOrNull { it.name == name && it.desc == desc }
        }
        if (found != null) {
            // 找到的话，则直接返回
            return ownerName
        }

        // 遍历父类是否能找到
        val superList = arrayListOf<Clazz?>()
        dfsClazz(clazzMap, clz, superList)
        for (i in 0 until superList.size) {
            val clz = superList[i]
            val found:Any? = if (isMethod) {
                clz?.methods?.firstOrNull { it.name == name && it.desc == desc }
            }else{
                clz?.fields?.firstOrNull { it.name == name && it.desc == desc }
            }
            if (found != null) {
                // 找到的话，则直接返回
                return clz?.className
            }
        }

        // 遍历接口是否能找到
        val interfaceList = arrayListOf<Clazz?>()
        dfsInterface(clazzMap, clz, interfaceList)
        for (i in 0 until interfaceList.size) {
            val clz = interfaceList[i]
            val found:Any? = if (isMethod) {
                clz?.methods?.firstOrNull { it.name == name && it.desc == desc }
            }else{
                clz?.fields?.firstOrNull { it.name == name && it.desc == desc }
            }
            if (found != null) {
                // 找到的话，则直接返回
                return clz?.className
            }
        }

        // 找不到的话，记录 unsolved
        if (isMethod){
            unsolvedMethodRecord(
                clazz,
                clazz.className?:"",
                "${ownerName}.${name}(${desc})"
            )
        }else{
            unsolvedFieldRecord(
                clazz,
                clazz.className ?: "",
                "${ownerName}.${name}(${desc})"
            )
        }

        return null
    }

    private fun dfsInterface(clazzMap: Map<String, Clazz>, clazz: Clazz?, list: ArrayList<Clazz?>) {
        val interfaces = clazz?.interfaces
        if (!interfaces.isNullOrEmpty()) {
            interfaces.forEach {
                list.add(clazzMap[it])
                dfsInterface(clazzMap, clazzMap[it], list)
            }
        }
    }

    private fun dfsClazz(clazzMap: Map<String, Clazz>, clazz: Clazz?, list: ArrayList<Clazz?>) {
        val superName = clazz?.superName
        if (superName != null) {
            list.add(clazzMap[superName])
            dfsClazz(clazzMap, clazzMap[superName], list)
        }
    }

    private fun depRefRecord(clazz: Clazz, refDep: String) {
        // 处于内部黑名单的依赖不记录
        if (Constants.blackList.contains(refDep)) {
            return
        }

        if (!isMatchEntryModule(clazz.moduleData!!.dep)) {
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
        // 忽略的类不记录
        if (isIgnoreClass(clazzError)) {
            return
        }

        if (!isMatchEntryModule(dep)) {
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
        val error = clazzError.replace("/", ".")
        if (analysisData.unsolved!!.clazz.contains(error)) {
            return
        }
        analysisData.unsolved!!.clazz.add(error)
    }

    private fun unsolvedFieldRecord(clazz: Clazz, filedClazz: String, filedError: String) {
        if (isIgnoreClass(filedClazz)) {
            return
        }

        if (!isMatchEntryModule(clazz.moduleData!!.dep)) {
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

        val error = filedError.replace("/", ".")
        if (analysisData.unsolved!!.fields.contains(error)) {
            return
        }
        analysisData.unsolved!!.fields.add(error)
    }

    private fun unsolvedMethodRecord(clazz: Clazz, methodClazz: String, methodError: String) {
        // 忽略的类不记录
        if (isIgnoreClass(methodClazz)) {
            return
        }

        if (!isMatchEntryModule(clazz.moduleData!!.dep)) {
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

        val error = methodError.replace("/", ".")
        if (analysisData.unsolved!!.methods.contains(error)) {
            return
        }
        analysisData.unsolved!!.methods.add(error)
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

        if (clazzName.contains("/")) {
            return clazzName
        }
        // 基础类型不关心，直接返回 null
        return null
    }


    private fun isIgnoreClass(clazz: String): Boolean {
        val ignoreList = moduleRefExtension?.ignoreClazz
        if (ignoreList.isNullOrEmpty()) {
            return false
        }
        // 忽略的类不记录
        val findClazz = moduleRefExtension?.ignoreClazz?.find {
            Pattern.compile(it).matcher(clazz.replace("/", ".")).matches()
        }
        return findClazz != null
    }

    private fun isMatchEntryModule(dep: String): Boolean {
        val entryModule = moduleRefExtension?.entryModule
        if (entryModule.isNullOrEmpty()) {
            // 没有配置 entryModule 的话，则全部记录
            return true
        }
        val findDep = moduleRefExtension?.entryModule?.find {
            Pattern.compile(it).matcher(dep).matches()
        }
        return findDep != null
    }
}