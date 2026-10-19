package com.seijind.stringsmith.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

object StringsXmlFileFilter {

    fun isStringsXml(file: PsiFile): Boolean {
        if (file !is XmlFile) return false
        if (file.name != "strings.xml") return false
        val parentName = file.virtualFile?.parent?.name ?: return false
        return parentName == "values" || parentName.startsWith("values-")
    }

    /** Resource key when [element] sits on the `name` attribute value of a `<string>` tag, else null. */
    fun keyAt(element: PsiElement): String? {
        val attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false) ?: return null
        val attr = attrValue.parent as? XmlAttribute ?: return null
        if (attr.name != "name") return null
        val tag = PsiTreeUtil.getParentOfType(attr, XmlTag::class.java) ?: return null
        if (tag.name != "string") return null
        return attr.value?.takeIf { it.isNotEmpty() }
    }
}
