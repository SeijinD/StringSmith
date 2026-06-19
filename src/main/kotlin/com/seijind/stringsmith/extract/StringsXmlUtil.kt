package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil

data class StringsXmlEntry(val key: String, val value: String)

/** A `<string>` to be written, with an optional preceding comment (e.g. `from File.kt:42`). */
data class StringEntryDraft(val key: String, val value: String, val comment: String? = null)

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
            // Prefer the default file sharing the longest path prefix with `near` (same module/tree).
            val nearest = all.maxByOrNull { it.path.commonPrefixWith(near.path).length }
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

    // Per-file parse cache, invalidated by the document's modification stamp. Weak keys avoid leaking
    // entries for deleted/closed files.
    private val entryCache = ContainerUtil.createConcurrentWeakMap<VirtualFile, CachedEntries>()

    fun readEntries(file: VirtualFile): List<StringsXmlEntry> {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
        val stamp = doc.modificationStamp
        entryCache[file]?.let { if (it.stamp == stamp) return it.entries }
        val entries = StringsXmlText.parseEntries(doc.text)
        entryCache[file] = CachedEntries(stamp, entries)
        return entries
    }

    /** All declared keys in [file] (declaration order), parsed once and cached via [readEntries]. */
    fun readKeys(file: VirtualFile): Set<String> =
        readEntries(file).mapTo(LinkedHashSet()) { it.key }

    fun findExistingKey(file: VirtualFile, value: String): String? {
        val target = value.trim()
        return readEntries(file).firstOrNull { it.value == target }?.key
    }

    fun keyExists(file: VirtualFile, key: String): Boolean =
        readEntries(file).any { it.key == key }

    fun findValueOfKey(file: VirtualFile, key: String): String? =
        readEntries(file).firstOrNull { it.key == key }?.value

    fun appendEntry(file: VirtualFile, key: String, value: String, comment: String? = null, sortAlpha: Boolean = false): Boolean =
        appendEntries(file, listOf(StringEntryDraft(key, value, comment)), sortAlpha)

    /**
     * Inserts every draft in [drafts] into [file] with a single document write (one re-parse, one save).
     * Returns false if the file has no document to write into (so callers can report the failure).
     */
    fun appendEntries(file: VirtualFile, drafts: List<StringEntryDraft>, sortAlpha: Boolean = false): Boolean {
        if (drafts.isEmpty()) return true
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return false
        doc.setText(StringsXmlText.appendEntries(doc.text, drafts, sortAlpha))
        FileDocumentManager.getInstance().saveDocument(doc)
        return true
    }

    /** Replaces the value of [key] in [file]. Returns false if the file has no editable document. */
    fun updateValue(file: VirtualFile, key: String, newValue: String): Boolean =
        applyAndSave(file) { StringsXmlText.updateEntryValue(it, key, newValue) }

    /** Renames [oldKey] to [newKey] in [file] (no-op if absent). Returns false if no editable document. */
    fun renameKey(file: VirtualFile, oldKey: String, newKey: String): Boolean =
        applyAndSave(file) { StringsXmlText.renameEntryKey(it, oldKey, newKey) }

    /** Removes [key] from [file] (no-op if absent). Returns false if the file has no editable document. */
    fun deleteKey(file: VirtualFile, key: String): Boolean =
        applyAndSave(file) { StringsXmlText.deleteEntry(it, key) }

    /** Rewrites [file]'s document via [transform]. Returns false if the file has no editable document. */
    private fun applyAndSave(file: VirtualFile, transform: (String) -> String): Boolean {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return false
        doc.setText(transform(doc.text))
        FileDocumentManager.getInstance().saveDocument(doc)
        return true
    }

    fun offsetOfKey(file: VirtualFile, key: String): Int {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return -1
        return doc.text.indexOf("name=\"$key\"")
    }
}
