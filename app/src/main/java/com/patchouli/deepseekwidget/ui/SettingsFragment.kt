package com.patchouli.deepseekwidget.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.net.ApiResult
import com.patchouli.deepseekwidget.net.CostsResult
import com.patchouli.deepseekwidget.net.DeepSeekApi
import com.patchouli.deepseekwidget.net.OpenAiCostsApi
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.repo.BalanceRepository
import com.patchouli.deepseekwidget.util.Format
import com.patchouli.deepseekwidget.widget.WidgetUpdater
import com.patchouli.deepseekwidget.work.RefreshScheduler
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SettingsFragment : Fragment() {

    private lateinit var providerGroup: MaterialButtonToggleGroup
    private lateinit var providerSelector: ProviderSelector
    private lateinit var providerNote: TextView
    private lateinit var deepSeekSettings: View
    private lateinit var openAiSettings: View

    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var apiKeyHelper: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var testButton: MaterialButton
    private lateinit var removeKeyButton: MaterialButton
    private lateinit var statusRow: View
    private lateinit var statusDot: ImageView
    private lateinit var statusText: TextView
    private lateinit var thresholdSlider: Slider
    private lateinit var thresholdValue: TextView
    private lateinit var thresholdState: TextView
    private lateinit var notificationRow: View
    private lateinit var notificationsButton: MaterialButton
    private lateinit var intervalGroup: MaterialButtonToggleGroup
    private lateinit var versionValue: TextView

    private lateinit var openAiKeyLayout: TextInputLayout
    private lateinit var openAiKeyInput: TextInputEditText
    private lateinit var openAiKeyHelper: TextView
    private lateinit var openAiKeyWarning: TextView
    private lateinit var openAiSaveButton: MaterialButton
    private lateinit var openAiTestButton: MaterialButton
    private lateinit var openAiRemoveButton: MaterialButton
    private lateinit var openAiStatusRow: View
    private lateinit var openAiStatusDot: ImageView
    private lateinit var openAiStatusText: TextView

    private var selectedTab = Provider.DEEPSEEK
    private var isTesting = false
    private var isOpenAiTesting = false
    private var binding = false

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateNotificationRow()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        providerGroup = view.findViewById(R.id.provider_group)
        providerNote = view.findViewById(R.id.provider_note)
        deepSeekSettings = view.findViewById(R.id.deepseek_settings)
        openAiSettings = view.findViewById(R.id.openai_settings)

        apiKeyLayout = view.findViewById(R.id.api_key_layout)
        apiKeyInput = view.findViewById(R.id.api_key_input)
        apiKeyHelper = view.findViewById(R.id.api_key_helper)
        saveButton = view.findViewById(R.id.save_button)
        testButton = view.findViewById(R.id.test_button)
        removeKeyButton = view.findViewById(R.id.remove_key_button)
        statusRow = view.findViewById(R.id.settings_status_row)
        statusDot = view.findViewById(R.id.settings_status_dot)
        statusText = view.findViewById(R.id.settings_status)
        thresholdSlider = view.findViewById(R.id.threshold_slider)
        thresholdValue = view.findViewById(R.id.threshold_value)
        thresholdState = view.findViewById(R.id.threshold_state)
        notificationRow = view.findViewById(R.id.notification_row)
        notificationsButton = view.findViewById(R.id.notifications_button)
        intervalGroup = view.findViewById(R.id.interval_group)
        versionValue = view.findViewById(R.id.version_value)

        openAiKeyLayout = view.findViewById(R.id.openai_key_layout)
        openAiKeyInput = view.findViewById(R.id.openai_key_input)
        openAiKeyHelper = view.findViewById(R.id.openai_key_helper)
        openAiKeyWarning = view.findViewById(R.id.openai_key_warning)
        openAiSaveButton = view.findViewById(R.id.openai_save_button)
        openAiTestButton = view.findViewById(R.id.openai_test_button)
        openAiRemoveButton = view.findViewById(R.id.openai_remove_button)
        openAiStatusRow = view.findViewById(R.id.openai_status_row)
        openAiStatusDot = view.findViewById(R.id.openai_status_dot)
        openAiStatusText = view.findViewById(R.id.openai_status)

        val prefs = Prefs(requireContext())
        selectedTab = savedInstanceState?.getString(KEY_TAB)?.let { Provider.fromStorage(it) }
            ?: arguments?.getString(ARG_PROVIDER)?.let { Provider.fromStorage(it) }
            ?: prefs.activeProviderOrNull()
            ?: Provider.DEEPSEEK

        providerSelector = ProviderSelector(
            group = providerGroup,
            prefs = prefs,
            persistSelection = false,
            onSelected = { provider ->
                selectedTab = provider
                bindProviderTab()
            },
            onNeedsSettings = { provider ->
                selectedTab = provider
                bindProviderTab()
            }
        )

        statusRow.visibility = View.GONE
        openAiStatusRow.visibility = View.GONE
        apiKeyInput.addTextChangedListener(deepKeyWatcher)
        openAiKeyInput.addTextChangedListener(openAiKeyWatcher)

        saveButton.setOnClickListener { saveDeepSeekKey() }
        testButton.setOnClickListener { testDeepSeekConnection() }
        removeKeyButton.setOnClickListener { confirmRemoveDeepSeekKey() }
        notificationsButton.setOnClickListener { openNotificationPermissionOrSettings() }

        openAiSaveButton.setOnClickListener { saveOpenAiKey() }
        openAiTestButton.setOnClickListener { testOpenAiConnection() }
        openAiRemoveButton.setOnClickListener { confirmRemoveOpenAiKey() }

        thresholdSlider.addOnChangeListener { _, value, fromUser ->
            updateThresholdLabel(value)
            updateThresholdState(value)
            if (fromUser) {
                Prefs(requireContext()).setThreshold(
                    BigDecimal.valueOf(value.toLong()).setScale(2, RoundingMode.HALF_UP)
                )
            }
        }
        thresholdSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                saveThreshold(slider.value)
            }
        })

        intervalGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (binding || !isChecked) return@addOnButtonCheckedListener
            val minutes = when (checkedId) {
                R.id.interval_15 -> 15
                R.id.interval_30 -> 30
                else -> 60
            }
            Prefs(requireContext()).setIntervalMinutes(minutes)
            scheduleIfAnyKey()
        }

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
        updateNotificationRow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_TAB, selectedTab.storageKey)
        super.onSaveInstanceState(outState)
    }

    private val deepKeyWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (binding || selectedTab != Provider.DEEPSEEK) return
            val hasText = !s.isNullOrBlank()
            val hasKey = Prefs(requireContext()).hasApiKey(Provider.DEEPSEEK)
            apiKeyHelper.text = getString(
                when {
                    hasText -> R.string.settings_api_key_helper_replace
                    hasKey -> R.string.settings_api_key_helper_saved
                    else -> R.string.settings_api_key_helper_new
                }
            )
            updateButtons()
        }
    }

    private val openAiKeyWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (binding || selectedTab != Provider.OPENAI) return
            val key = s?.toString()?.trim().orEmpty()
            openAiKeyWarning.visibility = if (key.isNotEmpty() && looksLikeUserKey(key)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            updateButtons()
        }
    }

    private fun load() {
        if (!isAdded) return
        binding = true
        val prefs = Prefs(requireContext())
        val active = prefs.activeProviderOrNull()

        apiKeyInput.setText("")
        openAiKeyInput.setText("")
        bindDeepSeekFields(prefs)
        bindOpenAiFields(prefs)

        val threshold = prefs.getThreshold()
            .max(BigDecimal.ZERO)
            .min(BigDecimal("100"))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
        thresholdSlider.value = threshold.toFloat()
        updateThresholdLabel(threshold.toFloat())
        updateThresholdState(threshold.toFloat())

        intervalGroup.check(
            when (prefs.getIntervalMinutes()) {
                15 -> R.id.interval_15
                30 -> R.id.interval_30
                else -> R.id.interval_60
            }
        )
        versionValue.text = versionLabel()
        binding = false

        providerSelector.render(active, selectedTab)
        providerNote.visibility = if (active != null && active != selectedTab) {
            providerNote.text = getString(
                R.string.provider_note_different,
                getString(labelRes(selectedTab)),
                getString(labelRes(active))
            )
            View.VISIBLE
        } else {
            View.GONE
        }
        bindProviderTab()
        updateNotificationRow()
    }

    private fun bindDeepSeekFields(prefs: Prefs) {
        val hasKey = prefs.hasApiKey(Provider.DEEPSEEK)
        apiKeyLayout.hint = getString(
            if (hasKey) R.string.settings_api_key_new_hint else R.string.settings_api_key_hint
        )
        apiKeyHelper.text = getString(
            if (hasKey) R.string.settings_api_key_helper_saved
            else R.string.settings_api_key_helper_new
        )
        removeKeyButton.visibility = if (hasKey) View.VISIBLE else View.GONE
    }

    private fun bindOpenAiFields(prefs: Prefs) {
        val hasKey = prefs.hasApiKey(Provider.OPENAI)
        openAiKeyLayout.hint = getString(
            if (hasKey) R.string.settings_openai_key_new_hint else R.string.settings_openai_key_hint
        )
        openAiKeyHelper.text = getString(
            if (hasKey) R.string.settings_openai_key_helper_saved
            else R.string.settings_openai_key_helper_new
        )
        openAiKeyWarning.visibility = View.GONE
        openAiRemoveButton.visibility = if (hasKey) View.VISIBLE else View.GONE
    }

    private fun bindProviderTab() {
        if (!isAdded) return
        val active = Prefs(requireContext()).activeProviderOrNull()
        deepSeekSettings.visibility =
            if (selectedTab == Provider.DEEPSEEK) View.VISIBLE else View.GONE
        openAiSettings.visibility =
            if (selectedTab == Provider.OPENAI) View.VISIBLE else View.GONE
        providerNote.visibility = if (active != null && active != selectedTab) {
            providerNote.text = getString(
                R.string.provider_note_different,
                getString(labelRes(selectedTab)),
                getString(labelRes(active))
            )
            View.VISIBLE
        } else {
            View.GONE
        }
        providerSelector.render(active, selectedTab)
        updateButtons()
    }

    private fun updateButtons() {
        if (!isAdded) return
        val prefs = Prefs(requireContext())
        val hasDeepKey = prefs.hasApiKey(Provider.DEEPSEEK)
        val hasOpenAiKey = prefs.hasApiKey(Provider.OPENAI)
        val deepText = !apiKeyInput.text.isNullOrBlank()
        val openAiText = !openAiKeyInput.text.isNullOrBlank()
        saveButton.isEnabled = !isTesting && deepText
        testButton.isEnabled = !isTesting && hasDeepKey && !deepText
        removeKeyButton.isEnabled = !isTesting && hasDeepKey
        openAiSaveButton.isEnabled = !isOpenAiTesting && openAiText
        openAiTestButton.isEnabled = !isOpenAiTesting && hasOpenAiKey && !openAiText
        openAiRemoveButton.isEnabled = !isOpenAiTesting && hasOpenAiKey
    }

    // --- DeepSeek -------------------------------------------------------

    private fun saveDeepSeekKey() {
        if (!isAdded) return
        val key = apiKeyInput.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) return
        val prefs = Prefs(requireContext())
        if (!prefs.setApiKey(Provider.DEEPSEEK, key)) {
            showDeepSeekStatus(getString(R.string.settings_key_save_error), StatusKind.ERROR)
            return
        }
        prefs.setLastError(null, Provider.DEEPSEEK)
        if (prefs.activeProviderOrNull() == null) prefs.setActiveProvider(Provider.DEEPSEEK)
        scheduleIfAnyKey()
        if (prefs.getActiveProvider() == Provider.DEEPSEEK) RefreshScheduler.refreshNow(requireContext(), Provider.DEEPSEEK)
        WidgetUpdater.updateAll(requireContext())

        binding = true
        apiKeyInput.setText("")
        binding = false
        apiKeyLayout.hint = getString(R.string.settings_api_key_new_hint)
        apiKeyHelper.text = getString(R.string.settings_api_key_helper_saved)
        removeKeyButton.visibility = View.VISIBLE
        updateButtons()
        testDeepSeekConnection()
    }

    private fun confirmRemoveDeepSeekKey() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_remove_title)
            .setMessage(R.string.settings_remove_body)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_remove_confirm) { _, _ -> removeDeepSeekKey() }
            .show()
    }

    private fun removeDeepSeekKey() {
        val prefs = Prefs(requireContext())
        val wasActive = prefs.activeProviderOrNull() == Provider.DEEPSEEK
        prefs.setApiKey(Provider.DEEPSEEK, "")
        prefs.setLastError(Prefs.ERROR_MISSING_KEY, Provider.DEEPSEEK)
        prefs.setBelowThreshold(false)
        if (wasActive) fallbackAfterRemoval(prefs, Provider.DEEPSEEK)
        scheduleIfAnyKey()
        WidgetUpdater.updateAll(requireContext())
        binding = true
        apiKeyInput.setText("")
        binding = false
        apiKeyLayout.hint = getString(R.string.settings_api_key_hint)
        apiKeyHelper.text = getString(R.string.settings_api_key_helper_new)
        removeKeyButton.visibility = View.GONE
        load()
        showDeepSeekStatus(getString(R.string.settings_key_removed), StatusKind.WARNING)
    }

    private fun testDeepSeekConnection() {
        if (!isAdded || isTesting) return
        val key = Prefs(requireContext()).getApiKey(Provider.DEEPSEEK)
        if (key.isNullOrBlank()) {
            showDeepSeekStatus(getString(R.string.settings_test_missing), StatusKind.WARNING)
            return
        }
        setTesting(true)
        showDeepSeekStatus(getString(R.string.settings_test_running), StatusKind.ACCENT)
        val api = DeepSeekApi()
        executor.execute {
            val result = api.fetch(key)
            mainHandler.post {
                if (!isAdded || view == null) return@post
                setTesting(false)
                when (result) {
                    is ApiResult.Success -> showDeepSeekStatus(
                        getString(R.string.settings_test_success, Format.money(result.balance.total)),
                        StatusKind.POSITIVE
                    )
                    ApiResult.Unauthorized -> showDeepSeekStatus(
                        getString(R.string.settings_test_auth),
                        StatusKind.ERROR
                    )
                    is ApiResult.NetworkError -> showDeepSeekStatus(
                        getString(R.string.settings_test_network),
                        StatusKind.ERROR
                    )
                    is ApiResult.Unexpected -> showDeepSeekStatus(
                        getString(R.string.settings_test_unexpected),
                        StatusKind.WARNING
                    )
                    ApiResult.MissingKey -> showDeepSeekStatus(
                        getString(R.string.settings_test_missing),
                        StatusKind.WARNING
                    )
                }
            }
        }
    }

    // --- OpenAI ---------------------------------------------------------

    private fun saveOpenAiKey() {
        if (!isAdded) return
        val key = openAiKeyInput.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) return
        if (looksLikeUserKey(key)) {
            openAiKeyWarning.visibility = View.VISIBLE
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_openai_save_anyway_title)
                .setMessage(R.string.settings_openai_save_anyway_body)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_openai_save_anyway) { _, _ ->
                    persistOpenAiKey(key)
                }
                .show()
        } else {
            persistOpenAiKey(key)
        }
    }

    private fun persistOpenAiKey(key: String) {
        val prefs = Prefs(requireContext())
        if (!prefs.setApiKey(Provider.OPENAI, key)) {
            showOpenAiStatus(getString(R.string.settings_key_save_error), StatusKind.ERROR)
            return
        }
        prefs.setLastError(null, Provider.OPENAI)
        if (prefs.activeProviderOrNull() == null) prefs.setActiveProvider(Provider.OPENAI)
        scheduleIfAnyKey()
        if (prefs.getActiveProvider() == Provider.OPENAI) {
            RefreshScheduler.refreshNow(requireContext(), Provider.OPENAI)
        }
        binding = true
        openAiKeyInput.setText("")
        binding = false
        openAiKeyWarning.visibility = View.GONE
        openAiKeyLayout.hint = getString(R.string.settings_openai_key_new_hint)
        openAiKeyHelper.text = getString(R.string.settings_openai_key_helper_saved)
        openAiRemoveButton.visibility = View.VISIBLE
        updateButtons()
        testOpenAiConnection()
    }

    private fun confirmRemoveOpenAiKey() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_openai_remove_title)
            .setMessage(R.string.settings_openai_remove_body)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_remove_confirm) { _, _ -> removeOpenAiKey() }
            .show()
    }

    private fun removeOpenAiKey() {
        val prefs = Prefs(requireContext())
        val wasActive = prefs.activeProviderOrNull() == Provider.OPENAI
        prefs.setApiKey(Provider.OPENAI, "")
        prefs.setLastError(Prefs.ERROR_MISSING_KEY, Provider.OPENAI)
        if (wasActive) fallbackAfterRemoval(prefs, Provider.OPENAI)
        scheduleIfAnyKey()
        load()
        showOpenAiStatus(getString(R.string.settings_key_removed), StatusKind.WARNING)
    }

    private fun testOpenAiConnection() {
        if (!isAdded || isOpenAiTesting) return
        val key = Prefs(requireContext()).getApiKey(Provider.OPENAI)
        if (key.isNullOrBlank()) {
            showOpenAiStatus(getString(R.string.settings_test_missing), StatusKind.WARNING)
            return
        }
        setOpenAiTesting(true)
        showOpenAiStatus(getString(R.string.settings_test_running), StatusKind.ACCENT)
        val nowSec = System.currentTimeMillis() / 1000L
        val start = (nowSec / 3600L - 1L) * 3600L
        val api = OpenAiCostsApi(maxPages = 1)
        executor.execute {
            val result = api.fetch(key, start, "1h", 1)
            mainHandler.post {
                if (!isAdded || view == null) return@post
                setOpenAiTesting(false)
                when (result) {
                    is CostsResult.Success -> showOpenAiStatus(
                        getString(R.string.settings_openai_test_success),
                        StatusKind.POSITIVE
                    )
                    CostsResult.Forbidden -> showOpenAiStatus(
                        getString(R.string.settings_openai_test_forbidden),
                        StatusKind.ERROR
                    )
                    CostsResult.Unauthorized -> showOpenAiStatus(
                        getString(R.string.settings_openai_test_auth),
                        StatusKind.ERROR
                    )
                    is CostsResult.NetworkError -> showOpenAiStatus(
                        getString(R.string.settings_test_network),
                        StatusKind.ERROR
                    )
                    is CostsResult.Unexpected -> showOpenAiStatus(
                        getString(R.string.settings_test_unexpected),
                        StatusKind.WARNING
                    )
                    CostsResult.MissingKey -> showOpenAiStatus(
                        getString(R.string.settings_test_missing),
                        StatusKind.WARNING
                    )
                }
            }
        }
    }

    private fun looksLikeUserKey(key: String): Boolean =
        key.startsWith("sk-") && !key.startsWith("sk-admin-")

    private fun fallbackAfterRemoval(prefs: Prefs, removed: Provider) {
        val other = if (removed == Provider.DEEPSEEK) Provider.OPENAI else Provider.DEEPSEEK
        if (prefs.hasApiKey(other)) {
            prefs.setActiveProvider(other)
            Snackbar.make(
                requireView(),
                getString(R.string.provider_switched, getString(labelRes(other))),
                Snackbar.LENGTH_SHORT
            ).show()
        } else {
            prefs.clearActiveProvider()
        }
    }

    // --- Comuni ---------------------------------------------------------

    private fun setTesting(testing: Boolean) {
        isTesting = testing
        testButton.text = getString(
            if (testing) R.string.settings_test_running else R.string.settings_test
        )
        updateButtons()
    }

    private fun setOpenAiTesting(testing: Boolean) {
        isOpenAiTesting = testing
        openAiTestButton.text = getString(
            if (testing) R.string.settings_test_running else R.string.settings_test
        )
        updateButtons()
    }

    private fun saveThreshold(value: Float) {
        val threshold = BigDecimal.valueOf(value.toLong()).setScale(2, RoundingMode.HALF_UP)
        Prefs(requireContext()).setThreshold(threshold)
        WidgetUpdater.updateAll(requireContext())
        updateThresholdState(value)
    }

    private fun updateThresholdLabel(value: Float) {
        thresholdValue.text = Format.money(BigDecimal.valueOf(value.toLong()))
    }

    private fun updateThresholdState(value: Float) {
        if (!isAdded) return
        val snapshot = BalanceRepository(requireContext()).latestSnapshot()
        val below = snapshot != null && snapshot.total < BigDecimal.valueOf(value.toLong())
        thresholdState.visibility = if (below) View.VISIBLE else View.GONE
    }

    private fun scheduleIfAnyKey() {
        val prefs = Prefs(requireContext())
        if (prefs.hasApiKey(Provider.DEEPSEEK) || prefs.hasApiKey(Provider.OPENAI)) {
            RefreshScheduler.schedulePeriodic(requireContext())
        } else {
            RefreshScheduler.cancel(requireContext())
        }
    }

    private fun updateNotificationRow() {
        if (!isAdded) return
        val enabled = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        notificationRow.visibility = if (enabled) View.GONE else View.VISIBLE
        if (enabled) return
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        notificationsButton.setText(
            if (needsPermission) R.string.settings_notifications_enable
            else R.string.settings_notifications_open
        )
    }

    private fun openNotificationPermissionOrSettings() {
        if (!isAdded) return
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        }
    }

    private fun versionLabel(): String {
        return try {
            val info = requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            )
            getString(
                R.string.settings_version_value,
                info.versionName ?: "",
                PackageInfoCompat.getLongVersionCode(info).toString()
            )
        } catch (_: Exception) {
            getString(R.string.settings_version_value, "1.0.0", "1")
        }
    }

    private fun showDeepSeekStatus(text: String, kind: StatusKind) {
        showStatusRow(statusRow, statusDot, statusText, text, kind)
    }

    private fun showOpenAiStatus(text: String, kind: StatusKind) {
        showStatusRow(openAiStatusRow, openAiStatusDot, openAiStatusText, text, kind)
    }

    private fun showStatusRow(
        row: View,
        dot: ImageView,
        textView: TextView,
        text: String,
        kind: StatusKind
    ) {
        if (!isAdded) return
        row.visibility = View.VISIBLE
        textView.text = text
        val context = requireContext()
        val (dotRes, color) = when (kind) {
            StatusKind.POSITIVE -> R.drawable.dot_positive to R.color.ds_color_positive
            StatusKind.WARNING -> R.drawable.dot_warning to R.color.ds_color_warning
            StatusKind.ERROR -> R.drawable.dot_error to R.color.ds_color_error
            StatusKind.ACCENT -> R.drawable.dot_accent to R.color.ds_color_accent
        }
        dot.setImageResource(dotRes)
        textView.setTextColor(ContextCompat.getColor(context, color))
    }

    private fun labelRes(provider: Provider): Int = when (provider) {
        Provider.DEEPSEEK -> R.string.provider_deepseek
        Provider.OPENAI -> R.string.provider_openai
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private enum class StatusKind {
        POSITIVE,
        WARNING,
        ERROR,
        ACCENT
    }

    companion object {
        private const val ARG_PROVIDER = "settings_provider"
        private const val KEY_TAB = "settings_tab"

        fun newInstance(provider: Provider): SettingsFragment = SettingsFragment().apply {
            arguments = Bundle().apply { putString(ARG_PROVIDER, provider.storageKey) }
        }
    }
}
