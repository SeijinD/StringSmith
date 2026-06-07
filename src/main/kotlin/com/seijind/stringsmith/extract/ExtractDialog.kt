package com.seijind.stringsmith.extract

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
    private val project: Project,
    rawValue: String,
    private val target: ExtractTarget,
    suggestedKey: String,
    private val existingKey: String?,
    initialTarget: VirtualFile,
    private val allTargets: List<VirtualFile>
) : DialogWrapper(project, true) {

    private val keyField: JTextField = JBTextField(suggestedKey).apply { columns = 30 }
    private val valueField: JTextField = JBTextField(rawValue).apply { columns = 30 }
    private val previewLabel = JBLabel()
    private val errorLabel = JBLabel().apply { foreground = JBColor.RED }
    private val reuseCheckbox: JCheckBox? = existingKey?.let { JCheckBox("Reuse existing key \"$it\"", true) }

    private val moduleModel = DefaultComboBoxModel<VirtualFile>().apply { allTargets.forEach { addElement(it) } }
    private val moduleCombo: ComboBox<VirtualFile> = ComboBox(moduleModel).apply {
        selectedItem = initialTarget
        setRenderer { _, value, _, _, _ ->
            JBLabel(value?.let { describeTarget(it) } ?: "")
        }
    }

    private data class LocaleRow(val variant: VirtualFile, val include: JCheckBox, val value: JTextField)

    private var localeRows: List<LocaleRow> = emptyList()
    private var currentVariantsPanelStamp: VirtualFile? = null

    init {
        title = "Extract String Resource"
        rebuildLocaleRows(initialTarget)
        init()
        wireListeners()
        refreshAll()
    }

    override fun createCenterPanel(): JComponent = panel {
        if (allTargets.size > 1) {
            row("Module:") {
                cell(moduleCombo).align(AlignX.FILL)
            }
        }
        if (reuseCheckbox != null) {
            row {
                cell(reuseCheckbox).align(AlignX.FILL)
            }
        }
        row("Key:") {
            cell(keyField).align(AlignX.FILL)
        }
        row("") {
            cell(errorLabel)
        }
        row("Replacement:") {
            cell(previewLabel).applyToComponent { foreground = JBColor.GRAY }
        }
        row("Value:") {
            cell(valueField).align(AlignX.FILL)
        }
        row {
            cell(buildLocalePanel()).align(AlignX.FILL)
        }
    }

    private fun buildLocalePanel(): JComponent = panel {
        group("Locale Files (edit value per locale)") {
            row("values:") {
                label("(default — uses Value above)").applyToComponent { foreground = JBColor.GRAY }
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
                    link("Copy default to all") {
                        val v = valueField.text
                        localeRows.forEach { it.value.text = v }
                    }
                    link("Select all") { localeRows.forEach { it.include.isSelected = true } }
                    link("Select none") { localeRows.forEach { it.include.isSelected = false } }
                }
            }
        }
    }

    private fun rebuildLocaleRows(target: VirtualFile) {
        if (currentVariantsPanelStamp == target) return
        currentVariantsPanelStamp = target
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
        val moduleRoot = findModuleRoot(file)
        return moduleRoot?.let { "${it.name}  (${file.path})" } ?: file.path
    }

    private fun findModuleRoot(stringsXml: VirtualFile): VirtualFile? {
        var cur: VirtualFile? = stringsXml.parent?.parent?.parent?.parent
        return cur
    }

    private fun wireListeners() {
        keyField.document.addDocumentListener(simpleListener { refreshAll() })
        valueField.document.addDocumentListener(simpleListener { refreshAll() })
        reuseCheckbox?.addActionListener { refreshAll() }
        moduleCombo.addActionListener { refreshAll() }
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
        val effectiveKey = if (reuse) existingKey.orEmpty() else keyField.text
        previewLabel.text = if (effectiveKey.isBlank()) "—" else Replacement.referenceFor(target, effectiveKey)
        errorLabel.text = validationError() ?: ""
        isOKActionEnabled = errorLabel.text.isEmpty()
    }

    private fun currentStringsXml(): VirtualFile = (moduleCombo.selectedItem as? VirtualFile) ?: allTargets.first()

    private fun validationError(): String? {
        if (reuseCheckbox?.isSelected == true) return null
        val key = keyField.text
        if (valueField.text.isBlank()) return "Value required"
        if (key.isBlank()) return "Key required"
        if (!KeyGenerator.isValidKey(key)) return "Use letters, digits, underscore. Must start with a letter."
        if (StringsXmlUtil.keyExists(currentStringsXml(), key) && key != existingKey) return "Key \"$key\" already exists in selected module."
        return null
    }

    override fun doValidate(): ValidationInfo? {
        val err = validationError() ?: return null
        return ValidationInfo(err, keyField)
    }

    fun result(): ExtractDialogResult {
        val reuse = reuseCheckbox?.isSelected == true
        val key = if (reuse) existingKey!! else keyField.text.trim()
        return ExtractDialogResult(
            key = key,
            reuseExisting = reuse,
            targetStringsXml = currentStringsXml(),
            defaultValue = valueField.text,
            localeEntries = localeRows.map { LocaleEntry(it.variant, it.value.text, it.include.isSelected) }
        )
    }
}
