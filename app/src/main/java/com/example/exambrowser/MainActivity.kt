package com.example.exambrowser

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class MainActivity : AppCompatActivity() {
    companion object {
        private const val BASE_URL = "http://10.46.96.185:8002"
        private const val EXAM_URL = "http://elsph.permataharapanku.sch.id"
        private const val LOCK_TASK_RETRY_DELAY_MS = 1_500L
    }

    private var activeSessionId: Long? = null
    private var examWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lockTaskRetryScheduled = false
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (examWebView != null) {
                showExitPinDialog()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        showPinInput()
    }

    private fun showPinInput() {
        setContentView(R.layout.activity_pin)
        applySystemBarsPadding(R.id.pinMain)

        val nameLayout = findViewById<TextInputLayout>(R.id.nameLayout)
        val nameInput = findViewById<TextInputEditText>(R.id.nameInput)
        val pinLayout = findViewById<TextInputLayout>(R.id.pinLayout)
        val pinInput = findViewById<TextInputEditText>(R.id.pinInput)
        val pinButton = findViewById<MaterialButton>(R.id.pinButton)

        pinButton.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty().trim()
            val pin = pinInput.text?.toString().orEmpty()
            if (name.isBlank()) {
                nameLayout.error = "Nama wajib diisi."
                return@setOnClickListener
            }

            if (pin.length != 6) {
                nameLayout.error = null
                pinLayout.error = "PIN harus 6 digit."
                return@setOnClickListener
            }

            nameLayout.error = null
            pinLayout.error = null
            pinButton.isEnabled = false

            Thread {
                val result = joinExamSession(name, pin)

                runOnUiThread {
                    pinButton.isEnabled = true

                    if (result.success) {
                        Snackbar.make(
                            findViewById(R.id.pinMain),
                            "PIN benar.",
                            Snackbar.LENGTH_SHORT
                        ).show()
                        activeSessionId = result.sessionId
                        pinButton.postDelayed({ showExamWebsite() }, 700)
                    } else {
                        pinLayout.error = result.message
                    }
                }
            }.start()
        }
    }

    private fun joinExamSession(name: String, pin: String): JoinResult {
        return try {
            val connection = (URL("$BASE_URL/api/student/exam-sessions/join").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            connection.outputStream.use { output ->
                output.write(
                    JSONObject()
                        .put("name", name)
                        .put("pin", pin)
                        .toString()
                        .toByteArray()
                )
            }

            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()

            val json = JSONObject(responseBody)
            if (responseCode in 200..299) {
                JoinResult(
                    success = true,
                    message = json.optString("message", "Berhasil masuk ujian."),
                    sessionTitle = json.optJSONObject("session")?.optString("title", "ujian").orEmpty(),
                    sessionId = json.optJSONObject("session")?.optLong("id")
                        ?.takeIf { it > 0 }
                )
            } else {
                JoinResult(
                    success = false,
                    message = json.optString("message", "PIN tidak valid."),
                    sessionTitle = "",
                    sessionId = null
                )
            }
        } catch (exception: Exception) {
            JoinResult(
                success = false,
                message = connectionErrorMessage(exception),
                sessionTitle = "",
                sessionId = null
            )
        }
    }

    private data class JoinResult(
        val success: Boolean,
        val message: String,
        val sessionTitle: String,
        val sessionId: Long?
    )

    private fun showExamWebsite() {
        setContentView(R.layout.activity_exam_web)
        applySystemBarsPadding(R.id.examWebMain)

        examWebView = findViewById<WebView>(R.id.examWebView).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return false

                    val currentHost = URL(EXAM_URL).host
                    val nextHost = runCatching { URL(url).host }.getOrNull()
                    val nextPath = runCatching { URL(url).path.lowercase() }.getOrDefault("")
                    val isExitLikeNavigation =
                        "logout" in nextPath ||
                            "keluar" in nextPath ||
                            "signout" in nextPath ||
                            "exit" in nextPath

                    return if (nextHost == currentHost && !isExitLikeNavigation) {
                        view?.loadUrl(url)
                        true
                    } else {
                        showExitPinDialog()
                        true
                    }
                }
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(EXAM_URL)
        }

        enterExamLockMode()

        findViewById<TextView>(R.id.exitExamButton).setOnClickListener {
            Snackbar.make(
                findViewById(R.id.examWebMain),
                "Membuka PIN keluar...",
                Snackbar.LENGTH_SHORT
            ).show()
            showExitPinDialog()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && examWebView != null) {
            showExitPinDialog()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun showExitPinDialog() {
        val exitPinInput = EditText(this).apply {
            hint = "PIN keluar"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            maxLines = 1
        }

        val container = FrameLayout(this).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(
                exitPinInput,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("PIN Keluar")
            .setView(container)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Keluar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val enteredPin = exitPinInput.text?.toString().orEmpty()
                val sessionId = activeSessionId
                if (sessionId == null) {
                    exitPinInput.error = "Sesi ujian belum terbaca."
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                Thread {
                    val result = verifyExitPin(sessionId, enteredPin)

                    runOnUiThread {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true

                        if (!result.success) {
                            exitPinInput.error = result.message
                            return@runOnUiThread
                        }

                        dialog.dismiss()
                        exitExamLockMode()
                        examWebView?.destroy()
                        examWebView = null
                        activeSessionId = null
                        finishAndRemoveTask()
                    }
                }.start()
            }
        }

        dialog.show()
    }

    private fun applySystemBarsPadding(rootId: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(rootId)) { view: View, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun enterExamLockMode() {
        hideSystemBars()
        runCatching { startLockTask() }
        scheduleLockTaskRetry()
    }

    private fun exitExamLockMode() {
        lockTaskRetryScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { stopLockTask() }
        showSystemBars()
    }

    private fun scheduleLockTaskRetry() {
        if (lockTaskRetryScheduled) return

        lockTaskRetryScheduled = true
        mainHandler.postDelayed({
            lockTaskRetryScheduled = false

            if (examWebView == null || isLockTaskActive()) return@postDelayed

            Snackbar.make(
                findViewById(R.id.examWebMain),
                "Mode ujian wajib diaktifkan.",
                Snackbar.LENGTH_SHORT
            ).show()

            enterExamLockMode()
        }, LOCK_TASK_RETRY_DELAY_MS)
    }

    private fun isLockTaskActive(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun verifyExitPin(sessionId: Long, pin: String): ExitResult {
        return try {
            val connection = (URL("$BASE_URL/api/student/exam-sessions/$sessionId/exit").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            connection.outputStream.use { output ->
                output.write(JSONObject().put("pin", pin).toString().toByteArray())
            }

            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()

            val json = JSONObject(responseBody)
            ExitResult(
                success = responseCode in 200..299,
                message = json.optString(
                    "message",
                    if (responseCode in 200..299) "PIN keluar benar." else "PIN keluar salah."
                )
            )
        } catch (exception: Exception) {
            ExitResult(
                success = false,
                message = connectionErrorMessage(exception)
            )
        }
    }

    private fun connectionErrorMessage(exception: Exception): String {
        return when (exception) {
            is SocketTimeoutException -> "Server tidak merespons. Pastikan backend aktif di $BASE_URL."
            is IOException -> "Tidak bisa terhubung ke server $BASE_URL."
            else -> "Respons server tidak valid: ${exception.message.orEmpty()}"
        }
    }

    private data class ExitResult(
        val success: Boolean,
        val message: String
    )

    override fun onResume() {
        super.onResume()
        if (examWebView != null) {
            enterExamLockMode()
        }
    }
}
