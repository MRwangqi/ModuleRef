package com.codelang.module

import com.codelang.module.analysis.AnalysisModule
import com.codelang.module.bean.AnalysisData
import com.codelang.module.collect.ClazzCollectModule
import com.codelang.module.collect.XmlCollectModule
import com.codelang.module.extension.ModuleRefExtension
import com.codelang.module.file.ModuleRefFile
import com.google.gson.Gson
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File


class ModuleRefPlugin : Plugin<Project> {

    companion object {
        const val TASK_NAME = "moduleRef"
        const val BUILD = "build"
        const val EXT_NAME = "moduleRefConfig"
    }


    override fun apply(project: Project) {
        // ./gradlew moduleRef -Pbuild=debug
        val params = project.gradle.startParameter.projectProperties
        val build = if (params.containsKey(BUILD)) {
            params[BUILD] ?: "debug"
        } else {
            // 默认 debug 兜底
            "debug"
        }

        project.extensions.create(EXT_NAME, ModuleRefExtension::class.java)

        project.afterEvaluate {
            val configurationName = "${build}RuntimeClasspath"
            val moduleRefExtension = project.extensions.findByName(EXT_NAME) as ModuleRefExtension

            project.tasks.create(TASK_NAME) {
                it.doLast {
                    val resolvableDeps =
                        project.configurations.getByName(configurationName).incoming

                    // 收集依赖里的所有 class 文件
                    val collect = ClazzCollectModule.collectClazz(project, resolvableDeps)
                    // 收集依赖里的所有 layout 文件
                    val xmlCollectList =
                        XmlCollectModule.collectDepLayoutModule(project, resolvableDeps)
                    // 分析 class、xml 文件的引用情况
                    val analysis =
                        AnalysisModule.analysis(collect, xmlCollectList, moduleRefExtension)
                    // 生成文件
                    ModuleRefFile.generatorFile(project, analysis)
                }
            }
        }
    }
}