package com.codelang.module

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
        if (qName != null && !ignoreViews.contains(qName) && qName.contains(".")) {
            views.add(qName)
        }
    }

    companion object {
        private val ignoreViews = ArrayList<String>(2)

        init {
            ignoreViews.add("include")
            ignoreViews.add("layout")
        }
    }
}
