package com.seijind.stringsmith.inspections

import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile

object StringsXmlFileFilter {

    fun isStringsXml(file: PsiFile): Boolean {
        if (file !is XmlFile) return false
        if (file.name != "strings.xml") return false
        val parentName = file.virtualFile?.parent?.name ?: return false
        return parentName == "values" || parentName.startsWith("values-")
    }
}
