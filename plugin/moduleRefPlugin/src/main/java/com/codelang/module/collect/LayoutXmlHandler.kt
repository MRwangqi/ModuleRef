package com.codelang.module.collect

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class LayoutXmlHandler : DefaultHandler() {
    var views = HashSet<String>(4)
    override fun startElement(
        uri: String,
        localName: String,
        qName: String?,
        attributes: Attributes
    ) {
        // 内部自定义 view 一般都有包名，即带有 .
        if (qName != null && qName.contains(".")) {
            views.add(qName.replace(".", "/"))
        }
    }
}
