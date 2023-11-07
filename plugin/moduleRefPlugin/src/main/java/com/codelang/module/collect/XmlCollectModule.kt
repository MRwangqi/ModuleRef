package com.codelang.module.collect

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.codelang.module.bean.XmlModuleData
import org.gradle.api.Project
import java.io.File
import javax.xml.parsers.SAXParserFactory

object XmlCollectModule {

    fun collectDepLayoutModule(
        project: Project,
        configurationName: String
    ): List<XmlModuleData> {
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
            // layout 下的自定义 view
            val layout = result.file.absolutePath + File.separator + "layout"
            val file = File(layout)
            return if (file.exists() && file.isDirectory) {
                file.listFiles()?.filter { it.name.endsWith(".xml") }?.map { xml ->
                    val handler = LayoutXmlHandler()
                    SAXParserFactory.newInstance().newSAXParser().parse(xml, handler)
                    handler.views
                }?.map {
                    XmlModuleData(dep, it.toList())
                } ?: emptyList()
            } else {
                emptyList()
            }
        }.toList()
    }
}