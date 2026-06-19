package com.seijind.stringsmith.extract

import com.intellij.openapi.editor.Document
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
     * Writes [key] → each locale's value into every locale file that doesn't already declare [key].
     * Returns the files that had no editable document, so the caller can report them. Shared by the
     * single-extract and duplicate writers.
     */
    fun mirrorKeyToLocales(
        locales: List<Pair<VirtualFile, String>>,
        key: String,
        comment: String?,
        sortAlpha: Boolean
    ): List<VirtualFile> {
        val failed = mutableListOf<VirtualFile>()
        for ((file, value) in locales) {
            if (keyExists(file, key)) continue
            if (!appendEntry(file, key, value, comment, sortAlpha)) failed += file
        }
        return failed
    }

    /**
     * Inserts every draft in [drafts] into [file] with a single document write (one re-parse, one save).
     * Returns false if the file has no document to write into (so callers can report the failure).
     */
    fun appendEntries(file: VirtualFile, drafts: List<StringEntryDraft>, sortAlpha: Boolean = false): Boolean {
        if (drafts.isEmpty()) return true
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return false
        replaceMinimalDiff(doc, StringsXmlText.appendEntries(doc.text, drafts, sortAlpha))
        return true
    }

    /** Replaces the value of [key] in [file]. Returns false if the file has no editable document. */
    fun updateValue(file: VirtualFile, key: String, newValue: String): Boolean =
        applyDocument(file) { StringsXmlText.updateEntryValue(it, key, newValue) }

    /** Renames [oldKey] to [newKey] in [file] (no-op if absent). Returns false if no editable document. */
    fun renameKey(file: VirtualFile, oldKey: String, newKey: String): Boolean =
        applyDocument(file) { StringsXmlText.renameEntryKey(it, oldKey, newKey) }

    /** Removes [key] from [file] (no-op if absent). Returns false if the file has no editable document. */
    fun deleteKey(file: VirtualFile, key: String): Boolean =
        applyDocument(file) { StringsXmlText.deleteEntry(it, key) }

    /**
     * Rewrites [file]'s document via [transform]. Returns false if the file has no editable document.
     * The document is left unsaved on purpose — the platform persists it on its own; forcing
     * `saveDocument` here would do disk I/O on the EDT under the write lock.
     */
    private fun applyDocument(file: VirtualFile, transform: (String) -> String): Boolean {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return false
        replaceMinimalDiff(doc, transform(doc.text))
        return true
    }

    /**
     * Replaces only the changed span of [doc] (skipping the common prefix and suffix) so the user's
     * caret, selection, and folding survive when the target `strings.xml` happens to be open, and the
     * undo step covers just the edit instead of the whole file. The resulting text equals [newText].
     */
    private fun replaceMinimalDiff(doc: Document, newText: String) {
        val old = doc.charsSequence
        val oldLen = old.length
        val newLen = newText.length
        if (oldLen == newLen && old.contentEquals(newText)) return
        val maxPrefix = minOf(oldLen, newLen)
        var prefix = 0
        while (prefix < maxPrefix && old[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < maxPrefix - prefix &&
            old[oldLen - 1 - suffix] == newText[newLen - 1 - suffix]
        ) suffix++
        doc.replaceString(prefix, oldLen - suffix, newText.subSequence(prefix, newLen - suffix))
    }

    fun offsetOfKey(file: VirtualFile, key: String): Int {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return -1
        return StringsXmlText.offsetOfKey(doc.text, key)
    }
}
