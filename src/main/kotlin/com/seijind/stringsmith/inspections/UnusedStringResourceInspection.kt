package com.seijind.stringsmith.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings
import java.util.concurrent.ConcurrentHashMap
import kotlin.experimental.or

class UnusedStringResourceInspection : LocalInspectionTool() {

    // Memoize per-key verdicts; drop the cache on any project PSI change so results never go stale.
    @Volatile private var cacheModCount: Long = -1L
    private val refCache = ConcurrentHashMap<String, Boolean>()

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!StringSmithSettings.getInstance().unusedStringInspectionEnabled) return null
        if (!StringsXmlFileFilter.isStringsXml(file)) return null

        val tags = PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java)
            .filter { it.name == "string" && it.getAttributeValue("name") != null }
        if (tags.isEmpty()) return null

        val helper = PsiSearchHelper.getInstance(file.project)
        val scope = GlobalSearchScope.projectScope(file.project)

        val modCount = PsiModificationTracker.getInstance(file.project).modificationCount
        if (modCount != cacheModCount) {
            synchronized(this) {
                if (modCount != cacheModCount) {
                    refCache.clear()
                    cacheModCount = modCount
                }
            }
        }

        val problems = mutableListOf<ProblemDescriptor>()
        for (tag in tags) {
            val key = tag.getAttributeValue("name") ?: continue
            if (refCache.computeIfAbsent(key) { isReferencedAnywhere(helper, scope, it) }) continue
            val nameAttr = tag.getAttribute("name") ?: continue
            problems.add(
                manager.createProblemDescriptor(
                    nameAttr.valueElement ?: nameAttr,
                    StringSmithBundle.message("inspection.unusedString.message", key),
                    true,
                    emptyArray(),
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL
                )
            )
        }
        return problems.toTypedArray()
    }

    private fun isReferencedAnywhere(
        helper: PsiSearchHelper,
        scope: GlobalSearchScope,
        key: String
    ): Boolean {
        var found = false
        helper.processElementsWithWord(
            { element, offsetInElement ->
                val owner = element.containingFile ?: return@processElementsWithWord true
                if (owner.name == "strings.xml") return@processElementsWithWord true
                val text = element.text ?: return@processElementsWithWord true
                // Widen the look-behind to fit the longest prefix: "Res.string." (11) for Compose
                // Multiplatform, plus "R.string." (9) and "@string/" (8) for Android.
                val start = maxOf(0, offsetInElement - 12)
                val end = minOf(text.length, offsetInElement + key.length + 2)
                val window = text.substring(start, end)
                if (window.contains("R.string.$key") ||
                    window.contains("Res.string.$key") ||
                    window.contains("@string/$key")
                ) {
                    found = true
                    return@processElementsWithWord false
                }
                true
            },
            scope,
            key,
            (UsageSearchContext.IN_CODE or UsageSearchContext.IN_FOREIGN_LANGUAGES or UsageSearchContext.IN_STRINGS),
            true
        )
        return found
    }
}
