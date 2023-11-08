package com.codelang.module.collect

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.codelang.module.bean.XmlModuleData
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableDependencies
import java.io.File
import javax.xml.parsers.SAXParserFactory

object XmlCollectModule {

    fun collectDepLayoutModule(
        project: Project,
        resolvableDeps: ResolvableDependencies
    ): List<XmlModuleData> {
        // 获取 dependencies layout
        val xmlModules = arrayListOf<XmlModuleData>()

        resolvableDeps.artifactView { conf ->
            conf.attributes { attr ->
                attr.attribute(
                    AndroidArtifacts.ARTIFACT_TYPE,
                    AndroidArtifacts.ArtifactType.ANDROID_RES.type
                )
            }
        }.artifacts.forEach { result ->
            val dep = result.variant.displayName.split(" ").find { it.contains(":") }
                ?: result.variant.displayName

            // layout 下的自定义 view
            val layout = result.file.absolutePath + File.separator + "layout"
            val file = File(layout)
            if (file.exists() && file.isDirectory) {

                val views = file.listFiles()?.filter { it.name.endsWith(".xml") }?.map { xml ->
                    val handler = XmlLayoutHandler()
                    SAXParserFactory.newInstance().newSAXParser().parse(xml, handler)
                    handler.views
                }?.flatten()?.toList() ?: emptyList()

                xmlModules.add(XmlModuleData(dep, views))
            }
        }

        return xmlModules
    }
}