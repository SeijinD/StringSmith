package com.seijind.stringsmith.extract

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.seijind.stringsmith.StringSmithBundle
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/** Shared building blocks for the locale sections of the Extract and Batch dialogs. */
internal object LocaleUi {

    /** Group header with a variant count, e.g. "Locales (3)". */
    fun header(count: Int): String {
        val base = StringSmithBundle.message("label.locales.header")
        return if (count > 0) "$base ($count)" else base
    }

    /** Wraps [content] in a height-capped scroll pane so many locales never grow the dialog off-screen. */
    fun cappedScroller(content: JComponent, width: Int, cap: Int): JComponent =
        JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(width, minOf(content.preferredSize.height, cap).coerceAtLeast(JBUI.scale(28)))
        }
}
