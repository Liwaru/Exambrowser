package com.example.exambrowser

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {
    private var currentSession: UserSession? = null
    private var activeMathPin = "------"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showLogin()
    }

    private fun showLogin() {
        setContentView(R.layout.activity_main)
        applySystemBarsPadding(R.id.main)

        val usernameLayout = findViewById<TextInputLayout>(R.id.usernameLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordLayout)
        val usernameInput = findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)

        loginButton.setOnClickListener {
            usernameLayout.error = null
            passwordLayout.error = null

            val username = usernameInput.text?.toString().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()

            if (username.isBlank()) {
                usernameLayout.error = "Username wajib diisi."
                return@setOnClickListener
            }

            if (password.isBlank()) {
                passwordLayout.error = "Password wajib diisi."
                return@setOnClickListener
            }

            val session = DemoAccess.login(username, password)
            if (session == null) {
                passwordLayout.error = "Username atau password salah."
                return@setOnClickListener
            }

            currentSession = session
            when (session.level) {
                UserLevel.STUDENT -> showPinInput(session)
                UserLevel.TEACHER -> showTeacherDashboard(session)
                UserLevel.ADMIN -> showAdminDashboard(session)
            }
        }
    }

    private fun showPinInput(session: UserSession) {
        setContentView(R.layout.activity_pin)
        applySystemBarsPadding(R.id.pinMain)

        val pinLayout = findViewById<TextInputLayout>(R.id.pinLayout)
        val pinInput = findViewById<TextInputEditText>(R.id.pinInput)
        val pinButton = findViewById<MaterialButton>(R.id.pinButton)
        val logoutButton = findViewById<MaterialButton>(R.id.logoutFromPinButton)

        pinButton.setOnClickListener {
            val pin = pinInput.text?.toString().orEmpty()
            if (pin.length != 6) {
                pinLayout.error = "PIN harus 6 digit."
                return@setOnClickListener
            }

            if (!DemoAccess.isValidExamPin(pin)) {
                pinLayout.error = "PIN tidak sesuai."
                return@setOnClickListener
            }

            pinLayout.error = null
            Snackbar.make(
                findViewById(R.id.pinMain),
                "${session.displayName} berhasil masuk ujian.",
                Snackbar.LENGTH_LONG
            ).show()
        }

        logoutButton.setOnClickListener { logout() }
    }

    private fun showTeacherDashboard(session: UserSession) {
        setContentView(R.layout.activity_dashboard)
        applySystemBarsPadding(R.id.dashboardMain)

        val teacherNameText = findViewById<TextView>(R.id.teacherNameText)
        val openMathSessionButton = findViewById<MaterialButton>(R.id.openMathSessionButton)
        val openIndoSessionButton = findViewById<MaterialButton>(R.id.openIndoSessionButton)
        val logoutButton = findViewById<MaterialButton>(R.id.logoutFromDashboardButton)

        teacherNameText.text = getString(R.string.dashboard_teacher_name, session.displayName)

        openMathSessionButton.setOnClickListener {
            showMathSessionDetail(session)
        }

        openIndoSessionButton.setOnClickListener {
            openSessionPlaceholder("Bahasa Indonesia", "10:30 - 12:00")
        }

        logoutButton.setOnClickListener { logout() }
    }

    private fun showMathSessionDetail(session: UserSession) {
        setContentView(R.layout.activity_session_detail)
        applySystemBarsPadding(R.id.sessionDetailMain)

        val supervisorText = findViewById<TextView>(R.id.sessionSupervisorText)
        val activePinText = findViewById<TextView>(R.id.activePinText)
        val generatePinButton = findViewById<MaterialButton>(R.id.generatePinButton)
        val backButton = findViewById<MaterialButton>(R.id.backToDashboardButton)
        val logoutButton = findViewById<MaterialButton>(R.id.logoutFromSessionButton)

        supervisorText.text = getString(R.string.session_supervisor, session.displayName)
        activePinText.text = activeMathPin

        generatePinButton.setOnClickListener {
            activeMathPin = "839204"
            activePinText.text = activeMathPin
            Snackbar.make(
                findViewById(R.id.sessionDetailMain),
                "PIN baru aktif. PIN lama otomatis nonaktif.",
                Snackbar.LENGTH_LONG
            ).show()
        }

        backButton.setOnClickListener { showTeacherDashboard(session) }
        logoutButton.setOnClickListener { logout() }
    }

    private fun openSessionPlaceholder(subject: String, time: String) {
        Snackbar.make(
            findViewById(R.id.dashboardMain),
            "Membuka detail sesi $subject ($time).",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showAdminDashboard(session: UserSession) {
        setContentView(R.layout.activity_admin_dashboard)
        applySystemBarsPadding(R.id.adminContent)

        val drawerLayout = findViewById<DrawerLayout>(R.id.adminDrawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.adminToolbar)
        val navigationView = findViewById<NavigationView>(R.id.adminNavigationView)
        val adminNameText = findViewById<TextView>(R.id.adminNameText)

        adminNameText.text = getString(R.string.admin_login_as, session.displayName)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.menu_logout -> {
                    logout()
                    true
                }
                else -> {
                    Snackbar.make(
                        findViewById(R.id.adminContent),
                        "${item.title} dibuka.",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        }
    }

    private fun logout() {
        currentSession = null
        showLogin()
    }

    private fun applySystemBarsPadding(rootId: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(rootId)) { view: View, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
