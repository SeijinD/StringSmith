package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.ConcurrentHashMap

data class StringsXmlEntry(val key: String, val value: String)

object StringsXmlUtil {

    private val IGNORED_DIRS = listOf(
        "/build/", "\\build\\", "/.gradle/", "\\.gradle\\", "/.idea/", "\\.idea\\",
    )

    private fun isResStringsXml(vf: VirtualFile): Boolean =
        !vf.isDirectory &&
            vf.name == "strings.xml" &&
            vf.parent?.name?.startsWith("values") == true &&
            IGNORED_DIRS.none { vf.path.contains(it) }

    /**
     * Every `strings.xml` under a `res/values...` directory in the project. Uses the filename index (fast,
     * called on every intention `isAvailable`); falls back to a manual VFS walk while indexes are
     * unavailable (dumb mode).
     */
    fun findAllStringsXml(project: Project): List<VirtualFile> {
        val viaIndex = if (DumbService.isDumb(project)) {
            null
        } else {
            runCatching {
                FilenameIndex.getVirtualFilesByName("strings.xml", GlobalSearchScope.projectScope(project))
            }.getOrNull()
        }
        val files = viaIndex ?: scanStringsXml(project)
        return files.filter(::isResStringsXml)
    }

    private fun scanStringsXml(project: Project): List<VirtualFile> {
        val base = project.guessProjectDir() ?: return emptyList()
        val out = mutableListOf<VirtualFile>()
        VfsUtil.iterateChildrenRecursively(
            base,
            { vf -> IGNORED_DIRS.none { vf.path.contains(it) } },
        ) { vf ->
            if (isResStringsXml(vf)) out += vf
            true
        }
        return out
    }

    fun findDefaultStringsXml(project: Project, near: VirtualFile? = null): VirtualFile? {
        val all = findAllDefaultStringsXml(project)
        if (all.isEmpty()) return null
        if (near != null) {
            val nearest = all.minByOrNull { commonPrefix(it.path, near.path).length * -1 }
            if (nearest != null) return nearest
        }
        return all.firstOrNull()
    }

    fun findAllDefaultStringsXml(project: Project): List<VirtualFile> =
        findAllStringsXml(project).filter { it.parent?.name == "values" }

    fun findLocaleVariants(defaultFile: VirtualFile): List<VirtualFile> {
        val resDir = defaultFile.parent?.parent ?: return emptyList()
        val out = mutableListOf<VirtualFile>()
        for (child in resDir.children ?: emptyArray()) {
            if (!child.isDirectory) continue
            if (child.name == "values") continue
            if (!child.name.startsWith("values-")) continue
            val candidate = child.findChild("strings.xml") ?: continue
            out += candidate
        }
        return out
    }

    private class CachedEntries(val stamp: Long, val entries: List<StringsXmlEntry>)

    // Parsing every strings.xml on each intention `isAvailable` is the hot path; cache per file and
    // invalidate on the document's modification stamp so edits are still reflected immediately.
    private val entryCache = ConcurrentHashMap<VirtualFile, CachedEntries>()

    fun readEntries(file: VirtualFile): List<StringsXmlEntry> {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
        val stamp = doc.modificationStamp
        entryCache[file]?.let { if (it.stamp == stamp) return it.entries }
        val entries = StringsXmlText.parseEntries(doc.text)
        entryCache[file] = CachedEntries(stamp, entries)
        return entries
    }

    fun findExistingKey(file: VirtualFile, value: String): String? {
        val target = value.trim()
        return readEntries(file).firstOrNull { it.value == target }?.key
    }

    fun keyExists(file: VirtualFile, key: String): Boolean =
        readEntries(file).any { it.key == key }

    fun findValueOfKey(file: VirtualFile, key: String): String? =
        readEntries(file).firstOrNull { it.key == key }?.value

    fun appendEntry(file: VirtualFile, key: String, value: String, comment: String? = null, sortAlpha: Boolean = false) {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return
        val newText = StringsXmlText.appendEntry(doc.text, key, value, comment, sortAlpha)
        doc.setText(newText)
        FileDocumentManager.getInstance().saveDocument(doc)
    }

    fun offsetOfKey(file: VirtualFile, key: String): Int {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return -1
        return doc.text.indexOf("name=\"$key\"")
    }

    private fun commonPrefix(a: String, b: String): String {
        var i = 0
        val max = minOf(a.length, b.length)
        while (i < max && a[i] == b[i]) i++
        return a.substring(0, i)
    }
}
