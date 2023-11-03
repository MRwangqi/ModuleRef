package com.codelang.module

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.codelang.module.bean.ModuleData
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import java.util.jar.JarFile

class ModuleRefPlugin : Plugin<Project> {

    companion object {
        const val TASK_NAME = "moduleRefPlugin"
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

            val configurationName = "debugRuntimeClasspath"
            project.tasks.create(TASK_NAME) {
                it.doLast {
                    val list = arrayListOf<ModuleData>()
                    // 收集 class file
                    collectAndroidModule(project)?.also { list.add(it) }
                    list.addAll(collectDepModule(project, configurationName))

                    // todo test
//                    list.get(0).classReaders.get(2)
//                        .accept(ModuleClassNode(), ClassReader.SKIP_DEBUG)
                }
            }
        }
    }


    private fun collectDepModule(project: Project, configurationName: String): List<ModuleData> {
        val resolvableDeps =
            project.configurations.getByName(configurationName).incoming
        // 获取 dependencies class.jar
        return resolvableDeps.artifactView { conf ->
            conf.attributes { attr ->
                attr.attribute(
                    AndroidArtifacts.ARTIFACT_TYPE,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR.type
                )
            }
        }.artifacts.map { result ->
            val dep = result.variant.displayName.split(" ").find { it.contains(":") }
                ?: result.variant.displayName
            ModuleData(dep, result.file, unzipJar(result.file))
        }.toList()
    }

    private fun collectAndroidModule(project: Project): ModuleData? {
        // 获取 android.jar
        val android = project.extensions.findByName("android") as? AppExtension
            ?: throw RuntimeException("This is not android project")
        val sdk = android.compileSdkVersion
            ?: throw RuntimeException("compileSdkVersion not set")

        val propFile = File(project.rootProject.projectDir, "local.properties")
        val p = Properties()
        p.load(FileInputStream(propFile))
        var path = p["sdk.dir"]
        path = path ?: System.getenv("ANDROID_HOME")
        val androidJar = File(
            path.toString(),
            "platforms${File.separator}${sdk}${File.separator}android.jar"
        )
        if (androidJar.exists()) {
            return ModuleData("android", androidJar, unzipJar(androidJar))
        }
        return null
    }


    private fun unzipJar(file: File): List<ClassReader> {
        // 获取 jar 中的 class 文件
        val jarFile = JarFile(file, false, JarFile.OPEN_READ)
        val jarEntries = jarFile.entries()
        val list = arrayListOf<ClassReader>()
        while (jarEntries.hasMoreElements()) {
            val entry = jarEntries.nextElement()
            if (!entry.isDirectory && entry.name.endsWith(".class") && !entry.name.endsWith("module-info.class")) {
                var ins: InputStream? = null
                try {
                    ins = jarFile.getInputStream(entry)
                    list.add(ClassReader(ins))
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (ins != null) {
                        try {
                            ins.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return list
    }

}