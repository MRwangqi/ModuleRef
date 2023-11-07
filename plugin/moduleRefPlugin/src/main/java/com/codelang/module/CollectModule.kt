package com.codelang.module

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.codelang.module.bean.Clazz
import com.codelang.module.bean.Collect
import com.codelang.module.bean.ModuleData
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Properties
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import javax.xml.parsers.SAXParserFactory

object CollectModule {


    /**
     * 收集 clazz，aList 为需要参与指令分析的 clazz，nList 为不需要参与指令分析的 clazz
     * @return
     */
    fun collectClazz(project: Project, configurationName: String): Collect {
        val aList = getAnalysisClazz(project, configurationName)
        val nList = getNoneAnalysisClazz(project)
        return Collect(aList, nList)
    }

    /**
     * 获取需要参与指令解析的 clazz
     */
    private fun getAnalysisClazz(project: Project, configurationName: String): List<Clazz> {
        val jarData = collectDepJarModule(project, configurationName)
        val layoutData = collectDepLayoutModule(project,configurationName)

        val list = arrayListOf<ModuleData>()
        list.addAll(jarData)
        list.addAll(layoutData)
        return parseClazz(list)
    }

    /**
     * 获取不需要参与指令解析的 clazz，例如 android.jar
     */
    private fun getNoneAnalysisClazz(project: Project): List<Clazz> {
        val depList = arrayListOf<ModuleData>()
        // android.jar
        collectAndroidModule(project)?.also { depList.add(it) }
        // java rt.jar
        collectJavaModule(project).also {
            depList.add(ModuleData(Constants.JAVA_DEP, it))
        }

        return parseClazz(depList)
    }

    /**
     * 获取 clazz
     */
    private fun parseClazz(datas: List<ModuleData>): List<Clazz> {
        return datas.map {
            it.classReaders.map { classReader ->
                getClazz(it, classReader)
            }.toList()
        }.flatten().toList()
    }


    /**
     * 收集依赖 jar module
     */
    private fun collectDepJarModule(project: Project, configurationName: String): List<ModuleData> {
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
            ModuleData(dep, unzipJar(result.file))
        }.toList()
    }

    private fun collectDepLayoutModule(
        project: Project,
        configurationName: String
    ): List<ModuleData> {
        val resolvableDeps =
            project.configurations.getByName(configurationName).incoming
        // 获取 dependencies layout
        return resolvableDeps.artifactView { conf ->
            conf.attributes { attr ->
                attr.attribute(
                    AndroidArtifacts.ARTIFACT_TYPE,
                    AndroidArtifacts.ArtifactType.ANDROID_RES.type
                )
            }
        }.artifacts.map { result ->
            val dep = result.variant.displayName.split(" ").find { it.contains(":") }
                ?: result.variant.displayName
            val layout = result.file.absolutePath + File.separator + "layout"

            val file = File(layout)
            return if (file.exists() && file.isDirectory) {
                file.listFiles()?.filter { it.name.endsWith(".xml") }?.map { xml ->
                    val handler = LayoutXmlHandler()
                    SAXParserFactory.newInstance().newSAXParser().parse(xml, handler)
                    handler.views
                }
            } else {
                emptyList()
            }
        }.toList()
    }


    /**
     * 收集 Android module
     */
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
            return ModuleData(Constants.ANDROID_DEP, unzipJar(androidJar))
        }
        return null
    }

    private fun collectJavaModule(project: Project): List<ClassReader> {
        val list = arrayListOf<ClassReader>()
        val file = "/META-INF/jdk8_rt.jar"
        val fileURL: URL? = this.javaClass.getResource(file)
        if (fileURL != null) {
            try {
                val ins = this.javaClass.getResourceAsStream(file)
                JarInputStream(ins).use { jarInputStream ->
                    while (true) {
                        val jarEntry = jarInputStream.nextJarEntry ?: break
                        if (jarEntry.name.endsWith(".class")) {
                            val classData = jarInputStream.readBytes()
                            // 使用 ASM 的 ClassReader 解析 class 文件
                            val classReader = ClassReader(classData)
                            list.add(classReader)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return list
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


    private fun getClazz(moduleData: ModuleData, classReader: ClassReader): Clazz {
        val clazz = Clazz()
        clazz.moduleData = moduleData
        classReader.accept(ClazzNode(clazz), ClassReader.SKIP_DEBUG)
        return clazz
    }
}

class ClazzNode(private val clazz: Clazz) : ClassNode(Opcodes.ASM9) {
    override fun visitEnd() {
        clazz.className = name
        clazz.superName = superName
        clazz.interfaces = interfaces
        clazz.fields = fields
        clazz.methods = methods
        clazz.visibleAnnotations = visibleAnnotations
    }
}
