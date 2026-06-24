package com.seijind.stringsmith.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.seijind.stringsmith.inspections.StringsXmlFileFilter

/**
 * Ctrl+Click on a `<string name="x">` declaration in strings.xml navigates to its usages
 * (`R.string.x`, `Res.string.x` or `@string/x`) — the same gesture that jumps from a class/method to
 * its definition, but in reverse for resources that have no PSI declaration the platform knows about.
 */
class StringResourceGotoUsagesHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val file = element.containingFile ?: return null
        if (!StringsXmlFileFilter.isStringsXml(file)) return null
        if (DumbService.isDumb(file.project)) return null

        val key = keyUnderCaret(element) ?: return null
        val targets = StringResourceUsageFinder.findUsages(file.project, key)
        return if (targets.isEmpty()) null else targets.toTypedArray()
    }

    /** Returns the resource key when [element] sits on the `name` attribute value of a `<string>` tag. */
    private fun keyUnderCaret(element: PsiElement): String? {
        val attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false) ?: return null
        val attr = attrValue.parent as? XmlAttribute ?: return null
        if (attr.name != "name") return null
        val tag = PsiTreeUtil.getParentOfType(attr, XmlTag::class.java) ?: return null
        if (tag.name != "string") return null
        return attr.value?.takeIf { it.isNotEmpty() }
    }
}
