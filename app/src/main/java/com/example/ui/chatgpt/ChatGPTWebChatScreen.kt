package com.example.ui.chatgpt

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.auth.ChatGPTAuthManager
import com.example.ui.theme.ObsidianBackground

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatGPTWebChatScreen(
    chatGPTAuthManager: ChatGPTAuthManager,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val htmlData = remember { getChatGptWebHtml() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 CodexCLI/0.155.1"
                    }

                    val bridge = AndroidBridge(
                        context = ctx,
                        chatGPTAuthManager = chatGPTAuthManager,
                        scope = coroutineScope,
                        webViewProvider = { webViewInstance }
                    )
                    addJavascriptInterface(bridge, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: ""
                            if (url.startsWith("http://localhost:1455/auth/callback")) {
                                view?.evaluateJavascript(
                                    "if (window.onOAuthCallbackReceived) { window.onOAuthCallbackReceived('$url'); }",
                                    null
                                )
                                return true
                            } else if (url.startsWith("https://auth.openai.com") || url.startsWith("https://chatgpt.com")) {
                                return false
                            }
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            android.util.Log.d("ChatGPTWebView", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                            return true
                        }
                    }

                    loadDataWithBaseURL(
                        "https://chatgpt-chatbox.local/",
                        htmlData,
                        "text/html",
                        "UTF-8",
                        null
                    )

                    webViewInstance = this
                }
            },
            update = {
                webViewInstance = it
            }
        )
    }
}

