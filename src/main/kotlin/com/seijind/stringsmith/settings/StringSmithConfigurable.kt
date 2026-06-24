package com.seijind.stringsmith.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.KeyGenerator
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class StringSmithConfigurable : Configurable {

    private var dialogPanel: DialogPanel? = null
    private val settings get() = StringSmithSettings.getInstance()
    private val sampleField = JBTextField(DEFAULT_SAMPLE)
    private val previewLabel = JBLabel()
    private val regexErrorLabel = JBLabel().apply { foreground = JBColor.RED }

    override fun getDisplayName(): String = "StringSmith - Android Strings Toolkit"

    override fun createComponent(): JComponent {
        val builder = panel {
            group("Key Generation") {
                row("Prefix:") {
                    textField()
                        .bindText({ settings.keyPrefix }, { settings.keyPrefix = it; refreshPreview() })
                        .comment("Prepended to every suggested key. Leave empty to disable.")
                        .align(AlignX.FILL)
                        .applyToComponent { toolTipText = "Example: \"app\" → app_welcome_screen" }
                }
                row("Naming:") {
                    comboBox(NamingConvention.entries.toList())
                        .bindItem(
                            { settings.namingConvention },
                            { v -> if (v != null) { settings.namingConvention = v; refreshPreview() } }
                        )
                        .applyToComponent { toolTipText = "Case style for generated keys" }
                }
                row("Max key length:") {
                    intTextField(1..200)
                        .bindIntText({ settings.maxKeyLength }, { settings.maxKeyLength = it; refreshPreview() })
                        .comment("Truncate suggested keys to this length.")
                        .applyToComponent { toolTipText = "Hard cap on key length after prefix" }
                }
                row("Min string length:") {
                    intTextField(1..50)
                        .bindIntText({ settings.minStringLength }, { settings.minStringLength = it })
                        .comment("Reject strings shorter than this. Avoids extracting \"x\", \"a\".")
                        .applyToComponent { toolTipText = "Strings shorter than this fail validation" }
                }
                row("Sample:") {
                    cell(sampleField)
                        .align(AlignX.FILL)
                        .comment("Type any text to preview the generated key live.")
                }
                row("Preview:") {
                    cell(previewLabel)
                }
            }

            group("Replacement Style") {
                row("Compose:") {
                    comboBox(ComposeArgStyle.entries.toList())
                        .bindItem(
                            { settings.composeStyle },
                            { v -> if (v != null) settings.composeStyle = v }
                        )
                        .applyToComponent { toolTipText = "How stringResource(...) is called in @Composable" }
                }
                row("Activity / Fragment:") {
                    comboBox(ActivityReplacementStyle.entries.toList())
                        .bindItem(
                            { settings.activityStyle },
                            { v -> if (v != null) settings.activityStyle = v }
                        )
                        .applyToComponent { toolTipText = "How getString(...) is invoked from Android UI classes" }
                }
            }

            group("Locale Files") {
                row {
                    checkBox("Auto-include locale variants by default")
                        .bindSelected({ settings.autoIncludeLocales }, { settings.autoIncludeLocales = it })
                        .comment("Initial checkbox state for <code>values-*/strings.xml</code> rows in the extract dialog.")
                        .applyToComponent { toolTipText = "When on, all locale variant rows start checked; when off, they start unchecked" }
                }
            }

            group("Custom Composable Wrappers") {
                row {
                    textArea()
                        .bindText({ settings.customComposableLambdaFunctions }, { settings.customComposableLambdaFunctions = it })
                        .align(AlignX.FILL)
                        .applyToComponent {
                            rows = 3
                            toolTipText = "One function name per line, comma- or space-separated. Strings inside their trailing lambda use stringResource."
                        }
                        .comment("Function names whose trailing lambda is a <code>@Composable</code> scope (e.g. <code>screenViewComposable</code>). Strings inside them use <code>stringResource</code> instead of <code>getString</code>. Separate names with a newline, comma, or space.")
                }
            }

            group("Kotlin Multiplatform") {
                row {
                    textField()
                        .label("Res package override:", LabelPosition.TOP)
                        .bindText({ settings.cmpResPackageOverride }, { settings.cmpResPackageOverride = it })
                        .align(AlignX.FILL)
                        .applyToComponent {
                            toolTipText = "e.g. com.example.app.generated.resources — leave blank to auto-detect"
                        }
                        .comment(
                            "Generated <code>Res</code> class package for Compose Multiplatform " +
                                "(<code>composeResources</code>) targets. Blank = auto-detect: gradle " +
                                "<code>packageOfResClass</code> → existing <code>*.generated.resources.Res</code> " +
                                "imports → derived from module package."
                        )
                }
            }

            group("Compose Previews") {
                row {
                    checkBox("Exclude strings inside @Preview composables")
                        .bindSelected({ settings.excludePreviewComposables }, { settings.excludePreviewComposables = it })
                        .comment("Skip strings inside functions annotated with <code>@Preview</code> (typically dummy data).")
                        .applyToComponent { toolTipText = "Recommended on; previews usually contain sample text not meant for translation" }
                }
            }

            group("Format Strings") {
                row {
                    checkBox("Detect template expressions and extract as %1\$s placeholders")
                        .bindSelected({ settings.detectFormatArgs }, { settings.detectFormatArgs = it })
                        .comment("Converts <code>\"Hello \$name\"</code> to <code>\"Hello %1\$s\"</code> in strings.xml and passes the original expression as a format argument.")
                        .applyToComponent { toolTipText = "When off, strings with template expressions are skipped entirely" }
                }
            }

            group("Inspection") {
                row {
                    checkBox("Highlight hardcoded strings in editor")
                        .bindSelected({ settings.inspectionEnabled }, { settings.inspectionEnabled = it })
                        .comment("Adds a warning under each extractable hardcoded literal with an Extract quick-fix.")
                        .applyToComponent { toolTipText = "Off by default; opt in for passive discovery of unextracted strings" }
                }
                row {
                    checkBox("Ignore logging strings")
                        .bindSelected({ settings.ignoreLoggingStrings }, { settings.ignoreLoggingStrings = it })
                        .comment("Don't flag literals passed to <code>Log.*</code>, <code>Timber.*</code>, <code>println</code>, <code>require</code>/<code>check</code>. Annotation arguments and <code>const</code> values are always skipped.")
                        .applyToComponent { toolTipText = "Logging and assertion messages are usually not user-facing text" }
                }
                row {
                    checkBox("Flag duplicate values in strings.xml")
                        .bindSelected({ settings.duplicateValueInspectionEnabled }, { settings.duplicateValueInspectionEnabled = it })
                        .comment("Reports two or more <code>&lt;string&gt;</code> entries with the same text under different keys.")
                        .applyToComponent { toolTipText = "Encourages key reuse across modules and locales" }
                }
                row {
                    checkBox("Flag unused string resources")
                        .bindSelected({ settings.unusedStringInspectionEnabled }, { settings.unusedStringInspectionEnabled = it })
                        .comment("Reports keys in <code>strings.xml</code> with no <code>R.string.key</code>, <code>Res.string.key</code> (Compose Multiplatform), or <code>@string/key</code> reference in the project.")
                        .applyToComponent { toolTipText = "Text-based search; dynamic key construction (e.g. \"key_\$type\") may report false positives" }
                }
                row {
                    checkBox("Flag format-string mismatches across locales")
                        .bindSelected({ settings.formatMismatchInspectionEnabled }, { settings.formatMismatchInspectionEnabled = it })
                        .comment("Reports a locale <code>&lt;string&gt;</code> whose <code>%s</code>/<code>%d</code> format arguments don't match the default value — a runtime <code>IllegalFormatException</code> waiting to happen. Covers Compose Multiplatform, which Android Lint doesn't check.")
                        .applyToComponent { toolTipText = "Compares each locale's format specifiers against values/strings.xml" }
                }
            }

            group("Target Module") {
                row {
                    checkBox("Remember last selected module across extracts")
                        .bindSelected({ settings.rememberLastModule }, { settings.rememberLastModule = it })
                        .comment("When the project has multiple <code>values/strings.xml</code> files, preselect the last one used.")
                        .applyToComponent { toolTipText = "Stores the last picked strings.xml path between extracts" }
                }
            }

            group("strings.xml Behavior") {
                row {
                    checkBox("Sort entries alphabetically after extract")
                        .bindSelected({ settings.sortAfterExtract }, { settings.sortAfterExtract = it })
                        .applyToComponent { toolTipText = "Reorders all <string> entries A→Z after each extract" }
                }
                row {
                    checkBox("Open strings.xml and jump to the new entry")
                        .bindSelected({ settings.openStringsXmlAfterExtract }, { settings.openStringsXmlAfterExtract = it })
                        .applyToComponent { toolTipText = "Switches the editor to the freshly added entry" }
                }
                row {
                    checkBox("Add XML comment with source file:line")
                        .bindSelected({ settings.addSourceComment }, { settings.addSourceComment = it })
                        .applyToComponent { toolTipText = "Adds <!-- from File.kt:42 --> above each new entry" }
                }
                row {
                    checkBox("Trim leading/trailing whitespace from extracted value")
                        .bindSelected({ settings.trimWhitespace }, { settings.trimWhitespace = it })
                        .applyToComponent { toolTipText = "Strips surrounding spaces before writing" }
                }
            }

            group("Exclusion Patterns") {
                row {
                    textArea()
                        .bindText({ settings.excludePatterns }, { settings.excludePatterns = it })
                        .align(AlignX.FILL)
                        .applyToComponent {
                            rows = 4
                            toolTipText = "One Kotlin regex per line. Matching strings are skipped."
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
                    val confirmed = Messages.showYesNoDialog(
                        StringSmithBundle.message("settings.restoreDefaults.confirm"),
                        StringSmithBundle.message("settings.restoreDefaults.title"),
                        Messages.getQuestionIcon()
                    )
                    if (confirmed == Messages.YES) {
                        settings.resetToDefaults()
                        dialogPanel?.reset()
                        refreshPreview()
                    }
                }
            }
        }
        dialogPanel = builder
        sampleField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refreshPreview()
            override fun removeUpdate(e: DocumentEvent) = refreshPreview()
            override fun changedUpdate(e: DocumentEvent) = refreshPreview()
        })
        refreshPreview()
        return builder
    }

    private fun refreshRegexError(area: JTextArea) {
        val invalid = area.text.lines()
            .withIndex()
            .filter { it.value.isNotBlank() }
            .firstNotNullOfOrNull { (i, line) ->
                runCatching { Regex(line) }.exceptionOrNull()?.let { i to it }
            }
        regexErrorLabel.text = invalid?.let { (i, e) -> "Line ${i + 1}: ${e.message ?: "invalid regex"}" } ?: ""
    }

    private fun refreshPreview() {
        // Single source of truth: use the real generator so the preview never drifts from extraction.
        val sample = sampleField.text.ifBlank { DEFAULT_SAMPLE }
        val key = KeyGenerator.suggest(sample, settings.keyPrefix, settings.namingConvention, settings.maxKeyLength)
        previewLabel.text = "→ $key"
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

    private companion object {
        const val DEFAULT_SAMPLE = "Welcome to the app"
    }
}
