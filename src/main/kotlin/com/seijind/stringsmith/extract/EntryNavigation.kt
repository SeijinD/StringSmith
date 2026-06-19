package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Opens a `strings.xml` in the editor with the caret on a given key's entry. Shared by the writers. */
object EntryNavigation {

    fun openAtKey(project: Project, stringsXml: VirtualFile, key: String) {
        val offset = StringsXmlUtil.offsetOfKey(stringsXml, key)
        if (offset >= 0) {
            FileEditorManager.getInstance(project).openTextEditor(
                OpenFileDescriptor(project, stringsXml, offset),
                true
            )
        }
    }
}
