package com.codelang.module

import com.codelang.module.analysis.AnalysisModule
import com.codelang.module.bean.AnalysisData
import com.codelang.module.collect.ClazzCollectModule
import com.codelang.module.collect.XmlCollectModule
import com.google.gson.Gson
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File


class ModuleRefPlugin : Plugin<Project> {

    companion object {
        const val TASK_NAME = "moduleRef"
        const val BUILD = "build"
    }


    override fun apply(project: Project) {
        // ./gradlew classAnalysis -Pbuild=debug
//        val params = project.gradle.startParameter.projectProperties
//
//        val build = if (params.containsKey(BUILD)) {
//            params[BUILD] ?: "debug"
//        } else {
//            // 默认 debug 兜底
//            "debug"
//        }


        project.afterEvaluate {
            // todo 暂时写死 debugRuntimeClasspath，后续需要根据 buildType 动态获取
            val configurationName = "debugRuntimeClasspath"
            project.tasks.create(TASK_NAME) {
                it.doLast {
                    // 收集依赖里的所有 class 文件
                    val collect = ClazzCollectModule.collectClazz(project, configurationName)
                    // 收集依赖里的所有 layout 文件
                    val xmlCollectList =
                        XmlCollectModule.collectDepLayoutModule(project, configurationName)
                    // 分析 class 文件的引用情况
                    val analysisMap = AnalysisModule.analysis(collect,xmlCollectList)
                    // 生成文件
                    generatorFile(project, analysisMap)
                    // todo collect layout 中自定义 View 的引用分析
                }
            }
        }
    }


    private fun generatorFile(project: Project, analysisMap: Map<String, AnalysisData>) {
        // 生成文件
        val text = Gson().toJson(analysisMap)
        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }
        val outputFile = File(project.buildDir.absolutePath + File.separator + "moduleRef.json")
        outputFile.writeText(text)

        println("配置文件生成----> " + outputFile.absolutePath)
    }

}