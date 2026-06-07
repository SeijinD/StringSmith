package com.seijind.stringsmith.extract

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

data class StringsXmlEntry(val key: String, val value: String)

object StringsXmlUtil {

    fun findAllStringsXml(project: Project): List<VirtualFile> {
        val base = project.guessProjectDir() ?: return emptyList()
        val out = mutableListOf<VirtualFile>()
        VfsUtil.iterateChildrenRecursively(base, { vf ->
            !vf.path.contains("/build/") &&
                !vf.path.contains("\\build\\") &&
                !vf.path.contains("/.gradle/") &&
                !vf.path.contains("\\.gradle\\") &&
                !vf.path.contains("/.idea/") &&
                !vf.path.contains("\\.idea\\")
        }) { vf ->
            if (!vf.isDirectory && vf.name == "strings.xml" && vf.parent?.name?.startsWith("values") == true) {
                out += vf
            }
            true
        }
        return out
    }

    fun findDefaultStringsXml(project: Project, near: VirtualFile? = null): VirtualFile? {
        val all = findAllStringsXml(project)
        if (all.isEmpty()) return null
        if (near != null) {
            val nearest = all.filter { it.parent?.name == "values" }
                .minByOrNull { commonPrefix(it.path, near.path).length * -1 }
            if (nearest != null) return nearest
        }
        return all.firstOrNull { it.parent?.name == "values" } ?: all.firstOrNull()
    }

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

    fun readEntries(file: VirtualFile): List<StringsXmlEntry> {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
        val text = doc.text
        val regex = Regex("""<string\s+name\s*=\s*"([^"]+)"\s*>([\s\S]*?)</string>""")
        return regex.findAll(text).map { m ->
            StringsXmlEntry(m.groupValues[1], decodeXml(m.groupValues[2]))
        }.toList()
    }

    fun findExistingKey(file: VirtualFile, value: String): String? {
        val target = value.trim()
        return readEntries(file).firstOrNull { it.value == target }?.key
    }

    fun keyExists(file: VirtualFile, key: String): Boolean =
        readEntries(file).any { it.key == key }

    fun appendEntry(file: VirtualFile, key: String, value: String) {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return
        val text = doc.text
        val escaped = encodeXml(value)
        val entry = "    <string name=\"$key\">$escaped</string>\n"
        val closeIdx = text.lastIndexOf("</resources>")
        val newText = if (closeIdx >= 0) {
            text.substring(0, closeIdx) + entry + text.substring(closeIdx)
        } else {
            buildString {
                append(text.trimEnd())
                append("\n<resources>\n")
                append(entry)
                append("</resources>\n")
            }
        }
        doc.setText(newText)
        FileDocumentManager.getInstance().saveDocument(doc)
    }

    private fun commonPrefix(a: String, b: String): String {
        var i = 0
        val max = minOf(a.length, b.length)
        while (i < max && a[i] == b[i]) i++
        return a.substring(0, i)
    }

    private fun encodeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "\\\"")
        .replace("'", "\\'")

    private fun decodeXml(value: String): String = value
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
        .trim()
}
