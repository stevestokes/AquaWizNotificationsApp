package app.aquawiznotifier

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AquaWizWebLogin(
    private val activity: Activity,
    private val loginUrl: String,
    private val onDismiss: () -> Unit = {},
    private val onToken: (String, String?, List<String>) -> Unit,
) {
    private val completed = java.util.concurrent.atomic.AtomicBoolean(false)

    fun show() {
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        root.addView(TextView(activity).apply {
            text = "AquaWiz Web Login"
            textSize = 20f
            setTextColor(Color.BLACK)
        })
        root.addView(TextView(activity).apply {
            text = "Sign in on the official AquaWiz page below. AquaWiz Notifier captures only the returned access token; it does not save your web password."
            textSize = 13f
            setPadding(0, dp(4), 0, dp(8))
        })

        val webView = WebView(activity)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false

        val bridge = TokenBridge { token, account, devices ->
            if (completed.compareAndSet(false, true)) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    onToken(token, account, devices)
                }
            }
        }
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && isAllowedNavigation(Uri.parse(url))) injectTokenCapture(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !isAllowedNavigation(request.url)
            }
        }

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        root.addView(Button(activity).apply {
            text = "Cancel"
            setOnClickListener { dialog.dismiss() }
        })

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            completed.set(true)
            onDismiss()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.stopLoading()
            webView.destroy()
        }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        webView.loadUrl(loginUrl)
    }

    private fun isAllowedNavigation(uri: Uri): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase().orEmpty()
        return host == "aquawiz.net" ||
            host.endsWith(".aquawiz.net") ||
            host == "aquawiz.cn" ||
            host.endsWith(".aquawiz.cn")
    }

    private fun injectTokenCapture(webView: WebView) {
        webView.evaluateJavascript(CAPTURE_SCRIPT, null)
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private class TokenBridge(
        private val onToken: (String, String?, List<String>) -> Unit,
    ) {
        @Volatile private var account: String? = null
        @Volatile private var devices: List<String> = emptyList()
        @JavascriptInterface
        fun captureAccount(user: String?, serials: String?) {
            account = user?.trim()?.takeIf { it.isNotBlank() && it.length < 200 }
            devices = serials.orEmpty().split(",").map { it.trim() }.filter { it.startsWith("KH", true) || it.startsWith("CA", true) }
        }
        @JavascriptInterface
        fun captureToken(token: String?) {
            val normalized = token?.trim().orEmpty()
            if (normalized.length < 20 || normalized.any { it.isWhitespace() }) return
            onToken(normalized, account, devices)
        }
    }

    companion object {
        private const val BRIDGE_NAME = "AquaWizTokenBridge"

        // Capture both fetch() and XMLHttpRequest login responses, then keep scanning web storage
        // in case the AquaWiz site has already stored the token before this script is installed.
        private val CAPTURE_SCRIPT = """
            (function() {
              if (window.__aquawizNotifierTokenCaptureInstalled) return;
              window.__aquawizNotifierTokenCaptureInstalled = true;

              function findToken(value, depth) {
                if (depth > 8 || value == null) return null;

                if (typeof value === 'string') {
                  var trimmed = value.trim();
                  if (!trimmed) return null;
                  if ((trimmed[0] === '{' && trimmed[trimmed.length - 1] === '}') ||
                      (trimmed[0] === '[' && trimmed[trimmed.length - 1] === ']')) {
                    try { return findToken(JSON.parse(trimmed), depth + 1); } catch (e) {}
                  }
                  return null;
                }

                if (typeof value === 'object') {
                  if (typeof value.access_token === 'string' && value.access_token.length > 20) {
                    return value.access_token;
                  }
                  if (typeof value.accessToken === 'string' && value.accessToken.length > 20) {
                    return value.accessToken;
                  }
                  for (var key in value) {
                    if (!Object.prototype.hasOwnProperty.call(value, key)) continue;
                    var found = findToken(value[key], depth + 1);
                    if (found) return found;
                  }
                }
                return null;
              }

              function report(value) {
                try {
                  if (typeof value === 'string') { try { value = JSON.parse(value); } catch (e) {} }
                  var token = findToken(value, 0);
                  if (value && typeof value === 'object' && window.AquaWizTokenBridge) {
                    var user = typeof value.user === 'string' ? value.user :
                      (value.username || (value.user && (value.user.username || value.user.user)));
                    var devices = value.devices || (value.user && value.user.devices) || [];
                    var serials = Array.isArray(devices) ? devices.map(function(d) {
                      return typeof d === 'string' ? d : (d.deviceSerial || d.serial || d.sn || '');
                    }).join(',') : '';
                    if (user) window.AquaWizTokenBridge.captureAccount(String(user), serials);
                  }
                  if (token && window.AquaWizTokenBridge) {
                    window.AquaWizTokenBridge.captureToken(token);
                  }
                } catch (e) {}
              }

              try {
                var originalFetch = window.fetch;
                if (originalFetch) {
                  window.fetch = function() {
                    return originalFetch.apply(this, arguments).then(function(response) {
                      try {
                        response.clone().text().then(report).catch(function(){});
                      } catch (e) {}
                      return response;
                    });
                  };
                }
              } catch (e) {}

              try {
                var originalSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                  try {
                    this.addEventListener('load', function() {
                      try { report(this.responseText); } catch (e) {}
                    });
                  } catch (e) {}
                  return originalSend.apply(this, arguments);
                };
              } catch (e) {}

              function scanStorage(storage) {
                if (!storage) return;
                for (var i = 0; i < storage.length; i++) {
                  try { report(storage.getItem(storage.key(i))); } catch (e) {}
                }
              }

              try { scanStorage(window.localStorage); } catch (e) {}
              try { scanStorage(window.sessionStorage); } catch (e) {}

              window.setInterval(function() {
                try { scanStorage(window.localStorage); } catch (e) {}
                try { scanStorage(window.sessionStorage); } catch (e) {}
              }, 750);
            })();
        """.trimIndent()
    }
}
