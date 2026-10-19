package com.seijind.stringsmith.navigation

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.inspections.StringsXmlFileFilter
import java.util.concurrent.Callable

/**
 * Alt+Click (or the bound shortcut) on a `<string name="x">` declaration in strings.xml navigates to its
 * usages (`R.string.x`, `Res.string.x` or `@string/x`).
 *
 * Kept separate from Ctrl+Click so the platform's native gesture (jump between locale `values-*` copies of
 * the same key) stays intact on Android. This action provides the reverse — find call sites — on both
 * Android and Compose Multiplatform resources.
 */
class StringResourceGotoUsagesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = keyUnderCaret(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val key = keyUnderCaret(e) ?: return

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        var targets: List<PsiElement> = emptyList()
        val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                targets = ReadAction.nonBlocking(Callable { StringResourceUsageFinder.findUsages(project, key) })
                    .inSmartMode(project)
                    .executeSynchronously()
            },
            StringSmithBundle.message("progress.findUsages", key),
            true,
            project
        )
        if (!completed) return
        if (targets.isEmpty()) {
            HintManager.getInstance().showInformationHint(editor, StringSmithBundle.message("usages.none", key))
            return
        }
        // navigate() jumps straight on a single target, otherwise shows the chooser popup.
        PsiTargetNavigator(targets.toTypedArray()).navigate(editor, StringSmithBundle.message("usages.popup.title", key))
    }

    /** Resource key when the caret sits on the `name` attribute value of a `<string>` tag, else null. */
    private fun keyUnderCaret(e: AnActionEvent): String? {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        if (!StringsXmlFileFilter.isStringsXml(file)) return null
        // update() runs under a read lock (ActionUpdateThread.BGT); actionPerformed() runs on EDT — both
        // hold read access, so no explicit ReadAction wrapper is needed.
        val element = file.findElementAt(editor.caretModel.offset) ?: return null
        return StringsXmlFileFilter.keyAt(element)
    }
}
