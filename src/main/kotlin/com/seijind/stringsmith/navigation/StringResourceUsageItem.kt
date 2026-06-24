package com.seijind.stringsmith.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * Navigation target wrapping a real usage [origin] of a string resource. In the Ctrl+Click popup it
 * presents as "<code snippet>   (File.kt:line)" instead of the raw multi-line element text, while
 * navigation is delegated to [origin] so the caret still lands on the actual reference.
 */
internal class StringResourceUsageItem(
    private val origin: PsiElement,
    private val snippet: String,
    private val location: String,
    private val fileIcon: Icon?
) : FakePsiElement() {

    override fun getParent(): PsiElement = origin.parent ?: origin

    override fun getContainingFile(): PsiFile? = origin.containingFile

    override fun isValid(): Boolean = origin.isValid

    override fun navigate(requestFocus: Boolean) {
        (origin as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (origin as? Navigatable)?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean = (origin as? Navigatable)?.canNavigateToSource() ?: false

    override fun getName(): String = snippet

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = snippet
        override fun getLocationString(): String = location
        override fun getIcon(unused: Boolean): Icon? = fileIcon
    }
}
