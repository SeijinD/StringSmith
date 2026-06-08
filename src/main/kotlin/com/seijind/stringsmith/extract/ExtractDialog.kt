package com.seijind.stringsmith.extract

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.seijind.stringsmith.StringSmithBundle
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

data class LocaleEntry(val file: VirtualFile, val value: String, val include: Boolean)

data class ExtractDialogResult(
    val key: String,
    val reuseExisting: Boolean,
    val targetStringsXml: VirtualFile,
    val defaultValue: String,
    val localeEntries: List<LocaleEntry>
)

class ExtractDialog(
    project: Project,
    rawValue: String,
    private val target: ExtractTarget,
    suggestedKey: String,
    private val existingKey: String?,
    initialTarget: VirtualFile,
    private val allTargets: List<VirtualFile>
) : DialogWrapper(project, true) {

    private val projectBasePath: String? = project.basePath?.replace('\\', '/')?.trimEnd('/')

    private val keyField: JTextField = JBTextField(suggestedKey).apply { columns = 50 }
    private val valueField: JTextField = JBTextField(rawValue).apply { columns = 50 }
    private val previewLabel = JBLabel(Replacement.referenceFor(target, suggestedKey)).apply {
        font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, font.size)
    }
    private val keyErrorLabel = JBLabel().apply { foreground = JBColor.RED }
    private val valueErrorLabel = JBLabel().apply { foreground = JBColor.RED }
    private var currentExistingKey: String? = existingKey
    private val reuseCheckbox: JCheckBox = JCheckBox(
        existingKey?.let { StringSmithBundle.message("checkbox.reuse", it) } ?: "",
        false
    ).apply {
        font = font.deriveFont(java.awt.Font.BOLD)
        foreground = JBColor(java.awt.Color(0x2864B0), java.awt.Color(0x6FA8DC))
        isVisible = existingKey != null
    }

    private val moduleModel = DefaultComboBoxModel<VirtualFile>().apply { allTargets.forEach { addElement(it) } }
    private val moduleCombo: ComboBox<VirtualFile> = ComboBox(moduleModel).apply {
        selectedItem = initialTarget
        setRenderer { _, value, _, _, _ ->
            JBLabel(value?.let { describeTarget(it) } ?: "").apply {
                icon = AllIcons.Nodes.Module
                iconTextGap = 6
            }
        }
    }

    private data class LocaleRow(val variant: VirtualFile, val include: JCheckBox, val value: JTextField)

    private var localeRows: List<LocaleRow> = emptyList()

    init {
        title = StringSmithBundle.message("dialog.title")
        rebuildLocaleRows(initialTarget)
        init()
        wireListeners()
        refreshAll()
    }

    override fun createCenterPanel(): JComponent = panel {
        if (allTargets.size > 1) {
            row(StringSmithBundle.message("label.module")) {
                cell(moduleCombo).align(AlignX.FILL)
            }
        }
        row {
            cell(reuseCheckbox).align(AlignX.FILL)
        }
        row(StringSmithBundle.message("label.key")) {
            cell(keyField).align(AlignX.FILL)
        }
        row("") {
            cell(keyErrorLabel)
        }
        row(StringSmithBundle.message("label.replacement")) {
            cell(previewLabel).applyToComponent { foreground = JBColor.GRAY }
        }
        row(StringSmithBundle.message("label.value")) {
            cell(valueField).align(AlignX.FILL)
        }
        row("") {
            cell(valueErrorLabel)
        }
        row {
            cell(buildLocalePanel()).align(AlignX.FILL)
        }
    }

    private fun buildLocalePanel(): JComponent = panel {
        group(StringSmithBundle.message("label.locales.header")) {
            row(StringSmithBundle.message("label.locales.default")) {
                label(StringSmithBundle.message("label.locales.defaultHint")).applyToComponent { foreground = JBColor.GRAY }
            }
            localeRows.forEach { lr ->
                row {
                    cell(lr.include)
                    label("${lr.variant.parent?.name ?: lr.variant.name}:")
                    cell(lr.value).align(AlignX.FILL)
                }
            }
            if (localeRows.isNotEmpty()) {
                row {
                    link(StringSmithBundle.message("link.copyToAll")) {
                        val v = valueField.text
                        localeRows.forEach { it.value.text = v }
                    }
                    link(StringSmithBundle.message("link.selectAll")) { localeRows.forEach { it.include.isSelected = true } }
                    link(StringSmithBundle.message("link.selectNone")) { localeRows.forEach { it.include.isSelected = false } }
                }
            }
        }
    }

    private fun rebuildLocaleRows(target: VirtualFile) {
        val variants = StringsXmlUtil.findLocaleVariants(target)
        localeRows = variants.map { variant ->
            LocaleRow(
                variant = variant,
                include = JCheckBox("", true),
                value = JBTextField(valueField.text).apply { columns = 25 }
            )
        }
    }

    private fun describeTarget(file: VirtualFile): String {
        val moduleRoot = inferModuleRootFrom(file)
        val normalized = file.path.replace('\\', '/')
        val relative = projectBasePath
            ?.takeIf { normalized.startsWith("$it/") }
            ?.let { normalized.removePrefix("$it/") }
            ?: normalized
        return moduleRoot?.let { "${it.name}  ($relative)" } ?: relative
    }

    private fun inferModuleRootFrom(stringsXml: VirtualFile): VirtualFile? =
        stringsXml.parent?.parent?.parent?.parent?.parent

    private fun wireListeners() {
        keyField.document.addDocumentListener(simpleListener { refreshAll() })
        valueField.document.addDocumentListener(simpleListener { refreshAll() })
        reuseCheckbox.addActionListener { refreshAll() }
        moduleCombo.addActionListener { onModuleChanged() }
    }

    private fun onModuleChanged() {
        currentExistingKey = StringsXmlUtil.findExistingKey(currentStringsXml(), valueField.text)
        reuseCheckbox.isSelected = false
        if (currentExistingKey != null) {
            reuseCheckbox.text = StringSmithBundle.message("checkbox.reuse", currentExistingKey!!)
            reuseCheckbox.isVisible = true
        } else {
            reuseCheckbox.isVisible = false
        }
        refreshAll()
    }

    private fun simpleListener(action: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = action()
        override fun removeUpdate(e: DocumentEvent) = action()
        override fun changedUpdate(e: DocumentEvent) = action()
    }

    private fun refreshAll() {
        val reuse = reuseCheckbox?.isSelected == true
        keyField.isEnabled = !reuse
        valueField.isEnabled = !reuse
        localeRows.forEach { it.include.isEnabled = !reuse; it.value.isEnabled = !reuse && it.include.isSelected }
        val effectiveKey = if (reuse) currentExistingKey.orEmpty() else keyField.text
        previewLabel.text = if (effectiveKey.isBlank()) "—" else Replacement.referenceFor(target, effectiveKey)
        val keyErr = keyError()
        val valueErr = valueError()
        keyErrorLabel.text = keyErr ?: ""
        valueErrorLabel.text = valueErr ?: ""
        isOKActionEnabled = keyErr == null && valueErr == null
    }

    private fun currentStringsXml(): VirtualFile = (moduleCombo.selectedItem as? VirtualFile) ?: allTargets.first()

    private fun keyError(): String? {
        if (reuseCheckbox?.isSelected == true) return null
        val key = keyField.text
        if (key.isBlank()) return StringSmithBundle.message("error.keyRequired")
        if (!KeyGenerator.isValidKey(key)) return StringSmithBundle.message("error.invalidKey")
        if (StringsXmlUtil.keyExists(currentStringsXml(), key) && key != currentExistingKey) {
            return StringSmithBundle.message("error.keyExists", key)
        }
        return null
    }

    private fun valueError(): String? {
        if (reuseCheckbox?.isSelected == true) return null
        if (valueField.text.isBlank()) return StringSmithBundle.message("error.valueRequired")
        return null
    }

    override fun doValidate(): ValidationInfo? {
        keyError()?.let { return ValidationInfo(it, keyField) }
        valueError()?.let { return ValidationInfo(it, valueField) }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = keyField

    fun result(): ExtractDialogResult {
        val reuse = reuseCheckbox?.isSelected == true
        val key = if (reuse) currentExistingKey!! else keyField.text.trim()
        return ExtractDialogResult(
            key = key,
            reuseExisting = reuse,
            targetStringsXml = currentStringsXml(),
            defaultValue = valueField.text,
            localeEntries = localeRows.map { LocaleEntry(it.variant, it.value.text, it.include.isSelected) }
        )
    }
}
