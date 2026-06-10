package com.seijind.stringsmith.extract

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel

enum class BatchRowStatus { NEW, REUSE, DUPLICATE, COLLISION, INVALID }

data class BatchRow(
    val target: ExtractTarget,
    var include: Boolean,
    var key: String,
    val value: String,
    val sourceLine: Int,
    var existingKey: String?,
    var status: BatchRowStatus
)

data class BatchLocaleSelection(val file: VirtualFile, val include: Boolean)

data class BatchDialogResult(
    val rows: List<BatchRow>,
    val targetStringsXml: VirtualFile,
    val localeSelections: List<BatchLocaleSelection>
)

class BatchDialog(
    project: Project,
    private val initialRows: List<BatchRow>,
    initialTarget: VirtualFile,
    private val allTargets: List<VirtualFile>
) : DialogWrapper(project, true) {

    private val projectBasePath: String? = project.basePath?.replace('\\', '/')?.trimEnd('/')

    private val rows: MutableList<BatchRow> = initialRows.toMutableList()
    private val tableModel = RowModel()
    private val table: JBTable = JBTable(tableModel).apply {
        setShowGrid(true)
        rowHeight = 22
        columnModel.getColumn(0).preferredWidth = 50
        columnModel.getColumn(1).preferredWidth = 50
        columnModel.getColumn(2).preferredWidth = 280
        columnModel.getColumn(3).preferredWidth = 200
        columnModel.getColumn(4).preferredWidth = 110
        preferredScrollableViewportSize = java.awt.Dimension(700, rowHeight * 18)
    }

    private val resolvedTarget: VirtualFile = initialTarget
    private val moduleLabel: JBLabel = JBLabel(describeTarget(initialTarget)).apply {
        icon = AllIcons.Nodes.Module
        iconTextGap = 6
    }

    private val summaryLabel = JBLabel("").apply { foreground = JBColor.GRAY }

    private data class LocaleRow(val variant: VirtualFile, val include: JCheckBox)
    private var localeRows: List<LocaleRow> = emptyList()

    init {
        title = StringSmithBundle.message("batch.dialog.title")
        rebuildLocaleRows(initialTarget)
        refreshAllStatuses()
        init()
        wireListeners()
        refreshSummary()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(StringSmithBundle.message("label.module")) {
            cell(moduleLabel).align(AlignX.FILL)
        }
        row {
            cell(JBScrollPane(table)).align(AlignX.FILL).resizableColumn()
        }.resizableRow()
        row {
            link(StringSmithBundle.message("batch.link.selectAll")) {
                rows.forEachIndexed { i, _ -> rows[i] = rows[i].copy(include = true) }
                tableModel.fireTableDataChanged()
                refreshSummary()
            }
            link(StringSmithBundle.message("batch.link.selectNone")) {
                rows.forEachIndexed { i, _ -> rows[i] = rows[i].copy(include = false) }
                tableModel.fireTableDataChanged()
                refreshSummary()
            }
        }
        row {
            cell(buildLocalePanel()).align(AlignX.FILL)
        }
        row {
            cell(summaryLabel)
        }
    }

    private fun buildLocalePanel(): JComponent = panel {
        group(StringSmithBundle.message("label.locales.header")) {
            if (localeRows.isEmpty()) {
                row {
                    label(StringSmithBundle.message("batch.locales.none")).applyToComponent { foreground = JBColor.GRAY }
                }
            } else {
                localeRows.forEach { lr ->
                    row {
                        cell(lr.include)
                        label(lr.variant.parent?.name ?: lr.variant.name)
                    }
                }
            }
        }
    }

    private fun rebuildLocaleRows(target: VirtualFile) {
        val variants = StringsXmlUtil.findLocaleVariants(target)
        val defaultInclude = StringSmithSettings.getInstance().autoIncludeLocales
        localeRows = variants.map { LocaleRow(it, JCheckBox("", defaultInclude)) }
    }

    private fun wireListeners() {
    }

    private fun currentStringsXml(): VirtualFile = resolvedTarget

    private fun describeTarget(file: VirtualFile): String {
        val moduleRoot = file.parent?.parent?.parent?.parent?.parent
        val normalized = file.path.replace('\\', '/')
        val relative = projectBasePath
            ?.takeIf { normalized.startsWith("$it/") }
            ?.let { normalized.removePrefix("$it/") }
            ?: normalized
        return moduleRoot?.let { "${it.name}  ($relative)" } ?: relative
    }

    private fun refreshAllStatuses() {
        val xml = currentStringsXml()
        rows.forEachIndexed { i, r ->
            val existing = StringsXmlUtil.findExistingKey(xml, r.value)
            val effectiveKey = existing ?: r.key
            rows[i] = r.copy(
                existingKey = existing,
                key = effectiveKey,
                status = computeStatus(effectiveKey, r.value, xml, existing, i)
            )
        }
    }

    private fun recomputeStatusesPreservingKeys() {
        val xml = currentStringsXml()
        rows.forEachIndexed { i, r ->
            rows[i] = r.copy(status = computeStatus(r.key, r.value, xml, r.existingKey, i))
        }
    }

    private fun computeStatus(key: String, value: String, xml: VirtualFile, existingKey: String?, selfIndex: Int): BatchRowStatus {
        if (!KeyGenerator.isValidKey(key)) return BatchRowStatus.INVALID
        if (existingKey != null && key == existingKey) return BatchRowStatus.REUSE
        val keyExistsInXml = StringsXmlUtil.keyExists(xml, key)
        if (keyExistsInXml && existingKey != key) return BatchRowStatus.COLLISION
        val sameKeyDiffValueInBatch = rows.withIndex().any { (i, other) ->
            i != selfIndex && other.include && other.key == key && other.value != value
        }
        if (sameKeyDiffValueInBatch) return BatchRowStatus.COLLISION
        val dupInBatch = rows.withIndex().any { (i, other) ->
            i != selfIndex && other.value == value
        }
        if (dupInBatch) return BatchRowStatus.DUPLICATE
        return BatchRowStatus.NEW
    }

    private fun refreshSummary() {
        val included = rows.count { it.include }
        val blocking = rows.count { it.include && (it.status == BatchRowStatus.COLLISION || it.status == BatchRowStatus.INVALID) }
        summaryLabel.text = StringSmithBundle.message("batch.summary", included, rows.size, blocking)
        isOKActionEnabled = included > 0 && blocking == 0
    }

    override fun doValidate(): ValidationInfo? {
        val blocking = rows.firstOrNull { it.include && (it.status == BatchRowStatus.COLLISION || it.status == BatchRowStatus.INVALID) }
        return blocking?.let { ValidationInfo(StringSmithBundle.message("batch.error.blocking", it.key)) }
    }

    fun result(): BatchDialogResult = BatchDialogResult(
        rows = rows.toList(),
        targetStringsXml = currentStringsXml(),
        localeSelections = localeRows.map { BatchLocaleSelection(it.variant, it.include.isSelected) }
    )

    private inner class RowModel : AbstractTableModel() {
        private val columns = arrayOf(
            StringSmithBundle.message("batch.col.include"),
            StringSmithBundle.message("batch.col.line"),
            StringSmithBundle.message("batch.col.value"),
            StringSmithBundle.message("batch.col.key"),
            StringSmithBundle.message("batch.col.status")
        )
        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(c: Int): String = columns[c]
        override fun getColumnClass(c: Int): Class<*> = if (c == 0) java.lang.Boolean::class.java else String::class.java
        override fun isCellEditable(r: Int, c: Int): Boolean = c == 0 || c == 3
        override fun getValueAt(r: Int, c: Int): Any = when (c) {
            0 -> rows[r].include
            1 -> rows[r].sourceLine.toString()
            2 -> rows[r].value
            3 -> rows[r].key
            4 -> displayStatus(rows[r].status)
            else -> ""
        }
        override fun setValueAt(value: Any?, r: Int, c: Int) {
            val row = rows[r]
            when (c) {
                0 -> {
                    rows[r] = row.copy(include = value as Boolean)
                    recomputeStatusesPreservingKeys()
                }
                3 -> {
                    val newKey = (value as? String)?.trim().orEmpty()
                    rows[r] = row.copy(key = newKey)
                    recomputeStatusesPreservingKeys()
                }
            }
            fireTableDataChanged()
            refreshSummary()
        }
        private fun displayStatus(s: BatchRowStatus): String = when (s) {
            BatchRowStatus.NEW -> StringSmithBundle.message("batch.status.new")
            BatchRowStatus.REUSE -> StringSmithBundle.message("batch.status.reuse")
            BatchRowStatus.DUPLICATE -> StringSmithBundle.message("batch.status.duplicate")
            BatchRowStatus.COLLISION -> StringSmithBundle.message("batch.status.collision")
            BatchRowStatus.INVALID -> StringSmithBundle.message("batch.status.invalid")
        }
    }
}
