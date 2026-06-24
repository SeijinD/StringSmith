package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings

class DuplicateStringValueInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!StringSmithSettings.getInstance().duplicateValueInspectionEnabled) return null
        if (!StringsXmlFileFilter.isStringsXml(file)) return null

        val tags = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java)
            .filter { it.name == "string" && it.getAttributeValue("name") != null }

        val byValue = mutableMapOf<String, MutableList<XmlTag>>()
        for (tag in tags) {
            ProgressManager.checkCanceled()
            val value = tag.value.text.trim()
            if (value.isEmpty()) continue
            byValue.getOrPut(value) { mutableListOf() }.add(tag)
        }

        val problems = mutableListOf<ProblemDescriptor>()
        for ((value, group) in byValue) {
            ProgressManager.checkCanceled()
            if (group.size < 2) continue
            val keys = group.mapNotNull { it.getAttributeValue("name") }
            val firstKey = keys.firstOrNull() ?: continue
            for (tag in group.drop(1)) {
                val nameAttr = tag.getAttribute("name") ?: continue
                problems.add(
                    manager.createProblemDescriptor(
                        nameAttr.valueElement ?: nameAttr,
                        StringSmithBundle.message("inspection.duplicateValue.message", value, firstKey),
                        true,
                        emptyArray(),
                        ProblemHighlightType.WEAK_WARNING
                    )
                )
            }
        }
        return problems.toTypedArray()
    }
}
