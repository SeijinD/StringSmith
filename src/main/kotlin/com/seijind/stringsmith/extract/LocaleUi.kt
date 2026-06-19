package com.seijind.stringsmith.extract

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.seijind.stringsmith.StringSmithBundle
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Shared building blocks for the locale sections of the Extract and Batch dialogs. */
internal object LocaleUi {

    /** Group header with a variant count, e.g. "Locales (3)". */
    fun header(count: Int): String {
        val base = StringSmithBundle.message("label.locales.header")
        return if (count > 0) "$base ($count)" else base
    }

    /** Locale qualifier shown to the user, e.g. `values` or `values-de`. */
    fun localeLabel(file: VirtualFile): String = file.parent?.name ?: file.name

    /** A Swing [DocumentListener] that runs [action] on any text change. */
    fun changeListener(action: () -> Unit): DocumentListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = action()
        override fun removeUpdate(e: DocumentEvent) = action()
        override fun changedUpdate(e: DocumentEvent) = action()
    }

    /** Wraps [content] in a height-capped scroll pane so many locales never grow the dialog off-screen. */
    fun cappedScroller(content: JComponent, width: Int, cap: Int): JComponent =
        JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(width, minOf(content.preferredSize.height, cap).coerceAtLeast(JBUI.scale(28)))
        }
}
