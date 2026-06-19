package com.seijind.stringsmith.extract

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.settings.StringSmithSettings
import java.awt.Color
import java.awt.Component
import javax.swing.DefaultCellEditor
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

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

    private val proj: Project = project

    private val rows: MutableList<BatchRow> = initialRows.toMutableList()
    private val tableModel = RowModel()
    private val keyEditorField = JBTextField()
    private val table: JBTable = JBTable(tableModel).apply {
        setShowGrid(true)
        rowHeight = 22
        columnModel.getColumn(0).preferredWidth = 50
        columnModel.getColumn(1).preferredWidth = 50
        columnModel.getColumn(2).preferredWidth = 280
        columnModel.getColumn(3).preferredWidth = 200
        columnModel.getColumn(3).cellEditor = DefaultCellEditor(keyEditorField).apply { clickCountToStart = 1 }
        columnModel.getColumn(4).preferredWidth = 110
        columnModel.getColumn(4).cellRenderer = StatusCellRenderer()
        preferredScrollableViewportSize = java.awt.Dimension(700, rowHeight * 18)
    }

    private val resolvedTarget: VirtualFile = initialTarget
    private val moduleLabel: JBLabel = JBLabel(describeTarget(initialTarget)).apply {
        icon = AllIcons.Nodes.Module
        iconTextGap = 6
    }

    private val summaryLabel = JBLabel("").apply { foreground = JBColor.GRAY }
    private val countsLabel = JBLabel("").apply { foreground = JBColor.GRAY }

    private data class LocaleRow(val variant: VirtualFile, val include: JCheckBox)
    private var localeRows: List<LocaleRow> = emptyList()

    init {
        title = StringSmithBundle.message("batch.dialog.title")
        rebuildLocaleRows(initialTarget)
        refreshAllStatuses()
        init()
        refreshSummary()
        // Live status/summary while typing a key — the table model only commits on Enter/focus loss.
        keyEditorField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onKeyEdited()
            override fun removeUpdate(e: DocumentEvent) = onKeyEdited()
            override fun changedUpdate(e: DocumentEvent) = onKeyEdited()
        })
    }

    private fun onKeyEdited() {
        val r = table.editingRow
        if (r < 0 || r >= rows.size) return
        rows[r] = rows[r].copy(key = keyEditorField.text.trim())
        recomputeStatusesPreservingKeys()
        refreshSummary()
        table.repaint()
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
            link(StringSmithBundle.message("batch.link.fixCollisions")) { fixCollisions() }
        }
        row {
            cell(buildLocalePanel()).align(AlignX.FILL)
        }
        row {
            cell(summaryLabel)
        }
        row {
            cell(countsLabel)
        }
    }

    /** Auto-suffixes every colliding key (`key`, `key_2`, …) until it no longer clashes with the file or another included row. */
    private fun fixCollisions() {
        val xml = currentStringsXml()
        rows.forEachIndexed { i, r ->
            if (r.status == BatchRowStatus.COLLISION) {
                rows[i] = r.copy(key = uniqueKey(r.key, xml, i))
            }
        }
        recomputeStatusesPreservingKeys()
        tableModel.fireTableDataChanged()
        refreshSummary()
    }

    private fun uniqueKey(base: String, xml: VirtualFile, selfIndex: Int): String {
        fun taken(k: String): Boolean =
            StringsXmlUtil.keyExists(xml, k) ||
                rows.withIndex().any { (j, r) -> j != selfIndex && r.include && r.key == k }
        if (!taken(base)) return base
        var n = 2
        while (taken("${base}_$n")) n++
        return "${base}_$n"
    }

    private fun buildLocalePanel(): JComponent = panel {
        group(LocaleUi.header(localeRows.size)) {
            if (localeRows.isEmpty()) {
                row {
                    label(StringSmithBundle.message("batch.locales.none")).applyToComponent { foreground = JBColor.GRAY }
                }
            } else {
                row {
                    cell(buildLocaleRowsScroller()).align(AlignX.FILL)
                }
            }
        }
    }

    private fun buildLocaleRowsScroller(): JComponent {
        val rowsPanel = panel {
            localeRows.forEach { lr ->
                row {
                    cell(lr.include)
                    label(LocaleUi.localeLabel(lr.variant))
                }
            }
        }
        return LocaleUi.cappedScroller(rowsPanel, width = JBUI.scale(320), cap = JBUI.scale(180))
    }

    private fun rebuildLocaleRows(target: VirtualFile) {
        val variants = StringsXmlUtil.findLocaleVariants(target)
        val defaultInclude = StringSmithSettings.getInstance().autoIncludeLocales
        localeRows = variants.map { LocaleRow(it, JCheckBox("", defaultInclude)) }
    }

    private fun currentStringsXml(): VirtualFile = resolvedTarget

    private fun describeTarget(file: VirtualFile): String {
        val moduleRoot = ModuleRootUtil.findModuleRoot(file)
        val relative = DisplayPath.projectRelative(proj, file)
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

    private fun computeStatus(key: String, value: String, xml: VirtualFile, existingKey: String?, selfIndex: Int): BatchRowStatus =
        BatchStatus.compute(
            rows.map { BatchStatus.RowFacts(it.include, it.key, it.value) },
            selfIndex, key, value, existingKey, StringsXmlUtil.keyExists(xml, key)
        )

    private fun refreshSummary() {
        val included = rows.count { it.include }
        val blocking = rows.count { it.include && (it.status == BatchRowStatus.COLLISION || it.status == BatchRowStatus.INVALID) }
        summaryLabel.text = StringSmithBundle.message("batch.summary", included, rows.size, blocking)
        fun count(s: BatchRowStatus) = rows.count { it.status == s }
        countsLabel.text = StringSmithBundle.message(
            "batch.counts",
            count(BatchRowStatus.NEW), count(BatchRowStatus.REUSE), count(BatchRowStatus.DUPLICATE),
            count(BatchRowStatus.COLLISION), count(BatchRowStatus.INVALID)
        )
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

    /** Colours and icons the Status column so blocking rows stand out at a glance. */
    private inner class StatusCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            val status = rows.getOrNull(row)?.status
            label.icon = status?.let { statusIcon(it) }
            label.iconTextGap = 6
            if (!isSelected) label.foreground = status?.let { statusColor(it) } ?: label.foreground
            return label
        }
    }

    private fun statusIcon(status: BatchRowStatus): Icon = when (status) {
        BatchRowStatus.NEW -> AllIcons.General.InspectionsOK
        BatchRowStatus.REUSE -> AllIcons.Actions.Refresh
        BatchRowStatus.DUPLICATE -> AllIcons.Actions.Copy
        BatchRowStatus.COLLISION -> AllIcons.General.Warning
        BatchRowStatus.INVALID -> AllIcons.General.Error
    }

    private fun statusColor(status: BatchRowStatus): JBColor = when (status) {
        BatchRowStatus.NEW -> JBColor(Color(0x59A869), Color(0x6CC07A))
        BatchRowStatus.REUSE -> JBColor(Color(0x2864B0), Color(0x6FA8DC))
        BatchRowStatus.DUPLICATE -> JBColor(Color(0xB07B28), Color(0xD9A343))
        BatchRowStatus.COLLISION, BatchRowStatus.INVALID -> JBColor(Color(0xC0392B), Color(0xE06C5A))
    }

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
