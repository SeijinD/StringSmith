package com.seijind.stringsmith.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.seijind.stringsmith.StringSmithBundle
import com.seijind.stringsmith.extract.KeyGenerator
import com.seijind.stringsmith.extract.LocaleUi
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

class StringSmithConfigurable : Configurable {

    private var dialogPanel: DialogPanel? = null
    private val settings get() = StringSmithSettings.getInstance()
    private val sampleField = JBTextField(DEFAULT_SAMPLE)
    private val previewLabel = JBLabel()
    private val regexErrorLabel = JBLabel().apply { foreground = JBColor.RED }
    private var prefixField: JBTextField? = null
    private var namingCombo: ComboBox<NamingConvention>? = null
    private var maxKeyLengthField: JBTextField? = null
    private var excludePatternsArea: JTextArea? = null
    private val previewListener = LocaleUi.changeListener { refreshPreview() }

    override fun getDisplayName(): String = "StringSmith - Android Strings Toolkit"

    override fun createComponent(): JComponent {
        val builder = panel {
            group("Key Generation") {
                row("Prefix:") {
                    textField()
                        .bindText({ settings.keyPrefix }, { settings.keyPrefix = it })
                        .applyToComponent { prefixField = this; document.addDocumentListener(previewListener) }
                        .comment("Prepended to every suggested key. Leave empty to disable.")
                        .align(AlignX.FILL)
                        .applyToComponent { toolTipText = "Example: \"app\" → app_welcome_screen" }
                }
                row("Naming:") {
                    comboBox(NamingConvention.entries.toList())
                        .bindItem(
                            { settings.namingConvention },
                            { v -> if (v != null) settings.namingConvention = v }
                        )
                        .applyToComponent { namingCombo = this; addActionListener { refreshPreview() } }
                        .applyToComponent { toolTipText = "Case style for generated keys" }
                }
                row("Max key length:") {
                    intTextField(1..200)
                        .bindIntText({ settings.maxKeyLength }, { settings.maxKeyLength = it })
                        .applyToComponent { maxKeyLengthField = this; document.addDocumentListener(previewListener) }
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

            group("Extraction Behavior") {
                row {
                    checkBox("Auto-include locale variants by default")
                        .bindSelected({ settings.autoIncludeLocales }, { settings.autoIncludeLocales = it })
                        .comment("Initial checkbox state for <code>values-*/strings.xml</code> rows in the extract dialog.")
                        .applyToComponent { toolTipText = "When on, all locale variant rows start checked; when off, they start unchecked" }
                }
                row {
                    checkBox("Detect template expressions and extract as %1\$s placeholders")
                        .bindSelected({ settings.detectFormatArgs }, { settings.detectFormatArgs = it })
                        .comment("Converts <code>\"Hello \$name\"</code> to <code>\"Hello %1\$s\"</code> in strings.xml and passes the original expression as a format argument.")
                        .applyToComponent { toolTipText = "When off, strings with template expressions are skipped entirely" }
                }
                row {
                    checkBox("Exclude strings inside @Preview composables")
                        .bindSelected({ settings.excludePreviewComposables }, { settings.excludePreviewComposables = it })
                        .comment("Skip strings inside functions annotated with <code>@Preview</code> (typically dummy data).")
                        .applyToComponent { toolTipText = "Recommended on; previews usually contain sample text not meant for translation" }
                }
                row {
                    checkBox("Remember last selected module across extracts")
                        .bindSelected({ settings.rememberLastModule }, { settings.rememberLastModule = it })
                        .comment("When the project has multiple <code>values/strings.xml</code> files, preselect the last one used.")
                        .applyToComponent { toolTipText = "Stores the last picked strings.xml path between extracts" }
                }
            }

            group("Inspections") {
                row {
                    comment("Turn the StringSmith inspections on or off in <b>Settings | Editor | Inspections | StringSmith</b>. Hardcoded strings are off by default.")
                }
                row {
                    checkBox("Ignore logging strings")
                        .bindSelected({ settings.ignoreLoggingStrings }, { settings.ignoreLoggingStrings = it })
                        .comment("Don't flag literals passed to <code>Log.*</code>, <code>Timber.*</code>, <code>println</code>, <code>require</code>/<code>check</code>. Annotation arguments and <code>const</code> values are always skipped.")
                        .applyToComponent { toolTipText = "Logging and assertion messages are usually not user-facing text" }
                }
            }

            group("strings.xml Output") {
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

            // Advanced, project-specific tuning, collapsed by default to keep the page short.
            collapsibleGroup("Compose & Multiplatform") {
                row {
                    textArea()
                        .bindText({ settings.customComposableLambdaFunctions }, { settings.customComposableLambdaFunctions = it })
                        .label("Custom @Composable wrapper functions:", LabelPosition.TOP)
                        .align(AlignX.FILL)
                        .applyToComponent {
                            rows = 3
                            toolTipText = "Separate names with a newline, comma or space. Strings inside their trailing lambda use stringResource."
                        }
                        .comment("Function names whose trailing lambda is a <code>@Composable</code> scope (e.g. <code>screenViewComposable</code>). Strings inside them use <code>stringResource</code> instead of <code>getString</code>. Separate names with a newline, comma, or space.")
                }
                row {
                    textField()
                        .label("Res package override:", LabelPosition.TOP)
                        .bindText({ settings.cmpResPackageOverride }, { settings.cmpResPackageOverride = it })
                        .align(AlignX.FILL)
                        .applyToComponent {
                            toolTipText = "e.g. com.example.app.generated.resources (leave blank to auto-detect)"
                        }
                        .comment(
                            "Generated <code>Res</code> class package for Compose Multiplatform " +
                                "(<code>composeResources</code>) targets. Blank = auto-detect: gradle " +
                                "<code>packageOfResClass</code> → existing <code>*.generated.resources.Res</code> " +
                                "imports → derived from module package."
                        )
                }
            }.expanded = false

            collapsibleGroup("Exclusion Patterns") {
                row {
                    textArea()
                        .bindText({ settings.excludePatterns }, { settings.excludePatterns = it })
                        .align(AlignX.FILL)
                        .applyToComponent {
                            rows = 4
                            toolTipText = "One Kotlin regex per line. Matching strings are skipped."
                            excludePatternsArea = this
                            document.addDocumentListener(LocaleUi.changeListener { refreshRegexError() })
                        }
                        .comment("One regex per line. Strings matching any pattern are skipped. Defaults skip CONSTANTS and URLs.")
                }
                row {
                    cell(regexErrorLabel)
                }
            }.expanded = false

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
        sampleField.document.addDocumentListener(previewListener)
        refreshPreview()
        refreshRegexError()
        return builder
    }

    private fun regexError(): String? {
        val text = excludePatternsArea?.text ?: return null
        val invalid = text.lines()
            .withIndex()
            .filter { it.value.isNotBlank() }
            .firstNotNullOfOrNull { (i, line) ->
                try {
                    Regex(line)
                    null
                } catch (e: IllegalArgumentException) {
                    i to e
                }
            }
        return invalid?.let { (i, e) -> "Exclusion pattern line ${i + 1}: ${e.message ?: "invalid regex"}" }
    }

    private fun refreshRegexError() {
        regexErrorLabel.text = regexError() ?: ""
    }

    // Reads the unsaved fields so the preview follows edits before Apply.
    private fun refreshPreview() {
        val sample = sampleField.text.ifBlank { DEFAULT_SAMPLE }
        val prefix = prefixField?.text ?: settings.keyPrefix
        val naming = namingCombo?.selectedItem as? NamingConvention ?: settings.namingConvention
        val maxLength = maxKeyLengthField?.text?.toIntOrNull() ?: settings.maxKeyLength
        previewLabel.text = "→ ${KeyGenerator.suggest(sample, prefix, naming, maxLength)}"
    }

    override fun isModified(): Boolean = dialogPanel?.isModified() == true

    override fun apply() {
        val panel = dialogPanel ?: return
        panel.validateAll().firstOrNull()?.let { throw ConfigurationException(it.message) }
        regexError()?.let { throw ConfigurationException(it) }
        panel.apply()
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
