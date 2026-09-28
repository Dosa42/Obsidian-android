package com.example.data.scripts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class DynamicScriptRule(
    val id: String,
    val name: String,
    val trigger: String, // "on_note_saved", "on_vault_scan", "manual"
    val description: String,
    val filePath: String,
    val isEnabled: Boolean = true,
    val rawJson: String
)

data class ScriptExecutionSummary(
    val scriptName: String,
    val filesExamined: Int,
    val filesModified: Int,
    val details: String,
    val success: Boolean = true
)

class DynamicRuleEngine(private val context: Context? = null) {
    private val TAG = "DynamicRuleEngine"
    private val jsEngine: AndroidJsEngine? = context?.let { AndroidJsEngine(it) }

    fun loadAllScripts(scriptsDir: File): List<DynamicScriptRule> {
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs()
        }
        val scriptFiles = scriptsDir.listFiles { file ->
            file.isFile && (file.extension.equals("json", ignoreCase = true) || file.extension.equals("js", ignoreCase = true))
        } ?: emptyArray()

        val rules = mutableListOf<DynamicScriptRule>()
        for (file in scriptFiles) {
            try {
                val content = file.readText()
                if (file.extension.equals("json", ignoreCase = true)) {
                    val json = JSONObject(content)
                    rules.add(
                        DynamicScriptRule(
                            id = json.optString("id", file.nameWithoutExtension),
                            name = json.optString("name", file.nameWithoutExtension),
                            trigger = json.optString("trigger", "manual"),
                            description = json.optString("description", "Dynamic script from ${file.name}"),
                            filePath = file.absolutePath,
                            isEnabled = json.optBoolean("enabled", true),
                            rawJson = content
                        )
                    )
                } else if (file.extension.equals("js", ignoreCase = true)) {
                    // JavaScript plugin with dynamic trigger metadata in header or default
                    val trigger = if (content.contains("@trigger on_vault_scan")) "on_vault_scan"
                    else if (content.contains("@trigger on_note_saved")) "on_note_saved"
                    else "manual"

                    rules.add(
                        DynamicScriptRule(
                            id = file.nameWithoutExtension,
                            name = file.nameWithoutExtension.replace("_", " ").split(" ")
                                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } },
                            trigger = trigger,
                            description = "Custom JavaScript Engine Plugin: ${file.name}",
                            filePath = file.absolutePath,
                            isEnabled = true,
                            rawJson = content
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading script from ${file.absolutePath}", e)
            }
        }
        return rules
    }

