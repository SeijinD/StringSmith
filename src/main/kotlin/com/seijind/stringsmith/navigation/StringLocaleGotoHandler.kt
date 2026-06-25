package com.seijind.stringsmith.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.seijind.stringsmith.extract.ResourceSystem
import com.seijind.stringsmith.extract.StringsXmlUtil
import com.seijind.stringsmith.inspections.StringsXmlFileFilter

/**
 * Ctrl+Click on a `<string name="x">` in a Compose Multiplatform strings.xml jumps to the same key in the
 * other locale `values-*` copies — the gesture Android Studio already provides natively for `res/values`,
 * but which has no equivalent for `composeResources`. Restricted to CMP so Android keeps its native
 * behaviour untouched (usages live behind Alt+Click via [StringResourceGotoUsagesAction]).
 */
class StringLocaleGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val file = element.containingFile ?: return null
        if (!StringsXmlFileFilter.isStringsXml(file)) return null
        val vFile = file.virtualFile ?: return null
        if (ResourceSystem.of(vFile) != ResourceSystem.COMPOSE_MULTIPLATFORM) return null
        if (DumbService.isDumb(file.project)) return null

        val key = keyUnderCaret(element) ?: return null
        val project = file.project
        val default = StringsXmlUtil.findDefaultStringsXml(project, vFile) ?: return null
        val siblings = (listOf(default) + StringsXmlUtil.findLocaleVariants(default)).filter { it != vFile }

        val psiManager = PsiManager.getInstance(project)
        val targets = siblings.mapNotNull { locale ->
            val xml = psiManager.findFile(locale) as? XmlFile ?: return@mapNotNull null
            nameAttrValue(xml, key)
        }
        return targets.ifEmpty { null }?.toTypedArray()
    }

    /** The `name` attribute value element of `<string name="key">` in [xml], or null if absent. */
    private fun nameAttrValue(xml: XmlFile, key: String): XmlAttributeValue? {
        val root = xml.rootTag ?: return null
        for (tag in root.findSubTags("string")) {
            val attr = tag.getAttribute("name") ?: continue
            if (attr.value == key) return attr.valueElement
        }
        return null
    }

    /** Resource key when [element] sits on the `name` attribute value of a `<string>` tag, else null. */
    private fun keyUnderCaret(element: PsiElement): String? {
        val attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false) ?: return null
        val attr = attrValue.parent as? XmlAttribute ?: return null
        if (attr.name != "name") return null
        val tag = PsiTreeUtil.getParentOfType(attr, XmlTag::class.java) ?: return null
        if (tag.name != "string") return null
        return attr.value?.takeIf { it.isNotEmpty() }
    }
}
