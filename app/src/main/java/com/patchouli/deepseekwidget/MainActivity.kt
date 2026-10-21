package com.patchouli.deepseekwidget

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.notify.AlertNotifier
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.ui.DashboardFragment
import com.patchouli.deepseekwidget.ui.HistoryFragment
import com.patchouli.deepseekwidget.ui.SettingsFragment
import com.patchouli.deepseekwidget.work.RefreshScheduler
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var backCallback: OnBackPressedCallback
    private var currentScreen = Screen.DASHBOARD
    private var pendingSettingsProvider: Provider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            root.isForceDarkAllowed = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)

        toolbar = findViewById(R.id.toolbar)
        AlertNotifier.ensureChannel(this)

        val restoredScreen = savedInstanceState?.getString(STATE_SCREEN)
            ?.let { runCatching { Screen.valueOf(it) }.getOrNull() }
        currentScreen = restoredScreen ?: screenFromIntent(intent)
        pendingSettingsProvider = providerFromIntent(intent)

        backCallback = object : OnBackPressedCallback(currentScreen != Screen.DASHBOARD) {
            override fun handleOnBackPressed() {
                showDashboard()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        if (savedInstanceState == null) {
            showScreen(currentScreen, animate = false)
        } else {
            applyChrome()
        }

        if (Prefs(this).hasApiKey()) {
            RefreshScheduler.schedulePeriodic(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_SCREEN) == SCREEN_SETTINGS) {
            pendingSettingsProvider = providerFromIntent(intent)
            showSettings(pendingSettingsProvider ?: Prefs(this).getActiveProvider())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SCREEN, currentScreen.name)
        super.onSaveInstanceState(outState)
    }

    fun showDashboard() {
        showScreen(Screen.DASHBOARD)
    }

    fun showHistory() {
        showScreen(Screen.HISTORY)
    }

    fun showSettings() {
        showScreen(Screen.SETTINGS)
    }

    fun showSettings(provider: Provider) {
        showScreen(Screen.SETTINGS, settingsProvider = provider)
    }

    private fun showScreen(
        screen: Screen,
        animate: Boolean = true,
        settingsProvider: Provider? = null
    ) {
        val mustRebuildSettings = screen == Screen.SETTINGS && settingsProvider != null
        if (screen == currentScreen &&
            supportFragmentManager.findFragmentById(R.id.fragment_container) != null &&
            !mustRebuildSettings
        ) {
            applyChrome()
            return
        }
        val fragment: Fragment = when (screen) {
            Screen.DASHBOARD -> DashboardFragment()
            Screen.HISTORY -> HistoryFragment()
            Screen.SETTINGS -> SettingsFragment.newInstance(
                settingsProvider ?: pendingSettingsProvider
                ?: Prefs(this).activeProviderOrNull() ?: Prefs(this).getActiveProvider()
            )
        }
        val transaction = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment)
        if (animate) {
            transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        transaction.commit()
        currentScreen = screen
        if (screen == Screen.SETTINGS) pendingSettingsProvider = null
        applyChrome()
    }

    private fun applyChrome() {
        toolbar.menu.clear()
        backCallback.isEnabled = currentScreen != Screen.DASHBOARD

        when (currentScreen) {
            Screen.DASHBOARD -> {
                toolbar.title = getString(R.string.dashboard_title)
                toolbar.setNavigationIcon(null)
                toolbar.setNavigationOnClickListener(null)
                toolbar.inflateMenu(R.menu.dashboard_menu)
                toolbar.menu.findItem(R.id.action_history)?.setOnMenuItemClickListener {
                    showHistory()
                    true
                }
                toolbar.menu.findItem(R.id.action_settings)?.setOnMenuItemClickListener {
                    showSettings()
                    true
                }
                tintMenuIcons()
            }

            Screen.HISTORY -> {
                toolbar.title = getString(R.string.history_title)
                configureBackNavigation()
            }

            Screen.SETTINGS -> {
                toolbar.title = getString(R.string.settings_title)
                configureBackNavigation()
            }
        }
    }

    private fun configureBackNavigation() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.navigationIcon?.mutate()?.let {
            DrawableCompat.setTint(it, ContextCompat.getColor(this, R.color.ds_color_text_primary))
        }
        toolbar.setNavigationContentDescription(R.string.cd_back)
        toolbar.setNavigationOnClickListener { showDashboard() }
    }

    private fun tintMenuIcons() {
        val color = ContextCompat.getColor(this, R.color.ds_color_text_secondary)
        for (index in 0 until toolbar.menu.size()) {
            toolbar.menu.getItem(index).icon?.mutate()?.let {
                DrawableCompat.setTint(it, color)
            }
        }
    }

    private fun screenFromIntent(intent: Intent?): Screen =
        if (intent?.getStringExtra(EXTRA_SCREEN) == SCREEN_SETTINGS) Screen.SETTINGS else Screen.DASHBOARD

    private fun providerFromIntent(intent: Intent?): Provider? =
        intent?.getStringExtra(EXTRA_PROVIDER)?.let { Provider.fromStorage(it) }

    private enum class Screen {
        DASHBOARD,
        HISTORY,
        SETTINGS
    }

    companion object {
        const val EXTRA_SCREEN = "com.patchouli.deepseekwidget.extra.SCREEN"
        const val SCREEN_SETTINGS = "settings"
        const val EXTRA_PROVIDER = "com.patchouli.deepseekwidget.extra.PROVIDER"
        private const val STATE_SCREEN = "state_screen"
    }
}
