package com.seijind.stringsmith.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * Navigation target wrapping a real usage [origin] of a string resource. In the usages popup it
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

    override fun getNavigationElement(): PsiElement = origin

    override fun getName(): String = snippet

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = snippet
        override fun getLocationString(): String = location
        override fun getIcon(unused: Boolean): Icon? = fileIcon
    }
}
