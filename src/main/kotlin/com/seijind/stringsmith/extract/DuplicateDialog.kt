package com.seijind.stringsmith.extract

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.seijind.stringsmith.StringSmithBundle
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

data class DuplicateDialogResult(val newKey: String, val updateReference: Boolean)

class DuplicateDialog(
    project: Project,
    private val source: DuplicateSource
) : DialogWrapper(project, true) {

    private val keyField: JTextField = JBTextField("${source.key}_copy").apply { columns = 40 }
    private val previewLabel = JBLabel().apply {
        font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, font.size)
    }
    private val updateRefCheckbox = JCheckBox(
        StringSmithBundle.message("duplicate.checkbox.updateRef"),
        false
    ).apply { isVisible = source.codeRef != null }

    init {
        title = StringSmithBundle.message("duplicate.dialog.title")
        keyField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refreshPreview()
            override fun removeUpdate(e: DocumentEvent) = refreshPreview()
            override fun changedUpdate(e: DocumentEvent) = refreshPreview()
        })
        refreshPreview()
        init()
    }

    private fun refreshPreview() {
        previewLabel.text = StringSmithBundle.message("duplicate.preview", keyField.text.trim(), source.defaultValue)
    }

    override fun createCenterPanel(): JComponent = panel {
        row(StringSmithBundle.message("label.value")) { cell(JBLabel(source.defaultValue)) }
        row(StringSmithBundle.message("duplicate.label.newKey")) { cell(keyField) }
        row("") { cell(previewLabel) }
        row(StringSmithBundle.message("duplicate.label.copiesTo")) {
            cell(summaryLabel(copyTargets()))
        }
        if (source.untranslatedLocales.isNotEmpty()) {
            row(StringSmithBundle.message("duplicate.label.skipped")) {
                cell(summaryLabel(source.untranslatedLocales.map(::localeLabel)).apply {
                    foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
                })
            }
        }
        if (source.codeRef != null) {
            row("") { cell(updateRefCheckbox) }
        }
    }

    /** Locale qualifier shown to the user, e.g. `values` or `values-de`. */
    private fun localeLabel(file: VirtualFile): String = file.parent?.name ?: file.name

    /** Default file plus every locale that receives the copy. */
    private fun copyTargets(): List<String> =
        listOf(localeLabel(source.defaultFile)) + source.localeValues.keys.map(::localeLabel)

    /**
     * Lists locale folders inline when few, otherwise collapses to a count with the full list in a
     * tooltip, so a project with many locales doesn't stretch the dialog into one giant line.
     */
    private fun summaryLabel(locales: List<String>): JBLabel {
        val full = locales.joinToString(", ")
        return if (locales.size <= MAX_INLINE_LOCALES) {
            JBLabel(full)
        } else {
            JBLabel(StringSmithBundle.message("duplicate.locales.count", locales.size))
                .apply { toolTipText = full }
        }
    }

    private companion object {
        const val MAX_INLINE_LOCALES = 6
    }

    override fun getPreferredFocusedComponent(): JComponent = keyField

    override fun doValidate(): ValidationInfo? {
        val key = keyField.text.trim()
        if (key.isEmpty()) return ValidationInfo(StringSmithBundle.message("error.keyRequired"), keyField)
        if (!KeyGenerator.isValidKey(key)) return ValidationInfo(StringSmithBundle.message("error.invalidKey"), keyField)
        if (StringsXmlUtil.keyExists(source.defaultFile, key)) {
            return ValidationInfo(StringSmithBundle.message("error.keyExists", key), keyField)
        }
        return null
    }

    fun result(): DuplicateDialogResult =
        DuplicateDialogResult(keyField.text.trim(), updateRefCheckbox.isSelected)
}
