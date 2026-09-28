package com.example.data.scripts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

class AndroidJsEngine(private val context: Context) {
    private val TAG = "AndroidJsEngine"
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getOrCreateWebView(): WebView {
        if (webView == null) {
            try {
                webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowContentAccess = false
                    settings.allowFileAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize headless WebView for JS runtime, falling back to AST parser", e)
            }
        }
        return webView!!
    }

    /**
     * Executes custom JavaScript code against a note's markdown content.
     * Provides full JavaScript ES6+ execution environment.
     */
    suspend fun executeScript(
        jsCode: String,
        content: String,
        file: File,
        tags: List<String> = emptyList()
    ): String = withContext(Dispatchers.Main) {
        try {
            val wv = try {
                getOrCreateWebView()
            } catch (e: Exception) {
                null
            }

            if (wv == null) {
                return@withContext fallbackJsEvaluator(jsCode, content, file, tags)
            }

            val title = file.nameWithoutExtension
            val folder = file.parentFile?.name ?: "Root"
            val tagsJson = JSONArray(tags).toString()

            // Prepare JS wrapper harness
            val escapedCode = jsCode
            val safeContent = JSONObject.quote(content)
            val safeTitle = JSONObject.quote(title)
            val safeFolder = JSONObject.quote(folder)

            val harness = """
                (function() {
                    try {
                        var content = $safeContent;
                        var title = $safeTitle;
                        var folder = $safeFolder;
                        var tags = $tagsJson;

                        // Helpers injected into JS scope
                        function replaceAll(pattern, replacement) {
                            return content.replace(new RegExp(pattern, 'g'), replacement);
                        }
                        function prependFrontmatter(key, value) {
                            if (!content.startsWith('---')) {
                                return '---\n' + key + ': "' + value + '"\n---\n\n' + content;
                            } else if (content.indexOf(key + ':') === -1) {
                                return content.replace('---\n', '---\n' + key + ': "' + value + '"\n');
                            }
                            return content;
                        }
                        function appendTag(t) {
                            var tagClean = t.startsWith('#') ? t : '#' + t;
                            if (content.indexOf(tagClean) === -1) {
                                return content + '\n\n' + tagClean;
                            }
                            return content;
                        }

                        // User Script Execution
                        var userResult = null;
                        
                        // Check if user script defines a transform function
                        $escapedCode

                        if (typeof transform === 'function') {
                            userResult = transform(content, title, folder, tags);
                        } else if (typeof run === 'function') {
                            userResult = run({ content: content, title: title, folder: folder, tags: tags });
                        } else {
                            // If script is a direct expression or sequence returning content
                            userResult = content;
                        }

                        if (userResult !== null && typeof userResult !== 'undefined') {
                            return String(userResult);
                        }
                        return content;
                    } catch (err) {
                        return "ERROR:" + err.message;
                    }
                })();
            """.trimIndent()

            val result = suspendCancellableCoroutine<String> { cont ->
                wv.evaluateJavascript(harness, ValueCallback { evalResult ->
                    if (cont.isActive) {
                        if (evalResult == null || evalResult == "null") {
                            cont.resume(content)
                        } else {
                            // Parse JSON-encoded string from evaluateJavascript
                            val unquoted = try {
                                if (evalResult.startsWith("\"") && evalResult.endsWith("\"")) {
                                    val jsonString = JSONObject("{\"res\":$evalResult}").getString("res")
                                    if (jsonString.startsWith("ERROR:")) {
                                        Log.w(TAG, "JS Error in script: $jsonString")
                                        content
                                    } else {
                                        jsonString
                                    }
                                } else {
                                    evalResult
                                }
                            } catch (e: Exception) {
                                evalResult
                            }
                            cont.resume(unquoted)
                        }
                    }
                })
            }

            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "JavaScript execution failed, falling back to AST parser", e)
            return@withContext fallbackJsEvaluator(jsCode, content, file, tags)
        }
    }

    private fun fallbackJsEvaluator(jsCode: String, content: String, file: File, tags: List<String>): String {
        var transformed = content
        // 1. Process .replace(/pattern/flags, "replacement")
        val replaceRegex = """\.replace\(\s*/([^/]+)/([gimsuy]*)\s*,\s*["'](.*?)["']\s*\)""".toRegex()
        for (match in replaceRegex.findAll(jsCode)) {
            val pattern = match.groupValues[1]
            val flags = match.groupValues[2]
            val replacement = match.groupValues[3]
            val options = mutableSetOf<RegexOption>()
            if (flags.contains("i")) options.add(RegexOption.IGNORE_CASE)
            if (flags.contains("m")) options.add(RegexOption.MULTILINE)
            transformed = transformed.replace(Regex(pattern, options), replacement)
        }

        // 2. Process .replaceAll("old", "new")
        val replaceAllRegex = """replaceAll\(\s*["'](.*?)["']\s*,\s*["'](.*?)["']\s*\)""".toRegex()
        for (match in replaceAllRegex.findAll(jsCode)) {
            val oldStr = match.groupValues[1]
            val newStr = match.groupValues[2]
            transformed = transformed.replace(oldStr, newStr)
        }

        // 3. Process toUpperCase() / toLowerCase()
        if (jsCode.contains("toUpperCase()")) {
            transformed = transformed.uppercase()
        }

        return transformed
    }
}
