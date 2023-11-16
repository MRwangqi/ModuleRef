package com.codelang.module.file

import com.codelang.module.bean.AnalysisData
import com.google.gson.Gson
import org.gradle.api.Project
import java.io.File

object ModuleRefFile {

    fun generatorFile(project: Project, analysis: Pair<Map<String, AnalysisData>,Map<String, ArrayList<String>>>) {
        val analysisMap = analysis.first
        generatorModuleRef(project, analysisMap)
        generatorPlantUML(project, analysisMap)
        generatorMermaid(project, analysisMap)
        val absMethodMap = analysis.second
        generatorAbsMethod(project, absMethodMap)
    }

    private fun generatorAbsMethod(project: Project, absMethodMap: Map<String, java.util.ArrayList<String>>) {
        // 生成文件
        val text = Gson().toJson(absMethodMap)
        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }
        val outputFile = File(project.buildDir.absolutePath + File.separator + "absMethod.json")
        outputFile.writeText(text)

        println("配置文件生成----> $outputFile")
    }


    private fun generatorModuleRef(project: Project, analysisMap: Map<String, AnalysisData>) {
        // 生成文件
        val text = Gson().toJson(analysisMap)
        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }
        val outputFile = File(project.buildDir.absolutePath + File.separator + "moduleRef.json")
        outputFile.writeText(text)

        println("配置文件生成----> $outputFile")
    }


    private fun generatorPlantUML(project: Project, analysisMap: Map<String, AnalysisData>) {
        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }
        val outputFile = File(project.buildDir.absolutePath + File.separator + "moduleRef.puml")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.appendText("@startuml\n")
        analysisMap.forEach {
            val dep = it.key
            it.value.dependencies.forEach {
                outputFile.appendText("(${dep}) --> (${it})\n")
            }
        }
        outputFile.appendText("@enduml\n")

        println("配置文件生成----> $outputFile")
    }


    private fun generatorMermaid(project: Project, analysisMap: Map<String, AnalysisData>) {
        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }
        val outputFile = File(project.buildDir.absolutePath + File.separator + "moduleRef.mmd")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.appendText("graph TD\n")
        analysisMap.forEach { it ->
            val dep = it.key
            it.value.dependencies.forEach {
                outputFile.appendText("  $dep --> ${it}\n")
            }
        }
        println("配置文件生成----> $outputFile")
    }
}