    suspend fun executeRuleOnNote(
        rule: DynamicScriptRule,
        noteFile: File,
        vaultDir: File
    ): String = withContext(Dispatchers.IO) {
        try {
            if (!noteFile.exists()) return@withContext "Note file ${noteFile.name} does not exist."
            val originalContent = noteFile.readText()
            var modifiedContent = originalContent

            if (rule.filePath.endsWith(".json", ignoreCase = true)) {
                val json = JSONObject(rule.rawJson)
                val conditions = json.optJSONArray("conditions")
                if (!evaluateConditions(conditions, originalContent, noteFile)) {
                    return@withContext "Rule [${rule.name}] skipped: Conditions not met for ${noteFile.name}."
                }

                val actions = json.optJSONArray("actions")
                if (actions != null) {
                    for (i in 0 until actions.length()) {
                        val action = actions.getJSONObject(i)
                        modifiedContent = applyAction(action, modifiedContent, noteFile, vaultDir)
                    }
                }
            } else if (rule.filePath.endsWith(".js", ignoreCase = true)) {
                // Execute via Android JavaScript Runtime
                modifiedContent = if (jsEngine != null) {
                    jsEngine.executeScript(rule.rawJson, originalContent, noteFile)
                } else {
                    executeCustomJsTransform(rule.rawJson, originalContent, noteFile)
                }
            }

            if (modifiedContent != originalContent) {
                noteFile.writeText(modifiedContent)
                "✅ Rule [${rule.name}] executed on `${noteFile.name}`: Content mutated successfully."
            } else {
                "ℹ️ Rule [${rule.name}] executed on `${noteFile.name}`: No content modifications required."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing rule ${rule.name} on ${noteFile.name}", e)
            "⚠️ Error executing rule [${rule.name}]: ${e.message}"
        }
    }

    suspend fun executeRuleAcrossVault(
        rule: DynamicScriptRule,
        vaultDir: File
    ): ScriptExecutionSummary = withContext(Dispatchers.IO) {
        val files = vaultDir.walkTopDown()
            .filter { it.isFile && (it.extension.equals("md", ignoreCase = true) || it.extension.equals("txt", ignoreCase = true)) }
            .filter { !it.relativeTo(vaultDir).path.split(File.separator).any { part -> part.startsWith(".") } }
            .toList()

        var modifiedCount = 0
        val logDetails = mutableListOf<String>()

        for (file in files) {
            val original = try { file.readText() } catch (e: Exception) { "" }
            var modified = original

            try {
                if (rule.filePath.endsWith(".json", ignoreCase = true)) {
                    val json = JSONObject(rule.rawJson)
                    val conditions = json.optJSONArray("conditions")
                    if (evaluateConditions(conditions, original, file)) {
                        val actions = json.optJSONArray("actions")
                        if (actions != null) {
                            for (i in 0 until actions.length()) {
                                modified = applyAction(actions.getJSONObject(i), modified, file, vaultDir)
                            }
                        }
                    }
                } else if (rule.filePath.endsWith(".js", ignoreCase = true)) {
                    modified = if (jsEngine != null) {
                        jsEngine.executeScript(rule.rawJson, original, file)
                    } else {
                        executeCustomJsTransform(rule.rawJson, original, file)
                    }
                }

                if (modified != original) {
                    file.writeText(modified)
                    modifiedCount++
                    logDetails.add("Updated [[${file.nameWithoutExtension}]]")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying rule ${rule.name} to ${file.name}", e)
            }
        }

        ScriptExecutionSummary(
            scriptName = rule.name,
            filesExamined = files.size,
            filesModified = modifiedCount,
            details = if (modifiedCount > 0) logDetails.take(5).joinToString(", ") + if (logDetails.size > 5) " and ${logDetails.size - 5} more" else "" else "No files required mutation.",
            success = true
        )
    }

    suspend fun executeTrigger(trigger: String, vaultDir: File, scriptsDir: File): List<ScriptExecutionSummary> = withContext(Dispatchers.IO) {
        val allRules = loadAllScripts(scriptsDir)
        val matching = allRules.filter { it.trigger.equals(trigger, ignoreCase = true) && it.isEnabled }
        val summaries = mutableListOf<ScriptExecutionSummary>()
        for (rule in matching) {
            val summary = executeRuleAcrossVault(rule, vaultDir)
            summaries.add(summary)
        }
        summaries
    }

    private fun evaluateConditions(conditions: JSONArray?, content: String, file: File): Boolean {
        if (conditions == null || conditions.length() == 0) return true
        for (i in 0 until conditions.length()) {
            val cond = conditions.getJSONObject(i)
            val type = cond.optString("type", cond.optString("condition", ""))
            when (type) {
                "contains_regex" -> {
                    val pattern = cond.optString("pattern", cond.optString("contains_regex", ""))
                    if (pattern.isNotBlank() && !Regex(pattern).containsMatchIn(content)) {
                        return false
                    }
                }
                "contains_tag" -> {
                    val tag = cond.optString("tag")
                    if (tag.isNotBlank() && !content.contains("#$tag", ignoreCase = true)) {
                        return false
                    }
                }
                "min_word_count" -> {
                    val min = cond.optInt("value", 0)
                    val words = content.split("\\s+".toRegex()).count { it.isNotBlank() }
                    if (words < min) return false
                }
                "folder_equals" -> {
                    val folder = cond.optString("folder")
                    if (file.parentFile?.name != folder) return false
                }
                "not_contains_regex" -> {
                    val pattern = cond.optString("pattern")
                    if (pattern.isNotBlank() && Regex(pattern).containsMatchIn(content)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun applyAction(action: JSONObject, content: String, file: File, vaultDir: File): String {
        val type = action.optString("type", action.optString("action", ""))
        var result = content

        when (type) {
            "prepend_frontmatter" -> {
                val key = action.optString("key", "category")
                val value = action.optString("value", "generic")
                if (!result.startsWith("---")) {
                    result = "---\n$key: \"$value\"\n---\n\n$result"
                } else if (!result.contains("$key:")) {
                    result = result.replaceFirst("---\n", "---\n$key: \"$value\"\n")
                }
            }
            "append_tag" -> {
                val tag = action.optString("tag")
                val cleanTag = if (tag.startsWith("#")) tag else "#$tag"
                if (!result.contains(cleanTag)) {
                    result = if (result.contains("\n")) {
                        val lines = result.lines().toMutableList()
                        val headerIdx = lines.indexOfFirst { it.startsWith("# ") }
                        if (headerIdx != -1 && headerIdx + 1 < lines.size) {
                            lines.add(headerIdx + 1, "\n$cleanTag")
                        } else {
                            lines.add("$cleanTag")
                        }
                        lines.joinToString("\n")
                    } else {
                        "$result\n\n$cleanTag"
                    }
                }
            }
            "auto_link" -> {
                val targets = action.optJSONArray("targets")
                if (targets != null) {
                    val links = mutableListOf<String>()
                    for (i in 0 until targets.length()) {
                        val target = targets.getString(i)
                        if (!result.contains("[[$target]]", ignoreCase = true)) {
                            links.add("[[$target]]")
                        }
                    }
                    if (links.isNotEmpty()) {
                        result += "\n\n**See Also:** " + links.joinToString(", ")
                    }
                }
            }
            "replace_regex" -> {
                val pattern = action.optString("pattern")
                val replacement = action.optString("replacement", "")
                if (pattern.isNotBlank()) {
                    result = result.replace(Regex(pattern), replacement)
                }
            }
            "auto_wikilink_keywords" -> {
                val keywords = action.optJSONArray("keywords")
                if (keywords != null) {
                    for (i in 0 until keywords.length()) {
                        val kw = keywords.getString(i)
                        if (kw.equals(file.nameWithoutExtension, ignoreCase = true)) continue
                        if (result.contains(kw, ignoreCase = true) && !result.contains("[[$kw]]", ignoreCase = true)) {
                            val kwRegex = "\\b(${Pattern.quote(kw)})\\b".toRegex(RegexOption.IGNORE_CASE)
                            var replaced = false
                            result = kwRegex.replace(result) { matchResult ->
                                if (!replaced) {
                                    replaced = true
                                    "[[${matchResult.value}]]"
                                } else {
                                    matchResult.value
                                }
                            }
                        }
                    }
                }
            }
            "ensure_heading" -> {
                val level = action.optInt("level", 1)
                val title = file.nameWithoutExtension
                val prefix = "#".repeat(level) + " "
                if (!result.trimStart().startsWith(prefix)) {
                    result = "$prefix$title\n\n$result"
                }
            }
        }
        return result
    }

    private fun executeCustomJsTransform(scriptCode: String, content: String, file: File): String {
        var transformed = content
        if (scriptCode.contains("replace(") || scriptCode.contains(".replace")) {
            val replaceRegex = """\.replace\(\s*/([^/]+)/([gimsuy]*)\s*,\s*["'](.*?)["']\s*\)""".toRegex()
            for (match in replaceRegex.findAll(scriptCode)) {
                val pattern = match.groupValues[1]
                val flags = match.groupValues[2]
                val replacement = match.groupValues[3]
                val options = mutableSetOf<RegexOption>()
                if (flags.contains("i")) options.add(RegexOption.IGNORE_CASE)
                if (flags.contains("m")) options.add(RegexOption.MULTILINE)
                transformed = transformed.replace(Regex(pattern, options), replacement)
            }
        }
        return transformed
    }

    fun initializeStarterScripts(scriptsDir: File) {
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs()
        }

        val autoTagger = File(scriptsDir, "auto_tagger.json")
        if (!autoTagger.exists()) {
            autoTagger.writeText(
                """
                {
                  "id": "auto_tagger",
                  "name": "Auto Meeting & Task Tagger",
                  "trigger": "on_note_saved",
                  "description": "Automatically categorizes notes containing meeting or agenda keywords",
                  "conditions": [
                    { "type": "contains_regex", "pattern": "(?i)\\b(meeting|discussion|agenda|action items)\\b" }
                  ],
                  "actions": [
                    { "type": "prepend_frontmatter", "key": "category", "value": "meeting" },
                    { "type": "append_tag", "tag": "#meeting" },
                    { "type": "auto_link", "targets": ["Meetings Index", "Action Items"] }
                  ]
                }
                """.trimIndent()
            )
        }

        val linkRules = File(scriptsDir, "concept_auto_linker.json")
        if (!linkRules.exists()) {
            linkRules.writeText(
                """
                {
                  "id": "concept_auto_linker",
                  "name": "Concept Auto-Linker",
                  "trigger": "on_vault_scan",
                  "description": "Automatically wikilinks concept keywords across all vault notes during sync",
                  "conditions": [],
                  "actions": [
                    { "type": "auto_wikilink_keywords", "keywords": ["Knowledge Graphs", "Wikilinks", "Obsidian Vault", "Venice AI"] }
                  ]
                }
                """.trimIndent()
            )
        }

        val taskNormalizer = File(scriptsDir, "task_normalizer.json")
        if (!taskNormalizer.exists()) {
            taskNormalizer.writeText(
                """
                {
                  "id": "task_normalizer",
                  "name": "Markdown Task Normalizer",
                  "trigger": "on_note_saved",
                  "description": "Normalizes custom markdown checkboxes to standard format",
                  "conditions": [
                    { "type": "contains_regex", "pattern": "\\[ \\]" }
                  ],
                  "actions": [
                    { "type": "append_tag", "tag": "#tasks" }
                  ]
                }
                """.trimIndent()
            )
        }

        val jsScript = File(scriptsDir, "custom_markdown_formatter.js")
        if (!jsScript.exists()) {
            jsScript.writeText(
                """
                // @trigger on_note_saved
                // JavaScript Engine Plugin for Obsidian Vault
                // This function is evaluated dynamically using Android's headless JS Runtime.
                
                function transform(content, title, folder, tags) {
                    var modified = content;
                    
                    // Normalize multiple trailing newlines
                    modified = modified.replace(/\n{3,}/g, '\n\n');
                    
                    // Clean trailing whitespace on every line
                    modified = modified.split('\n').map(function(line) {
                        return line.trimEnd();
                    }).join('\n');
                    
                    return modified;
                }
                """.trimIndent()
            )
        }
    }
}
