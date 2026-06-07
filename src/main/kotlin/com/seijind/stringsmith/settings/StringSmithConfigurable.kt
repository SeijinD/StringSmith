package com.seijind.stringsmith.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class StringSmithConfigurable : Configurable {

    private var dialogPanel: DialogPanel? = null
    private val settings get() = StringSmithSettings.getInstance()
    private val previewLabel = JBLabel()
    private val regexErrorLabel = JBLabel().apply { foreground = JBColor.RED }

    override fun getDisplayName(): String = "StringSmith – Android String Extractor"

    override fun createComponent(): JComponent {
        val builder = panel {
            group("Key Generation") {
                row("Prefix:") {
                    textField()
                        .bindText({ settings.keyPrefix }, { settings.keyPrefix = it; refreshPreview() })
                        .comment("Prepended to every suggested key. Leave empty to disable.")
                        .align(AlignX.FILL)
                }
                row("Naming:") {
                    comboBox(NamingConvention.entries.toList())
                        .bindItem(
                            { settings.namingConvention },
                            { v -> if (v != null) { settings.namingConvention = v; refreshPreview() } }
                        )
                }
                row("Max key length:") {
                    intTextField(1..200)
                        .bindIntText({ settings.maxKeyLength }, { settings.maxKeyLength = it; refreshPreview() })
                        .comment("Truncate suggested keys to this length.")
                }
                row("Min string length:") {
                    intTextField(1..50)
                        .bindIntText({ settings.minStringLength }, { settings.minStringLength = it })
                        .comment("Reject strings shorter than this. Avoids extracting \"x\", \"a\".")
                }
                row("Preview:") {
                    cell(previewLabel)
                        .comment("Example: key generated for \"Welcome to the app\".")
                }
            }

            group("Replacement Style") {
                row("Compose:") {
                    comboBox(ComposeArgStyle.entries.toList())
                        .bindItem(
                            { settings.composeStyle },
                            { v -> if (v != null) settings.composeStyle = v }
                        )
                }
                row("Activity / Fragment:") {
                    comboBox(ActivityReplacementStyle.entries.toList())
                        .bindItem(
                            { settings.activityStyle },
                            { v -> if (v != null) settings.activityStyle = v }
                        )
                }
            }

            group("Locale Files") {
                row("Locale propagation:") {
                    comboBox(LocalePropagation.entries.toList())
                        .bindItem(
                            { settings.localePropagation },
                            { v -> if (v != null) settings.localePropagation = v }
                        )
                        .comment("How to handle <code>values-*/strings.xml</code> when extracting.")
                }
            }

            group("Target Module") {
                row {
                    checkBox("Remember last selected module across extracts")
                        .bindSelected({ settings.rememberLastModule }, { settings.rememberLastModule = it })
                        .comment("When the project has multiple <code>values/strings.xml</code> files, preselect the last one used.")
                }
            }

            group("strings.xml Behavior") {
                row {
                    checkBox("Sort entries alphabetically after extract")
                        .bindSelected({ settings.sortAfterExtract }, { settings.sortAfterExtract = it })
                }
                row {
                    checkBox("Open strings.xml and jump to the new entry")
                        .bindSelected({ settings.openStringsXmlAfterExtract }, { settings.openStringsXmlAfterExtract = it })
                }
                row {
                    checkBox("Add XML comment with source file:line")
                        .bindSelected({ settings.addSourceComment }, { settings.addSourceComment = it })
                }
                row {
                    checkBox("Trim leading/trailing whitespace from extracted value")
                        .bindSelected({ settings.trimWhitespace }, { settings.trimWhitespace = it })
                }
            }

            group("Exclusion Patterns") {
                row {
                    textArea()
                        .bindText({ settings.excludePatterns }, { settings.excludePatterns = it })
                        .align(AlignX.FILL)
                        .applyToComponent {
                            rows = 4
                            document.addDocumentListener(object : DocumentListener {
                                override fun insertUpdate(e: DocumentEvent) = refreshRegexError(this@applyToComponent)
                                override fun removeUpdate(e: DocumentEvent) = refreshRegexError(this@applyToComponent)
                                override fun changedUpdate(e: DocumentEvent) = refreshRegexError(this@applyToComponent)
                            })
                            refreshRegexError(this)
                        }
                        .comment("One regex per line. Strings matching any pattern are skipped. Defaults skip CONSTANTS and URLs.")
                }
                row {
                    cell(regexErrorLabel)
                }
            }

            row {
                button("Restore Defaults") {
                    settings.resetToDefaults()
                    dialogPanel?.reset()
                    refreshPreview()
                }
            }
        }
        dialogPanel = builder
        refreshPreview()
        return builder
    }

    private fun refreshRegexError(area: JTextArea) {
        val invalid = area.text.lines()
            .withIndex()
            .filter { it.value.isNotBlank() }
            .firstOrNull { runCatching { Regex(it.value) }.isFailure }
        regexErrorLabel.text = if (invalid == null) {
            ""
        } else {
            val msg = runCatching { Regex(invalid.value) }.exceptionOrNull()?.message ?: "invalid regex"
            "Line ${invalid.index + 1}: $msg"
        }
    }

    private fun refreshPreview() {
        val sample = "Welcome to the app"
        val key = previewKey(sample)
        previewLabel.text = "→ $key"
    }

    private fun previewKey(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9]+"), " ").trim()
        val base = when (settings.namingConvention) {
            NamingConvention.SNAKE_CASE -> cleaned.lowercase().replace(' ', '_')
            NamingConvention.CAMEL_CASE -> {
                val parts = cleaned.split(' ').filter { it.isNotEmpty() }
                if (parts.isEmpty()) "" else parts.first().lowercase() +
                    parts.drop(1).joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercaseChar() } }
            }
        }
        val withPrefix = if (settings.keyPrefix.isNotBlank()) {
            val prefix = settings.keyPrefix.trim().trimEnd('_')
            when (settings.namingConvention) {
                NamingConvention.SNAKE_CASE -> "${prefix.lowercase()}_$base"
                NamingConvention.CAMEL_CASE -> prefix + base.replaceFirstChar { it.uppercaseChar() }
            }
        } else base
        return withPrefix.take(settings.maxKeyLength.coerceAtLeast(1)).ifEmpty { "label" }
    }

    override fun isModified(): Boolean = dialogPanel?.isModified() == true

    override fun apply() {
        dialogPanel?.apply()
        refreshPreview()
    }

    override fun reset() {
        dialogPanel?.reset()
        refreshPreview()
    }

    override fun disposeUIResources() {
        dialogPanel = null
    }
}
