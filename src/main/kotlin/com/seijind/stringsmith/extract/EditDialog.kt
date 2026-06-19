package com.seijind.stringsmith.extract

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.seijind.stringsmith.StringSmithBundle
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

/** One locale edited in the dialog: [originalValue] is null when that locale had no translation yet. */
data class EditLocaleEdit(val file: VirtualFile, val originalValue: String?, val newValue: String)

data class EditDialogResult(
    val originalKey: String,
    val newKey: String,
    val newDefaultValue: String,
    val localeEdits: List<EditLocaleEdit>
)

/** Edits an existing string resource in place: its key, default value, and every locale translation. */
class EditDialog(
    project: Project,
    private val source: DuplicateSource
) : DialogWrapper(project, true) {

    private val keyField: JTextField = JBTextField(source.key).apply { columns = 50 }
    private val valueField: JTextField = JBTextField(source.defaultValue).apply { columns = 50 }
    private val previewLabel = JBLabel().apply {
        font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, font.size)
    }
    private val keyErrorLabel = JBLabel().apply { foreground = JBColor.RED }
    private val valueErrorLabel = JBLabel().apply { foreground = JBColor.RED }

    private data class LocaleRow(val variant: VirtualFile, val originalValue: String?, val value: JTextField)

    private val localeRows: List<LocaleRow> = StringsXmlUtil.findLocaleVariants(source.defaultFile).map { variant ->
        val existing = source.localeValues[variant]
        LocaleRow(variant, existing, JBTextField(existing.orEmpty()).apply { columns = 25 })
    }

    init {
        title = StringSmithBundle.message("edit.dialog.title")
        init()
        wireListeners()
        refreshAll()
        keyField.selectAll()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(StringSmithBundle.message("label.key")) {
            cell(keyField).align(AlignX.FILL)
        }
        row("") { cell(keyErrorLabel) }
        row(StringSmithBundle.message("label.replacement")) {
            cell(previewLabel).applyToComponent { foreground = JBColor.GRAY }
        }
        row(StringSmithBundle.message("label.value")) {
            cell(valueField).align(AlignX.FILL)
        }
        row("") { cell(valueErrorLabel) }
        row {
            cell(buildLocalePanel()).align(AlignX.FILL)
        }
    }

    private fun buildLocalePanel(): JComponent {
        if (localeRows.isEmpty()) return JPanel()
        return panel {
            group(LocaleUi.header(localeRows.size)) {
                row(StringSmithBundle.message("label.locales.default")) {
                    label(StringSmithBundle.message("label.locales.defaultHint")).applyToComponent { foreground = JBColor.GRAY }
                }
                row {
                    cell(buildLocaleRowsScroller()).align(AlignX.FILL)
                }
                row {
                    link(StringSmithBundle.message("link.copyToAll")) {
                        val v = valueField.text
                        localeRows.forEach { it.value.text = v }
                    }
                    link(StringSmithBundle.message("edit.link.clearAll")) {
                        localeRows.forEach { it.value.text = "" }
                    }
                }
            }
        }
    }

    private fun buildLocaleRowsScroller(): JComponent {
        val rowsPanel = panel {
            localeRows.forEach { lr ->
                row {
                    label("${LocaleUi.localeLabel(lr.variant)}:")
                    cell(lr.value).align(AlignX.FILL)
                }
            }
        }
        return LocaleUi.cappedScroller(rowsPanel, width = JBUI.scale(560), cap = JBUI.scale(220))
    }

    private fun wireListeners() {
        keyField.document.addDocumentListener(LocaleUi.changeListener { refreshAll() })
        valueField.document.addDocumentListener(LocaleUi.changeListener { refreshAll() })
    }

    private fun refreshAll() {
        val key = keyField.text.trim()
        previewLabel.text = if (key.isBlank()) "—" else referencePreview(key)
        val keyErr = keyError()
        val valueErr = valueError()
        keyErrorLabel.text = keyErr ?: ""
        valueErrorLabel.text = valueErr ?: ""
        isOKActionEnabled = keyErr == null && valueErr == null
    }

    private fun referencePreview(key: String): String = when (source.system) {
        ResourceSystem.COMPOSE_MULTIPLATFORM -> "${ResourceSystem.CMP_REF_PREFIX}$key"
        ResourceSystem.ANDROID -> "${ResourceSystem.ANDROID_REF_PREFIX}$key"
    }

    private fun keyError(): String? {
        val key = keyField.text.trim()
        if (key.isBlank()) return StringSmithBundle.message("error.keyRequired")
        if (!KeyGenerator.isValidKey(key)) return StringSmithBundle.message("error.invalidKey")
        // The unchanged key is allowed; only a collision with a *different* existing key is an error.
        if (key != source.key && StringsXmlUtil.keyExists(source.defaultFile, key)) {
            return StringSmithBundle.message("error.keyExists", key)
        }
        return null
    }

    private fun valueError(): String? {
        if (valueField.text.isBlank()) return StringSmithBundle.message("error.valueRequired")
        return null
    }

    override fun doValidate(): ValidationInfo? {
        keyError()?.let { return ValidationInfo(it, keyField) }
        valueError()?.let { return ValidationInfo(it, valueField) }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = keyField

    fun result(): EditDialogResult = EditDialogResult(
        originalKey = source.key,
        newKey = keyField.text.trim(),
        newDefaultValue = valueField.text,
        localeEdits = localeRows.map { EditLocaleEdit(it.variant, it.originalValue, it.value.text) }
    )
}