private fun getChatGptWebHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>ChatGPT PKCE Chatbox</title>
    <!-- React & ReactDOM CDN -->
    <script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
    <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
    <style>
        :root {
            --bg-primary: #121214;
            --bg-secondary: #18181b;
            --bg-tertiary: #27272a;
            --bg-hover: #3f3f46;
            --text-primary: #f4f4f5;
            --text-secondary: #a1a1aa;
            --text-muted: #71717a;
            --accent-primary: #10b981;
            --accent-cyan: #06b6d4;
            --accent-purple: #8b5cf6;
            --accent-danger: #ef4444;
            --border-color: #27272a;
            --border-focus: #3f3f46;
            --radius-sm: 6px;
            --radius-md: 10px;
            --radius-lg: 16px;
            --font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
        }

        [data-theme="light"] {
            --bg-primary: #ffffff;
            --bg-secondary: #f4f4f5;
            --bg-tertiary: #e4e4e7;
            --bg-hover: #d4d4d8;
            --text-primary: #18181b;
            --text-secondary: #52525b;
            --text-muted: #a1a1aa;
            --border-color: #e4e4e7;
            --border-focus: #d4d4d8;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            -webkit-tap-highlight-color: transparent;
        }

        body, html {
            height: 100%;
            width: 100%;
            font-family: var(--font-family);
            background-color: var(--bg-primary);
            color: var(--text-primary);
            overflow: hidden;
        }

        .app-container {
            display: flex;
            height: 100vh;
            width: 100vw;
            position: relative;
        }

        /* Sidebar */
        .side-drawer {
            width: 280px;
            background: var(--bg-secondary);
            border-right: 1px solid var(--border-color);
            display: flex;
            flex-direction: column;
            transition: transform 0.25s ease;
            z-index: 50;
        }

        @media (max-width: 768px) {
            .side-drawer {
                position: absolute;
                top: 0;
                bottom: 0;
                left: 0;
                transform: translateX(-100%);
            }
            .side-drawer.open {
                transform: translateX(0);
            }
        }

        .drawer-overlay {
            position: absolute;
            inset: 0;
            background: rgba(0,0,0,0.5);
            z-index: 40;
        }

        .drawer-header {
            padding: 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid var(--border-color);
        }

        .app-brand {
            display: flex;
            align-items: center;
            gap: 8px;
            font-weight: 700;
            font-size: 16px;
        }

        .brand-icon {
            color: var(--accent-cyan);
            font-size: 18px;
        }

        .brand-badge {
            font-size: 10px;
            background: rgba(6, 182, 212, 0.15);
            color: var(--accent-cyan);
            padding: 2px 6px;
            border-radius: 999px;
            font-weight: bold;
        }

        .drawer-actions {
            padding: 12px 16px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        .btn-new-chat {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            background: var(--accent-primary);
            color: #09090b;
            font-weight: 600;
            border: none;
            padding: 10px 14px;
            border-radius: var(--radius-md);
            cursor: pointer;
            font-size: 14px;
            transition: opacity 0.2s;
        }
        .btn-new-chat:hover { opacity: 0.9; }

        .search-container {
            display: flex;
            align-items: center;
            background: var(--bg-tertiary);
            border-radius: var(--radius-md);
            padding: 6px 10px;
            border: 1px solid var(--border-color);
        }
        .search-icon { font-size: 12px; margin-right: 6px; }
        .search-input {
            background: transparent;
            border: none;
            outline: none;
            color: var(--text-primary);
            font-size: 13px;
            width: 100%;
        }

        .sessions-list {
            flex: 1;
            overflow-y: auto;
            padding: 8px;
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .session-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 10px 12px;
            border-radius: var(--radius-md);
            cursor: pointer;
            transition: background 0.15s;
        }
        .session-item:hover { background: var(--bg-tertiary); }
        .session-item.active { background: var(--bg-tertiary); border-left: 3px solid var(--accent-cyan); }

        .session-info { flex: 1; min-width: 0; }
        .session-title {
            font-size: 13px;
            font-weight: 500;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .session-meta { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

        .session-item-actions {
            display: none;
            gap: 4px;
        }
        .session-item:hover .session-item-actions,
        .session-item.active .session-item-actions {
            display: flex;
        }

        .session-action-btn {
            background: transparent;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            font-size: 12px;
            padding: 4px;
            border-radius: 4px;
        }
        .session-action-btn:hover { color: var(--text-primary); background: var(--bg-hover); }
        .session-action-btn.delete:hover { color: var(--accent-danger); }

        .drawer-footer {
            padding: 12px 16px;
            border-top: 1px solid var(--border-color);
            display: flex;
            justify-content: space-around;
            align-items: center;
        }

        .btn-icon {
            background: transparent;
            border: none;
            color: var(--text-secondary);
            font-size: 16px;
            cursor: pointer;
            padding: 8px;
            border-radius: var(--radius-sm);
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .btn-icon:hover { background: var(--bg-tertiary); color: var(--text-primary); }

        /* Main Content */
        .chat-main {
            flex: 1;
            display: flex;
            flex-direction: column;
            background: var(--bg-primary);
            height: 100%;
            min-width: 0;
        }

        .chat-header {
            padding: 10px 16px;
            background: var(--bg-secondary);
            border-bottom: 1px solid var(--border-color);
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .header-left, .header-right {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .model-select {
            background: var(--bg-tertiary);
            color: var(--text-primary);
            border: 1px solid var(--border-color);
            padding: 6px 10px;
            border-radius: var(--radius-md);
            font-size: 13px;
            outline: none;
            font-weight: 500;
        }

        .btn-system-prompt {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
            color: var(--text-secondary);
            padding: 6px 12px;
            border-radius: var(--radius-md);
            font-size: 12px;
            font-weight: 500;
            cursor: pointer;
        }
        .btn-system-prompt.active {
            border-color: var(--accent-purple);
            color: var(--accent-purple);
            background: rgba(139, 92, 246, 0.1);
        }

        .status-badge {
            display: flex;
            align-items: center;
            gap: 6px;
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
            padding: 6px 12px;
            border-radius: 999px;
            font-size: 12px;
            color: var(--text-primary);
        }

        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--accent-primary);
        }
        .status-dot.offline { background: var(--text-muted); }
        .status-dot.pulsing {
            animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }

        /* Messages */
        .messages-container {
            flex: 1;
            overflow-y: auto;
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .empty-chat {
            margin: auto;
            text-align: center;
            max-width: 480px;
            padding: 32px 16px;
        }
        .empty-sparkle { font-size: 32px; color: var(--accent-cyan); margin-bottom: 12px; }
        .empty-title { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
        .empty-desc { font-size: 13px; color: var(--text-secondary); line-height: 1.5; margin-bottom: 24px; }

        .quick-prompts {
            display: flex;
            flex-direction: column;
            gap: 8px;
            text-align: left;
        }
        .quick-prompt-btn {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            padding: 10px 14px;
            border-radius: var(--radius-md);
            color: var(--text-primary);
            font-size: 13px;
            cursor: pointer;
            transition: border-color 0.2s, background 0.2s;
        }
        .quick-prompt-btn:hover { border-color: var(--accent-cyan); background: var(--bg-tertiary); }

        .message-row {
            display: flex;
            width: 100%;
        }
        .message-row.user { justify-content: flex-end; }
        .message-row.assistant { justify-content: flex-start; }

        .message-bubble {
            max-width: 88%;
            padding: 12px 16px;
            border-radius: var(--radius-lg);
            position: relative;
        }
        .message-row.user .message-bubble {
            background: var(--accent-purple);
            color: #ffffff;
            border-bottom-right-radius: 4px;
        }
        .message-row.assistant .message-bubble {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            color: var(--text-primary);
            border-bottom-left-radius: 4px;
        }

        .message-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 11px;
            opacity: 0.8;
            margin-bottom: 6px;
            gap: 12px;
        }
        .message-sender { font-weight: 600; }

        .streaming-status-tag {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 11px;
            color: var(--accent-cyan);
            margin-bottom: 6px;
        }

        .message-body {
            font-size: 14px;
            line-height: 1.6;
            word-break: break-word;
        }

        .code-block-wrapper {
            background: #09090b;
            border: 1px solid #27272a;
            border-radius: var(--radius-md);
            margin: 10px 0;
            overflow: hidden;
        }
        .code-header {
            display: flex;
            justify-content: space-between;
            padding: 6px 12px;
            background: #18181b;
            font-size: 11px;
            color: var(--text-muted);
            border-bottom: 1px solid #27272a;
        }
        .btn-copy-code {
            background: transparent;
            border: none;
            color: var(--text-secondary);
            cursor: pointer;
            font-size: 11px;
        }
        .code-block-wrapper pre {
            padding: 12px;
            overflow-x: auto;
            font-family: monospace;
            font-size: 13px;
        }

        .message-footer-actions {
            display: flex;
            gap: 8px;
            margin-top: 8px;
        }
        .btn-msg-action {
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 11px;
            cursor: pointer;
            padding: 2px 6px;
            border-radius: 4px;
        }
        .btn-msg-action:hover { background: var(--bg-tertiary); color: var(--text-primary); }

        .streaming-cursor {
            display: inline-block;
            width: 6px;
            height: 14px;
            background: var(--accent-cyan);
            margin-left: 4px;
            vertical-align: middle;
            animation: blink 1s infinite;
        }
        @keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

        /* Composer */
        .composer-area {
            padding: 12px 16px;
            background: var(--bg-secondary);
            border-top: 1px solid var(--border-color);
        }

        .composer-box {
            display: flex;
            align-items: flex-end;
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-lg);
            padding: 8px 12px;
            gap: 8px;
        }
        .composer-box:focus-within { border-color: var(--accent-cyan); }

        .composer-textarea {
            flex: 1;
            background: transparent;
            border: none;
            outline: none;
            color: var(--text-primary);
            font-size: 14px;
            font-family: inherit;
            resize: none;
            max-height: 160px;
        }

        .btn-send, .btn-stop {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            border: none;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-weight: bold;
            font-size: 16px;
        }
        .btn-send { background: var(--accent-primary); color: #09090b; }
        .btn-send:disabled { background: var(--bg-hover); color: var(--text-muted); cursor: not-allowed; }
        .btn-stop { background: var(--accent-danger); color: #ffffff; }

        .composer-hint {
            text-align: center;
            font-size: 10px;
            color: var(--text-muted);
            margin-top: 6px;
        }

        /* Modals */
        .modal-backdrop {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.6);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 100;
            padding: 16px;
        }
        .modal-content {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-lg);
            width: 100%;
            max-width: 480px;
            display: flex;
            flex-direction: column;
            max-height: 90vh;
            box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5);
        }
        .modal-header {
            padding: 14px 16px;
            border-bottom: 1px solid var(--border-color);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .modal-title { font-weight: 700; font-size: 15px; }
        .modal-body { padding: 16px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; }
        .modal-footer { padding: 12px 16px; border-top: 1px solid var(--border-color); display: flex; justify-content: flex-end; gap: 8px; }

        .form-group { display: flex; flex-direction: column; gap: 6px; }
        .form-label { font-size: 12px; font-weight: 600; color: var(--text-secondary); }
        .form-help { font-size: 11px; color: var(--text-muted); }
        .form-input, .form-textarea {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
            color: var(--text-primary);
            padding: 8px 12px;
            border-radius: var(--radius-md);
            font-size: 13px;
            outline: none;
            font-family: inherit;
        }
        .preset-chips { display: flex; flex-wrap: wrap; gap: 6px; }
        .preset-chip {
            background: var(--bg-tertiary);
            border: 1px solid var(--border-color);
            color: var(--text-secondary);
            font-size: 11px;
            padding: 4px 10px;
            border-radius: 999px;
            cursor: pointer;
        }
        .preset-chip.active {
            background: rgba(139, 92, 246, 0.2);
            border-color: var(--accent-purple);
            color: var(--accent-purple);
        }
        .btn-primary {
            background: var(--accent-primary);
            color: #09090b;
            border: none;
            padding: 8px 16px;
            border-radius: var(--radius-md);
            font-weight: 600;
            font-size: 13px;
            cursor: pointer;
        }
        .btn-secondary {
            background: var(--bg-tertiary);
            color: var(--text-primary);
            border: 1px solid var(--border-color);
            padding: 8px 16px;
            border-radius: var(--radius-md);
            font-size: 13px;
            cursor: pointer;
        }
    </style>
</head>
<body>
    <div id="root"></div>

    <script>
        // IndexedDB Lightweight Storage Bridge for Browser / WebView
        class LocalChatDb {
            constructor() {
                this.dbName = 'ChatGptPKCEDatabase';
                this.dbVersion = 1;
                this.db = null;
            }

            async open() {
                if (this.db) return this.db;
                return new Promise((resolve, reject) => {
                    const req = indexedDB.open(this.dbName, this.dbVersion);
                    req.onupgradeneeded = (e) => {
                        const db = e.target.result;
                        if (!db.objectStoreNames.contains('sessions')) {
                            db.createObjectStore('sessions', { keyPath: 'id' });
                        }
                        if (!db.objectStoreNames.contains('messages')) {
                            const msgStore = db.createObjectStore('messages', { keyPath: 'id' });
                            msgStore.createIndex('sessionId', 'sessionId', { unique: false });
                        }
                        if (!db.objectStoreNames.contains('settings')) {
                            db.createObjectStore('settings', { keyPath: 'key' });
                        }
                    };
                    req.onsuccess = () => {
                        this.db = req.result;
                        resolve(this.db);
                    };
                    req.onerror = () => reject(req.error);
                });
            }

            async getSessions() {
                const db = await this.open();
                return new Promise((resolve) => {
                    const tx = db.transaction('sessions', 'readonly');
                    const store = tx.objectStore('sessions');
                    const req = store.getAll();
                    req.onsuccess = () => resolve(req.result.sort((a,b) => b.updatedAt - a.updatedAt));
                    req.onerror = () => resolve([]);
                });
            }

            async saveSession(session) {
                const db = await this.open();
                const tx = db.transaction('sessions', 'readwrite');
                tx.objectStore('sessions').put(session);
            }

            async deleteSession(id) {
                const db = await this.open();
                const tx = db.transaction(['sessions', 'messages'], 'readwrite');
                tx.objectStore('sessions').delete(id);
                const msgStore = tx.objectStore('messages');
                const idx = msgStore.index('sessionId');
                const req = idx.getAllKeys(id);
                req.onsuccess = () => {
                    req.result.forEach((k) => msgStore.delete(k));
                };
            }

            async getMessages(sessionId) {
                const db = await this.open();
                return new Promise((resolve) => {
                    const tx = db.transaction('messages', 'readonly');
                    const idx = tx.objectStore('messages').index('sessionId');
                    const req = idx.getAll(sessionId);
                    req.onsuccess = () => resolve(req.result.sort((a,b) => a.createdAt - b.createdAt));
                    req.onerror = () => resolve([]);
                });
            }

            async saveMessage(msg) {
                const db = await this.open();
                const tx = db.transaction('messages', 'readwrite');
                tx.objectStore('messages').put(msg);
            }

            async updateMessage(id, updates) {
                const db = await this.open();
                const tx = db.transaction('messages', 'readwrite');
                const store = tx.objectStore('messages');
                const req = store.get(id);
                req.onsuccess = () => {
                    if (req.result) {
                        store.put({ ...req.result, ...updates });
                    }
                };
            }

            async getSetting(key, defValue) {
                const db = await this.open();
                return new Promise((resolve) => {
                    const tx = db.transaction('settings', 'readonly');
                    const req = tx.objectStore('settings').get(key);
                    req.onsuccess = () => resolve(req.result ? req.result.value : defValue);
                    req.onerror = () => resolve(defValue);
                });
            }

            async setSetting(key, value) {
                const db = await this.open();
                const tx = db.transaction('settings', 'readwrite');
                tx.objectStore('settings').put({ key, value });
            }
        }

        window.chatDb = new LocalChatDb();
    </script>

    <!-- ChatGPT PKCE Auth Core Logic -->
    <script>
        const AUTH_URL = 'https://auth.openai.com/oauth/authorize';
        const TOKEN_URL = 'https://auth.openai.com/oauth/token';
        const API_BASE = 'https://chatgpt.com/backend-api/codex';
        const REDIRECT_URI = 'http://localhost:1455/auth/callback';
        const PUBLIC_CLIENT_ID = 'app_EMoamEEZ73f0CkXaXp7hrann';
        const CODEX_VERSION = '0.155.1';
        const SCOPE = 'openid profile email offline_access api.connectors.read api.connectors.invoke';

        function base64url(bytes) {
            let binary = '';
            const len = bytes.byteLength;
            for (let i = 0; i < len; i++) {
                binary += String.fromCharCode(bytes[i]);
            }
            return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        }

        function randomString(length = 32) {
            const bytes = new Uint8Array(length);
            crypto.getRandomValues(bytes);
            return base64url(bytes);
        }

        async function pkceChallenge(verifier) {
            const encoder = new TextEncoder();
            const data = encoder.encode(verifier);
            const hash = await crypto.subtle.digest('SHA-256', data);
            return base64url(new Uint8Array(hash));
        }

        function parseJwt(token) {
            if (!token) return {};
            try {
                let base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
                while (base64.length % 4) base64 += '=';
                const jsonStr = decodeURIComponent(
                    atob(base64).split('').map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')
                );
                return JSON.parse(jsonStr);
            } catch (e) {
                return {};
            }
        }

        async function sendHttpRequest(url, { method = 'GET', headers = {}, body = null }) {
            if (window.AndroidBridge && typeof window.AndroidBridge.nativeHttpRequest === 'function') {
                return new Promise((resolve, reject) => {
                    const requestId = 'req_' + Date.now() + '_' + Math.random().toString(36).substring(2, 8);
                    window['__http_cb_' + requestId] = (statusCode, responseText, errorMsg) => {
                        delete window['__http_cb_' + requestId];
                        if (errorMsg) {
                            reject(new Error(errorMsg));
                        } else if (statusCode < 200 || statusCode >= 300) {
                            let errDetail = responseText;
                            try {
                                const parsed = JSON.parse(responseText);
                                errDetail = parsed.error?.message || parsed.error_description || parsed.message || responseText;
                            } catch (_) {}
                            reject(new Error(`HTTP ${'$'}{statusCode}: ${'$'}{errDetail}`));
                        } else {
                            try {
                                resolve(JSON.parse(responseText));
                            } catch (_) {
                                resolve(responseText);
                            }
                        }
                    };
                    window.AndroidBridge.nativeHttpRequest(
                        requestId,
                        url,
                        method,
                        JSON.stringify(headers),
                        body ? (typeof body === 'string' ? body : JSON.stringify(body)) : null
                    );
                });
            }

            const resp = await fetch(url, {
                method,
                headers,
                body: body ? (typeof body === 'string' ? body : JSON.stringify(body)) : undefined,
            });
            const text = await resp.text();
            if (!resp.ok) {
                let errDetail = text;
                try {
                    const parsed = JSON.parse(text);
                    errDetail = parsed.error?.message || parsed.error_description || parsed.message || text;
                } catch (_) {}
                throw new Error(`HTTP ${'$'}{resp.status}: ${'$'}{errDetail}`);
            }
            try {
                return JSON.parse(text);
            } catch (_) {
                return text;
            }
        }

        class ChatGPTAuth {
            constructor() {
                this.session = null;
                this.pendingAuth = null;
            }

            async loadSession() {
                // First check if native Android bridge has a session from vault file
                if (window.AndroidBridge && typeof window.AndroidBridge.getNativeSessionJson === 'function') {
                    try {
                        const nativeJson = window.AndroidBridge.getNativeSessionJson();
                        if (nativeJson && nativeJson.trim()) {
                            const nativeSession = JSON.parse(nativeJson);
                            if (nativeSession && nativeSession.accessToken) {
                                this.session = nativeSession;
                                await window.chatDb.setSetting('chatgpt_session', nativeSession);
                                return this.session;
                            }
                        }
                    } catch (e) {
                        console.warn('Failed reading native session:', e);
                    }
                }

                const saved = await window.chatDb.getSetting('chatgpt_session', null);
                if (saved && saved.accessToken) {
                    this.session = saved;
                    // Sync to native if native session file is not yet populated
                    if (window.AndroidBridge && typeof window.AndroidBridge.saveNativeSession === 'function') {
                        try { window.AndroidBridge.saveNativeSession(JSON.stringify(saved)); } catch (_) {}
                    }
                    if (this.session.expiresAt && Date.now() >= this.session.expiresAt - 120000) {
                        try {
                            await this.refreshToken();
                        } catch (e) {
                            console.warn('Auto refresh failed:', e);
                        }
                    }
                }
                return this.session;
            }

            async initiateLogin(customClientId = null) {
                const clientId = (customClientId || PUBLIC_CLIENT_ID).trim();
                const verifier = randomString(32);
                const state = randomString(32);
                const challenge = await pkceChallenge(verifier);

                this.pendingAuth = {
                    state,
                    verifier,
                    clientId,
                    redirectUri: REDIRECT_URI,
                    timestamp: Date.now(),
                };
                await window.chatDb.setSetting('pending_oauth', this.pendingAuth);

                const params = new URLSearchParams({
                    response_type: 'code',
                    client_id: clientId,
                    redirect_uri: REDIRECT_URI,
                    scope: SCOPE,
                    state: state,
                    code_challenge: challenge,
                    code_challenge_method: 'S256',
                    id_token_add_organizations: 'true',
                    codex_cli_simplified_flow: 'true',
                    originator: 'codex_cli_rs',
                });

                const fullUrl = `${'$'}{AUTH_URL}?${'$'}{params.toString()}`;

                if (window.AndroidBridge && typeof window.AndroidBridge.startLoopbackServer === 'function') {
                    try { window.AndroidBridge.startLoopbackServer(); } catch (_) {}
                }

                if (window.AndroidBridge && typeof window.AndroidBridge.openExternalUrl === 'function') {
                    window.AndroidBridge.openExternalUrl(fullUrl);
                } else {
                    window.open(fullUrl, '_blank');
                }

                return { fullUrl, state };
            }

            parseCallbackUrl(rawUrl) {
                let url;
                try {
                    url = new URL(rawUrl.trim());
                } catch (_) {
                    throw new Error('Please paste a valid URL starting with http://localhost:1455/auth/callback');
                }

                if (!url.pathname.includes('/auth/callback')) {
                    throw new Error('Callback URL must contain path /auth/callback');
                }

                const error = url.searchParams.get('error');
                if (error) {
                    const errorDesc = url.searchParams.get('error_description') || error;
                    throw new Error('OAuth error from OpenAI: ' + errorDesc);
                }

                const code = url.searchParams.get('code');
                const state = url.searchParams.get('state');

                if (!code) throw new Error('Authorization code missing in callback URL.');
                if (!state) throw new Error('State parameter missing in callback URL.');

                return { code, state };
            }

            async completeLoginWithUrl(rawUrl) {
                const { code, state } = this.parseCallbackUrl(rawUrl);
                const pending = this.pendingAuth || (await window.chatDb.getSetting('pending_oauth', null));

                if (!pending) {
                    throw new Error('No sign-in session found. Please start sign-in again.');
                }

                if (pending.state !== state) {
                    throw new Error('State mismatch. Sign-in may have expired.');
                }

                const bodyParams = new URLSearchParams({
                    grant_type: 'authorization_code',
                    client_id: pending.clientId,
                    redirect_uri: pending.redirectUri,
                    code: code,
                    code_verifier: pending.verifier,
                });

                const data = await sendHttpRequest(TOKEN_URL, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: bodyParams.toString(),
                });

                if (!data.access_token) {
                    throw new Error('No access_token returned.');
                }

                const accessClaims = parseJwt(data.access_token);
                const idClaims = parseJwt(data.id_token);
                const authClaim = idClaims['https://api.openai.com/auth'] || accessClaims['https://api.openai.com/auth'] || {};
                const profileClaim = accessClaims['https://api.openai.com/profile'] || {};
                const expiresAt = data.expires_in ? Date.now() + data.expires_in * 1000 : (accessClaims.exp ? accessClaims.exp * 1000 : Date.now() + 3600000);

                const sessionObj = {
                    accessToken: data.access_token,
                    refreshToken: data.refresh_token || '',
                    idToken: data.id_token || '',
                    accountId: authClaim.chatgpt_account_id || '',
                    email: idClaims.email || profileClaim.email || accessClaims.email || 'ChatGPT User',
                    clientId: pending.clientId,
                    expiresAt: expiresAt,
                    refreshedAt: Date.now(),
                };

                this.session = sessionObj;
                await window.chatDb.setSetting('chatgpt_session', sessionObj);
                await window.chatDb.setSetting('pending_oauth', null);
                this.pendingAuth = null;

                // Seamlessly synchronize session with native Vault storage
                if (window.AndroidBridge && typeof window.AndroidBridge.saveNativeSession === 'function') {
                    try { window.AndroidBridge.saveNativeSession(JSON.stringify(sessionObj)); } catch (_) {}
                }

                if (window.AndroidBridge && typeof window.AndroidBridge.stopLoopbackServer === 'function') {
                    window.AndroidBridge.stopLoopbackServer();
                }

                return this.session;
            }

            async refreshToken() {
                if (!this.session || !this.session.refreshToken) {
                    throw new Error('No active refresh token.');
                }

                const payload = {
                    grant_type: 'refresh_token',
                    client_id: this.session.clientId || PUBLIC_CLIENT_ID,
                    refresh_token: this.session.refreshToken,
                };

                const data = await sendHttpRequest(TOKEN_URL, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload),
                });

                if (!data.access_token) {
                    throw new Error('Refresh failed: token endpoint returned no access token.');
                }

                const accessClaims = parseJwt(data.access_token);
                const idClaims = parseJwt(data.id_token || this.session.idToken);
                const authClaim = idClaims['https://api.openai.com/auth'] || accessClaims['https://api.openai.com/auth'] || {};
                const expiresAt = data.expires_in ? Date.now() + data.expires_in * 1000 : (accessClaims.exp ? accessClaims.exp * 1000 : Date.now() + 3600000);

                this.session = {
                    ...this.session,
                    accessToken: data.access_token,
                    refreshToken: data.refresh_token || this.session.refreshToken,
                    idToken: data.id_token || this.session.idToken,
                    accountId: authClaim.chatgpt_account_id || this.session.accountId,
                    expiresAt: expiresAt,
                    refreshedAt: Date.now(),
                };

                await window.chatDb.setSetting('chatgpt_session', this.session);
                if (window.AndroidBridge && typeof window.AndroidBridge.saveNativeSession === 'function') {
                    try { window.AndroidBridge.saveNativeSession(JSON.stringify(this.session)); } catch (_) {}
                }
                return this.session;
            }

            async signOut() {
                this.session = null;
                await window.chatDb.setSetting('chatgpt_session', null);
                await window.chatDb.setSetting('pending_oauth', null);
                if (window.AndroidBridge && typeof window.AndroidBridge.clearNativeSession === 'function') {
                    try { window.AndroidBridge.clearNativeSession(); } catch (_) {}
                }
            }

            getApiHeaders() {
                if (!this.session || !this.session.accessToken) {
                    throw new Error('Not authenticated.');
                }
                const headers = {
                    Authorization: 'Bearer ' + this.session.accessToken,
                    originator: 'codex_cli_rs',
                };
                if (this.session.accountId) {
                    headers['ChatGPT-Account-ID'] = this.session.accountId;
                }
                return headers;
            }

            async fetchModels() {
                const headers = this.getApiHeaders();
                const url = `${'$'}{API_BASE}/models?client_version=${'$'}{encodeURIComponent(CODEX_VERSION)}`;
                let result;
                try {
                    result = await sendHttpRequest(url, { headers });
                } catch (err) {
                    if (err.message && err.message.includes('401')) {
                        await this.refreshToken();
                        const retryHeaders = this.getApiHeaders();
                        result = await sendHttpRequest(url, { headers: retryHeaders });
                    } else {
                        throw err;
                    }
                }

                const list = Array.isArray(result.models) ? result.models : (Array.isArray(result.data) ? result.data : []);
                return list.map((m) => ({
                    id: m.slug || m.id,
                    name: m.display_name || m.name || m.slug || m.id,
                    description: m.description || '',
                    reasoningLevels: m.supported_reasoning_levels || [],
                }));
            }
        }

        window.chatAuth = new ChatGPTAuth();
    </script>

    <!-- React Chat Application Core Component -->
    <script>
        const { useState, useEffect, useRef, useMemo } = React;

        const SYSTEM_PRESETS = [
            { id: 'helpful', name: 'Helpful Assistant', prompt: 'You are an intelligent, helpful, and versatile AI assistant.' },
            { id: 'coder', name: 'Code Expert & Architect', prompt: 'You are an expert senior software engineer. Provide clean, secure, idiomatic, and well-commented code with concise explanations.' },
            { id: 'concise', name: 'Concise & Direct', prompt: 'Be extremely concise, direct, and actionable. Avoid fluff, unnecessary disclaimers, or repetitive pleasantries.' },
            { id: 'creative', name: 'Creative Writer', prompt: 'You are an imaginative and engaging creative writer. Use vivid metaphors, dynamic pacing, and thoughtful storytelling.' },
            { id: 'tutor', name: 'Socratic Tutor', prompt: 'You are a patient and encouraging tutor. Guide the user step-by-step using thought-provoking questions.' },
        ];

        function formatTime(timestamp) {
            if (!timestamp) return '';
            const date = new Date(timestamp);
            return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        }

        function formatDate(timestamp) {
            if (!timestamp) return '';
            const date = new Date(timestamp);
            return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
        }

        function renderMarkdownContent(text) {
            if (!text) return '';
            let escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            escaped = escaped.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (match, lang, code) => {
                return '<div class="code-block-wrapper"><div class="code-header"><span>' + (lang || 'code') + '</span></div><pre><code>' + code + '</code></pre></div>';
            });
            escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>');
            escaped = escaped.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
            escaped = escaped.replace(/\*([^*]+)\*/g, '<em>$1</em>');
            escaped = escaped.replace(/\n/g, '<br/>');
            return escaped;
        }

        function ChatApp() {
            const [sessions, setSessions] = useState([]);
            const [activeSessionId, setActiveSessionId] = useState(null);
            const [messages, setMessages] = useState([]);
            const [authSession, setAuthSession] = useState(null);
            const [models, setModels] = useState([
                { id: 'gpt-4o', name: 'GPT-4o (Omni)' },
                { id: 'gpt-4o-mini', name: 'GPT-4o Mini' },
                { id: 'o1', name: 'o1 (Reasoning)' },
                { id: 'o3-mini', name: 'o3-mini' },
            ]);
            const [selectedModel, setSelectedModel] = useState('gpt-4o');
            const [reasoningEffort, setReasoningEffort] = useState('');
            const [systemInstructions, setSystemInstructions] = useState('You are a helpful assistant.');
            const [composerText, setComposerText] = useState('');
            const [isStreaming, setIsStreaming] = useState(false);
            const [streamingStatus, setStreamingStatus] = useState('');
            const [drawerOpen, setDrawerOpen] = useState(window.innerWidth > 768);
            const [isMobile, setIsMobile] = useState(window.innerWidth <= 768);
            const [searchQuery, setSearchQuery] = useState('');
            const [activeModal, setActiveModal] = useState(null);
            const [theme, setTheme] = useState('dark');
            const [sessionToRename, setSessionToRename] = useState(null);
            const [renameInput, setRenameInput] = useState('');
            const [callbackInput, setCallbackInput] = useState('');
            const [authNotice, setAuthNotice] = useState('');
            const [customClientId, setCustomClientId] = useState('');

            const messagesEndRef = useRef(null);
            const activeStreamIdRef = useRef(null);
            const liveMessageRef = useRef('');

            useEffect(() => {
                async function setup() {
                    const savedTheme = (await window.chatDb.getSetting('theme', 'dark')) || 'dark';
                    setTheme(savedTheme);
                    document.documentElement.setAttribute('data-theme', savedTheme);

                    const savedInstructions = await window.chatDb.getSetting('system_instructions', 'You are a helpful assistant.');
                    setSystemInstructions(savedInstructions);

                    const savedModel = await window.chatDb.getSetting('selected_model', 'gpt-4o');
                    setSelectedModel(savedModel);

                    const session = await window.chatAuth.loadSession();
                    setAuthSession(session);

                    if (session) {
                        try {
                            const modelList = await window.chatAuth.fetchModels();
                            if (modelList && modelList.length > 0) setModels(modelList);
                        } catch (e) {
                            console.warn('Could not fetch models:', e);
                        }
                    }

                    const loadedSessions = await window.chatDb.getSessions();
                    setSessions(loadedSessions);

                    if (loadedSessions.length > 0) {
                        const lastSessionId = await window.chatDb.getSetting('last_session_id', loadedSessions[0].id);
                        const validId = loadedSessions.some((s) => s.id === lastSessionId) ? lastSessionId : loadedSessions[0].id;
                        setActiveSessionId(validId);
                        loadSessionMessages(validId);
                    } else {
                        await createNewChat();
                    }
                }
                setup();

                window.onNativeSessionUpdated = async (sessionJson) => {
                    try {
                        if (sessionJson && sessionJson.trim()) {
                            const parsed = typeof sessionJson === 'string' ? JSON.parse(sessionJson) : sessionJson;
                            window.chatAuth.session = parsed;
                            await window.chatDb.setSetting('chatgpt_session', parsed);
                            setAuthSession(parsed);
                            try {
                                const mList = await window.chatAuth.fetchModels();
                                if (mList && mList.length > 0) setModels(mList);
                            } catch (_) {}
                        } else {
                            window.chatAuth.session = null;
                            await window.chatDb.setSetting('chatgpt_session', null);
                            setAuthSession(null);
                        }
                    } catch (err) {
                        console.warn('onNativeSessionUpdated error:', err);
                    }
                };

                window.onOAuthCallbackReceived = async (url) => {
                    try {
                        setAuthNotice('Received OAuth callback! Exchanging authorization code...');
                        setActiveModal('auth');
                        const session = await window.chatAuth.completeLoginWithUrl(url);
                        setAuthSession(session);
                        setAuthNotice('Successfully signed in with ChatGPT!');
                        try {
                            const mList = await window.chatAuth.fetchModels();
                            if (mList && mList.length > 0) setModels(mList);
                        } catch (_) {}
                        setTimeout(() => setActiveModal(null), 1200);
                    } catch (err) {
                        setAuthNotice('Login error: ' + err.message);
                    }
                };
            }, []);

            useEffect(() => {
                messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
            }, [messages, isStreaming, streamingStatus]);

            const loadSessionMessages = async (sessionId) => {
                if (!sessionId) return;
                const msgs = await window.chatDb.getMessages(sessionId);
                setMessages(msgs);
                await window.chatDb.setSetting('last_session_id', sessionId);
            };

            const handleSelectSession = (sessionId) => {
                if (isStreaming) {
                    if (!confirm('A reply is currently generating. Stop and switch session?')) return;
                    handleStopStreaming();
                }
                setActiveSessionId(sessionId);
                loadSessionMessages(sessionId);
                if (isMobile) setDrawerOpen(false);
            };

            const createNewChat = async () => {
                if (isStreaming) handleStopStreaming();

                const newId = 'session_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
                const newSession = {
                    id: newId,
                    title: 'New Conversation',
                    createdAt: Date.now(),
                    updatedAt: Date.now(),
                    systemInstructions: systemInstructions,
                    model: selectedModel,
                    messageCount: 0,
                };

                await window.chatDb.saveSession(newSession);
                const updated = await window.chatDb.getSessions();
                setSessions(updated);
                setActiveSessionId(newId);
                setMessages([]);
                await window.chatDb.setSetting('last_session_id', newId);

                if (isMobile) setDrawerOpen(false);
            };

            const handleDeleteSession = async (e, sessionId) => {
                e.stopPropagation();
                if (!confirm('Are you sure you want to delete this conversation?')) return;

                if (sessionId === activeSessionId && isStreaming) {
                    handleStopStreaming();
                }

                await window.chatDb.deleteSession(sessionId);
                const updated = await window.chatDb.getSessions();
                setSessions(updated);

                if (activeSessionId === sessionId) {
                    if (updated.length > 0) {
                        setActiveSessionId(updated[0].id);
                        loadSessionMessages(updated[0].id);
                    } else {
                        await createNewChat();
                    }
                }
            };

            const handleExportMarkdown = (e, sessionToExport = null) => {
                if (e) e.stopPropagation();
                const targetSession = sessionToExport || sessions.find((s) => s.id === activeSessionId);
                if (!targetSession) return;

                window.chatDb.getMessages(targetSession.id).then((chatMsgs) => {
                    let md = '# ' + targetSession.title + '\n\n';
                    md += '*Generated by ChatGPT PKCE Chatbox*\n';
                    md += '*Date: ' + new Date(targetSession.createdAt).toLocaleString() + '*\n';
                    md += '*Model: ' + (targetSession.model || selectedModel) + '*\n\n---\n\n';

                    chatMsgs.forEach((msg) => {
                        const roleName = msg.role === 'user' ? '👤 User' : '🤖 Assistant (' + (msg.model || 'ChatGPT') + ')';
                        const time = new Date(msg.createdAt).toLocaleTimeString();
                        md += '### ' + roleName + ' - ' + time + '\n\n' + msg.text + '\n\n---\n\n';
                    });

                    const safeTitle = targetSession.title.replace(/[^a-zA-Z0-9_-]/g, '_').toLowerCase();
                    const fileName = 'chat_' + safeTitle + '_' + Date.now() + '.md';

                    if (window.AndroidBridge && typeof window.AndroidBridge.exportMarkdown === 'function') {
                        window.AndroidBridge.exportMarkdown(fileName, md);
                    }
                });
            };

            const handleStartOAuth = async () => {
                try {
                    setAuthNotice('Opening OpenAI authorization page in browser...');
                    await window.chatAuth.initiateLogin(customClientId);
                    setAuthNotice('Sign in with OpenAI in the browser. When completed, copy localhost:1455 address and paste below if needed.');
                } catch (err) {
                    setAuthNotice('Failed to start sign in: ' + err.message);
                }
            };

            const handleManualOAuthComplete = async () => {
                if (!callbackInput.trim()) return;
                try {
                    setAuthNotice('Validating callback and exchanging code...');
                    const session = await window.chatAuth.completeLoginWithUrl(callbackInput.trim());
                    setAuthSession(session);
                    setCallbackInput('');
                    setAuthNotice('Signed in successfully!');
                    try {
                        const mList = await window.chatAuth.fetchModels();
                        if (mList && mList.length > 0) setModels(mList);
                    } catch (_) {}
                    setTimeout(() => setActiveModal(null), 1200);
                } catch (err) {
                    setAuthNotice('Sign-in error: ' + err.message);
                }
            };

            const handleSignOut = async () => {
                if (!confirm('Sign out from ChatGPT?')) return;
                await window.chatAuth.signOut();
                setAuthSession(null);
                setAuthNotice('');
                setActiveModal(null);
            };

            const handleSendMessage = async () => {
                if (!composerText.trim() || isStreaming) return;

                if (!authSession || !authSession.accessToken) {
                    setActiveModal('auth');
                    setAuthNotice('Please authenticate with ChatGPT OAuth 2.0 PKCE first.');
                    return;
                }

                const currentText = composerText.trim();
                setComposerText('');

                const currentSession = sessions.find((s) => s.id === activeSessionId);
                let titleToUpdate = currentSession?.title;

                if (currentSession && (currentSession.title === 'New Conversation' || messages.length === 0)) {
                    titleToUpdate = currentText.length > 36 ? currentText.substring(0, 36) + '…' : currentText;
                }

                const userMsgId = 'msg_' + Date.now() + '_u';
                const userMessage = {
                    id: userMsgId,
                    sessionId: activeSessionId,
                    role: 'user',
                    text: currentText,
                    status: 'complete',
                    createdAt: Date.now(),
                };

                const aiMsgId = 'msg_' + (Date.now() + 1) + '_a';
                const aiMessage = {
                    id: aiMsgId,
                    sessionId: activeSessionId,
                    role: 'assistant',
                    text: '',
                    status: 'streaming',
                    model: selectedModel,
                    createdAt: Date.now(),
                };

                const newMessages = [...messages, userMessage, aiMessage];
                setMessages(newMessages);

                await window.chatDb.saveMessage(userMessage);
                await window.chatDb.saveMessage(aiMessage);

                if (window.AndroidBridge && typeof window.AndroidBridge.saveMessageToVault === 'function') {
                    try { window.AndroidBridge.saveMessageToVault('user', currentText, selectedModel); } catch (_) {}
                }

                if (titleToUpdate !== currentSession?.title) {
                    const updated = { ...currentSession, title: titleToUpdate, updatedAt: Date.now(), messageCount: newMessages.length };
                    await window.chatDb.saveSession(updated);
                    const list = await window.chatDb.getSessions();
                    setSessions(list);
                }

                const inputHistory = newMessages
                    .filter((m) => m.id !== aiMsgId)
                    .map((m) => ({
                        type: 'message',
                        role: m.role,
                        content: [{ type: m.role === 'user' ? 'input_text' : 'output_text', text: m.text }],
                    }));

                const payload = {
                    model: selectedModel,
                    instructions: systemInstructions,
                    input: inputHistory,
                    stream: true,
                    store: false,
                    tools: [],
                    parallel_tool_calls: false,
                };

                if (reasoningEffort) {
                    payload.reasoning = { effort: reasoningEffort };
                }

                setIsStreaming(true);
                setStreamingStatus('Thinking...');
                liveMessageRef.current = '';

                const streamId = 'stream_' + Date.now();
                activeStreamIdRef.current = streamId;

                try {
                    let auth = authSession;
                    if (auth.expiresAt && Date.now() >= auth.expiresAt - 90000) {
                        auth = await window.chatAuth.refreshToken();
                        setAuthSession(auth);
                    }

                    const headers = window.chatAuth.getApiHeaders();
                    const apiUrl = 'https://chatgpt.com/backend-api/codex/responses';

                    if (window.AndroidBridge && typeof window.AndroidBridge.startStreamingRequest === 'function') {
                        window['__stream_chunk_' + streamId] = (chunk) => {
                            handleIncomingStreamDelta(chunk, aiMsgId);
                        };
                        window['__stream_end_' + streamId] = (isError, errorMsg) => {
                            delete window['__stream_chunk_' + streamId];
                            delete window['__stream_end_' + streamId];
                            finalizeStream(aiMsgId, isError ? errorMsg : null);
                        };

                        window.AndroidBridge.startStreamingRequest(
                            streamId,
                            apiUrl,
                            JSON.stringify({ ...headers, 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
                            JSON.stringify(payload)
                        );
                    }
                } catch (err) {
                    finalizeStream(aiMsgId, err.message);
                }
            };

            const handleIncomingStreamDelta = (rawSSEChunk, aiMsgId) => {
                const lines = rawSSEChunk.split('\n');
                for (const line of lines) {
                    const trimmed = line.trim();
                    if (trimmed.startsWith('data: ')) {
                        const dataStr = trimmed.substring(6).trim();
                        if (dataStr === '[DONE]') continue;
                        try {
                            const event = JSON.parse(dataStr);
                            const type = event.type;
                            if (type === 'response.output_text.delta' || type === 'response.refusal.delta') {
                                if (event.delta) {
                                    liveMessageRef.current += event.delta;
                                    setStreamingStatus('Receiving reply...');
                                    setMessages((prev) =>
                                        prev.map((m) => (m.id === aiMsgId ? { ...m, text: liveMessageRef.current } : m))
                                    );
                                }
                            } else if (type === 'response.created' || type === 'response.in_progress' || type?.startsWith('response.reasoning')) {
                                setStreamingStatus('Thinking...');
                            } else if (type === 'response.completed') {
                                if (event.response?.output) {
                                    let fullText = '';
                                    for (const item of event.response.output) {
                                        if (item.type === 'message' && item.content) {
                                            for (const c of item.content) {
                                                if (c.type === 'output_text') fullText += c.text;
                                            }
                                        }
                                    }
                                    if (fullText) liveMessageRef.current = fullText;
                                }
                            }
                        } catch (_) {}
                    }
                }
            };

            const finalizeStream = async (aiMsgId, error) => {
                setIsStreaming(false);
                setStreamingStatus('');
                activeStreamIdRef.current = null;

                const finalText = liveMessageRef.current;
                const finalStatus = error ? 'error' : 'complete';

                setMessages((prev) =>
                    prev.map((m) =>
                        m.id === aiMsgId
                            ? { ...m, text: finalText || (error ? 'Error: ' + error : ''), status: finalStatus, error: error || '' }
                            : m
                    )
                );

                await window.chatDb.updateMessage(aiMsgId, {
                    text: finalText || (error ? 'Error: ' + error : ''),
                    status: finalStatus,
                    error: error || '',
                });

                if (!error && finalText && window.AndroidBridge && typeof window.AndroidBridge.saveMessageToVault === 'function') {
                    try { window.AndroidBridge.saveMessageToVault('model', finalText, selectedModel); } catch (_) {}
                }
            };

            const handleStopStreaming = () => {
                if (!isStreaming) return;
                if (activeStreamIdRef.current && window.AndroidBridge && typeof window.AndroidBridge.cancelStreaming === 'function') {
                    window.AndroidBridge.cancelStreaming(activeStreamIdRef.current);
                }
                setIsStreaming(false);
                setStreamingStatus('');
            };

            const filteredSessions = useMemo(() => {
                if (!searchQuery.trim()) return sessions;
                const q = searchQuery.toLowerCase();
                return sessions.filter((s) => s.title.toLowerCase().includes(q));
            }, [sessions, searchQuery]);

            return (
                React.createElement('div', { className: 'app-container' },
                    isMobile && drawerOpen && React.createElement('div', { className: 'drawer-overlay', onClick: () => setDrawerOpen(false) }),
                    React.createElement('aside', { className: 'side-drawer ' + (drawerOpen ? 'open' : '') },
                        React.createElement('div', { className: 'drawer-header' },
                            React.createElement('div', { className: 'app-brand' },
                                React.createElement('span', { className: 'brand-icon' }, '✦'),
                                React.createElement('span', null, 'ChatGPT Codex'),
                                React.createElement('span', { className: 'brand-badge' }, 'PKCE')
                            ),
                            isMobile && React.createElement('button', { className: 'btn-icon', onClick: () => setDrawerOpen(false) }, '✕')
                        ),
                        React.createElement('div', { className: 'drawer-actions' },
                            React.createElement('button', { className: 'btn-new-chat', onClick: createNewChat }, '+ New Chat'),
                            React.createElement('div', { className: 'search-container' },
                                React.createElement('span', { className: 'search-icon' }, '🔍'),
                                React.createElement('input', {
                                    type: 'text',
                                    className: 'search-input',
                                    placeholder: 'Search conversations...',
                                    value: searchQuery,
                                    onChange: (e) => setSearchQuery(e.target.value)
                                })
                            )
                        ),
                        React.createElement('div', { className: 'sessions-list' },
                            filteredSessions.map((session) =>
                                React.createElement('div', {
                                    key: session.id,
                                    className: 'session-item ' + (session.id === activeSessionId ? 'active' : ''),
                                    onClick: () => handleSelectSession(session.id)
                                },
                                    React.createElement('div', { className: 'session-info' },
                                        React.createElement('div', { className: 'session-title' }, session.title),
                                        React.createElement('div', { className: 'session-meta' }, formatDate(session.updatedAt))
                                    ),
                                    React.createElement('div', { className: 'session-item-actions' },
                                        React.createElement('button', { className: 'session-action-btn', onClick: (e) => handleExportMarkdown(e, session), title: 'Export Markdown' }, '📥'),
                                        React.createElement('button', { className: 'session-action-btn delete', onClick: (e) => handleDeleteSession(e, session.id), title: 'Delete' }, '🗑')
                                    )
                                )
                            )
                        ),
                        React.createElement('div', { className: 'drawer-footer' },
                            React.createElement('button', { className: 'btn-icon', onClick: () => setActiveModal('system'), title: 'Persona' }, '⚙️'),
                            React.createElement('button', { className: 'btn-icon', onClick: () => setActiveModal('auth'), title: 'OAuth Account' }, '👤')
                        )
                    ),
                    React.createElement('main', { className: 'chat-main' },
                        React.createElement('header', { className: 'chat-header' },
                            React.createElement('div', { className: 'header-left' },
                                React.createElement('button', { className: 'btn-icon', onClick: () => setDrawerOpen(!drawerOpen) }, '☰'),
                                React.createElement('select', {
                                    className: 'model-select',
                                    value: selectedModel,
                                    onChange: (e) => {
                                        setSelectedModel(e.target.value);
                                        window.chatDb.setSetting('selected_model', e.target.value);
                                    }
                                },
                                    models.map((m) => React.createElement('option', { key: m.id, value: m.id }, m.name))
                                )
                            ),
                            React.createElement('div', { className: 'header-right' },
                                React.createElement('button', {
                                    className: 'status-badge',
                                    onClick: () => setActiveModal('auth')
                                },
                                    React.createElement('div', { className: 'status-dot ' + (authSession ? (isStreaming ? 'pulsing' : '') : 'offline') }),
                                    React.createElement('span', null, authSession ? authSession.email.split('@')[0] : 'Sign In')
                                )
                            )
                        ),
                        React.createElement('div', { className: 'messages-container' },
                            messages.length === 0 ? React.createElement('div', { className: 'empty-chat' },
                                React.createElement('div', { className: 'empty-sparkle' }, '✦'),
                                React.createElement('h2', { className: 'empty-title' }, 'ChatGPT OAuth PKCE'),
                                React.createElement('p', { className: 'empty-desc' }, 'Real-time Codex SSE streaming via native OkHttp AndroidBridge.'),
                                !authSession && React.createElement('button', { className: 'btn-new-chat', onClick: () => setActiveModal('auth') }, 'Sign In with ChatGPT')
                            ) : messages.map((msg) =>
                                React.createElement('div', { key: msg.id, className: 'message-row ' + msg.role },
                                    React.createElement('div', { className: 'message-bubble' },
                                        React.createElement('div', { className: 'message-header' },
                                            React.createElement('span', { className: 'message-sender' }, msg.role === 'user' ? 'You' : msg.model || 'ChatGPT'),
                                            React.createElement('span', { className: 'message-time' }, formatTime(msg.createdAt))
                                        ),
                                        msg.role === 'assistant' && msg.status === 'streaming' && React.createElement('div', { className: 'streaming-status-tag' },
                                            React.createElement('div', { className: 'status-dot pulsing' }),
                                            React.createElement('span', null, streamingStatus || 'Generating...')
                                        ),
                                        React.createElement('div', {
                                            className: 'message-body',
                                            dangerouslySetInnerHTML: { __html: renderMarkdownContent(msg.text) }
                                        }),
                                        msg.role === 'assistant' && msg.status === 'streaming' && React.createElement('span', { className: 'streaming-cursor' }),
                                        msg.error && React.createElement('div', { style: { color: 'var(--accent-danger)', fontSize: '12px', marginTop: '6px' } }, '⚠️ ' + msg.error)
                                    )
                                )
                            ),
                            React.createElement('div', { ref: messagesEndRef })
                        ),
                        React.createElement('div', { className: 'composer-area' },
                            React.createElement('div', { className: 'composer-box' },
                                React.createElement('textarea', {
                                    className: 'composer-textarea',
                                    placeholder: authSession ? 'Message ChatGPT Codex... (Enter to send)' : 'Sign in with ChatGPT OAuth to start chatting...',
                                    value: composerText,
                                    disabled: !authSession,
                                    rows: 1,
                                    onChange: (e) => setComposerText(e.target.value),
                                    onKeyDown: (e) => {
                                        if (e.key === 'Enter' && !e.shiftKey) {
                                            e.preventDefault();
                                            handleSendMessage();
                                        }
                                    }
                                }),
                                isStreaming ? React.createElement('button', { className: 'btn-stop', onClick: handleStopStreaming }, '■')
                                    : React.createElement('button', {
                                        className: 'btn-send',
                                        disabled: !composerText.trim() || !authSession,
                                        onClick: handleSendMessage
                                    }, '↑')
                            ),
                            React.createElement('div', { className: 'composer-hint' }, 'ChatGPT OAuth 2.0 PKCE · Native OkHttp Bridge · CORS-Bypassed')
                        )
                    ),
                    activeModal === 'auth' && React.createElement('div', { className: 'modal-backdrop', onClick: () => setActiveModal(null) },
                        React.createElement('div', { className: 'modal-content', onClick: (e) => e.stopPropagation() },
                            React.createElement('div', { className: 'modal-header' },
                                React.createElement('div', { className: 'modal-title' }, '🔐 ChatGPT OAuth 2.0 PKCE'),
                                React.createElement('button', { className: 'btn-icon', onClick: () => setActiveModal(null) }, '✕')
                            ),
                            React.createElement('div', { className: 'modal-body' },
                                authSession ? React.createElement('div', null,
                                    React.createElement('div', { style: { padding: '12px', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)' } },
                                        React.createElement('div', { style: { fontWeight: 600, color: 'var(--accent-primary)', marginBottom: '4px' } }, '✓ Authenticated'),
                                        React.createElement('div', { style: { fontSize: '13px' } }, 'Email: ' + authSession.email),
                                        React.createElement('div', { style: { fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' } }, 'Token expires: ' + new Date(authSession.expiresAt).toLocaleString())
                                    ),
                                    React.createElement('button', {
                                        className: 'btn-secondary',
                                        style: { color: 'var(--accent-danger)', marginTop: '10px', width: '100%' },
                                        onClick: handleSignOut
                                    }, 'Sign Out')
                                ) : React.createElement('div', null,
                                    React.createElement('p', { style: { fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '12px' } },
                                        'Connect directly to OpenAI using official OAuth PKCE flow (redirects to localhost:1455).'
                                    ),
                                    React.createElement('button', { className: 'btn-new-chat', style: { width: '100%' }, onClick: handleStartOAuth }, 'Sign In with ChatGPT'),
                                    React.createElement('div', { className: 'form-group', style: { marginTop: '14px' } },
                                        React.createElement('label', { className: 'form-label' }, 'Manual Callback URL Paste'),
                                        React.createElement('input', {
                                            type: 'text',
                                            className: 'form-input',
                                            placeholder: 'http://localhost:1455/auth/callback?code=...&state=...',
                                            value: callbackInput,
                                            onChange: (e) => setCallbackInput(e.target.value)
                                        }),
                                        React.createElement('button', {
                                            className: 'btn-primary',
                                            style: { marginTop: '8px', width: '100%' },
                                            disabled: !callbackInput.trim(),
                                            onClick: handleManualOAuthComplete
                                        }, 'Complete Sign-In')
                                    )
                                ),
                                authNotice && React.createElement('div', {
                                    style: {
                                        padding: '8px 12px',
                                        background: 'rgba(6, 182, 212, 0.1)',
                                        border: '1px solid rgba(6, 182, 212, 0.3)',
                                        color: 'var(--accent-cyan)',
                                        fontSize: '12px',
                                        borderRadius: 'var(--radius-md)'
                                    }
                                }, authNotice)
                            ),
                            React.createElement('div', { className: 'modal-footer' },
                                React.createElement('button', { className: 'btn-secondary', onClick: () => setActiveModal(null) }, 'Close')
                            )
                        )
                    ),
                    activeModal === 'system' && React.createElement('div', { className: 'modal-backdrop', onClick: () => setActiveModal(null) },
                        React.createElement('div', { className: 'modal-content', onClick: (e) => e.stopPropagation() },
                            React.createElement('div', { className: 'modal-header' },
                                React.createElement('div', { className: 'modal-title' }, '⚙️ System Instructions & Persona'),
                                React.createElement('button', { className: 'btn-icon', onClick: () => setActiveModal(null) }, '✕')
                            ),
                            React.createElement('div', { className: 'modal-body' },
                                React.createElement('div', { className: 'preset-chips' },
                                    SYSTEM_PRESETS.map((p) =>
                                        React.createElement('button', {
                                            key: p.id,
                                            className: 'preset-chip ' + (systemInstructions === p.prompt ? 'active' : ''),
                                            onClick: () => setSystemInstructions(p.prompt)
                                        }, p.name)
                                    )
                                ),
                                React.createElement('textarea', {
                                    className: 'form-textarea',
                                    rows: 6,
                                    value: systemInstructions,
                                    onChange: (e) => setSystemInstructions(e.target.value)
                                })
                            ),
                            React.createElement('div', { className: 'modal-footer' },
                                React.createElement('button', { className: 'btn-secondary', onClick: () => setActiveModal(null) }, 'Cancel'),
                                React.createElement('button', {
                                    className: 'btn-primary',
                                    onClick: async () => {
                                        await window.chatDb.setSetting('system_instructions', systemInstructions);
                                        setActiveModal(null);
                                    }
                                }, 'Save Instructions')
                            )
                        )
                    )
                )
            );
        }

        const root = ReactDOM.createRoot(document.getElementById('root'));
        root.render(React.createElement(ChatApp));
    </script>
</body>
</html>
    """.trimIndent()
}
