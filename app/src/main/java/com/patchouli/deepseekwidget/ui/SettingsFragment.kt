package com.patchouli.deepseekwidget.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.MetricAffectingSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    private lateinit var creditSection: TextView
    private lateinit var creditAmountLayout: TextInputLayout
    private lateinit var creditAmountInput: TextInputEditText
    private lateinit var creditDateRow: View
    private lateinit var creditDateValue: TextView
    private lateinit var creditHelper: TextView
    private lateinit var creditSaveButton: MaterialButton
    private lateinit var creditRemoveButton: MaterialButton
    private lateinit var creditStatusRow: View
    private lateinit var creditStatusDot: ImageView
    private lateinit var creditStatusText: TextView
    private lateinit var creditNote: TextView
    private lateinit var creditRealLine: TextView

    private var selectedTab = Provider.DEEPSEEK
    private var isTesting = false
    private var isOpenAiTesting = false
    private var binding = false
    private var creditRefDate: LocalDate = LocalDate.now(ZoneOffset.UTC)

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

        creditSection = view.findViewById(R.id.openai_credit_section)
        creditAmountLayout = view.findViewById(R.id.openai_credit_amount_layout)
        creditAmountInput = view.findViewById(R.id.openai_credit_amount_input)
        creditDateRow = view.findViewById(R.id.openai_credit_date_row)
        creditDateValue = view.findViewById(R.id.openai_credit_date_value)
        creditHelper = view.findViewById(R.id.openai_credit_helper)
        creditSaveButton = view.findViewById(R.id.openai_credit_save_button)
        creditRemoveButton = view.findViewById(R.id.openai_credit_remove_button)
        creditStatusRow = view.findViewById(R.id.openai_credit_status_row)
        creditStatusDot = view.findViewById(R.id.openai_credit_status_dot)
        creditStatusText = view.findViewById(R.id.openai_credit_status)
        creditNote = view.findViewById(R.id.openai_credit_note)
        creditRealLine = view.findViewById(R.id.openai_credit_real_line)

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
        creditStatusRow.visibility = View.GONE
        apiKeyInput.addTextChangedListener(deepKeyWatcher)
        openAiKeyInput.addTextChangedListener(openAiKeyWatcher)
        creditAmountInput.addTextChangedListener(creditAmountWatcher)

        saveButton.setOnClickListener { saveDeepSeekKey() }
        testButton.setOnClickListener { testDeepSeekConnection() }
        removeKeyButton.setOnClickListener { confirmRemoveDeepSeekKey() }
        notificationsButton.setOnClickListener { openNotificationPermissionOrSettings() }

        openAiSaveButton.setOnClickListener { saveOpenAiKey() }
        openAiTestButton.setOnClickListener { testOpenAiConnection() }
        openAiRemoveButton.setOnClickListener { confirmRemoveOpenAiKey() }

        creditDateRow.setOnClickListener { showCreditDatePicker() }
        creditSaveButton.setOnClickListener { saveCreditAnchor() }
        creditRemoveButton.setOnClickListener { confirmRemoveCreditAnchor() }

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

        if (arguments?.getBoolean(ARG_FOCUS_CREDIT) == true) {
            view.post { scrollToCreditSection(view) }
        }
    }

    /**
     * Arrivo dalla card "da impostare" in Dashboard: porta al campo del
     * riferimento, non solo alla schermata delle impostazioni.
     */
    private fun scrollToCreditSection(root: View) {
        if (!isAdded || openAiSettings.visibility != View.VISIBLE) return
        val scroll = root as? ScrollView ?: return
        val section = IntArray(2)
        creditSection.getLocationInWindow(section)
        val origin = IntArray(2)
        scroll.getLocationInWindow(origin)
        val margin = (16 * resources.displayMetrics.density).toInt()
        scroll.smoothScrollTo(0, (scroll.scrollY + section[1] - origin[1] - margin).coerceAtLeast(0))
        creditAmountInput.requestFocus()
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

    private val creditAmountWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (binding) return
            updateCreditAmountError()
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
        bindOpenAiCreditFields(prefs)

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
        val keyState = prefs.keyState(Provider.DEEPSEEK)
        val hasKey = keyState == Prefs.KeyState.READABLE
        apiKeyLayout.hint = getString(
            if (hasKey) R.string.settings_api_key_new_hint else R.string.settings_api_key_hint
        )
        apiKeyHelper.text = getString(
            when (keyState) {
                Prefs.KeyState.READABLE -> R.string.settings_api_key_helper_saved
                Prefs.KeyState.UNREADABLE -> R.string.settings_key_unreadable
                Prefs.KeyState.MISSING -> R.string.settings_api_key_helper_new
            }
        )
        apiKeyHelper.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (keyState == Prefs.KeyState.UNREADABLE) R.color.ds_color_error
                else R.color.ds_color_text_secondary
            )
        )
        removeKeyButton.visibility = if (keyState != Prefs.KeyState.MISSING) View.VISIBLE else View.GONE
    }

    private fun bindOpenAiFields(prefs: Prefs) {
        val keyState = prefs.keyState(Provider.OPENAI)
        val hasKey = keyState == Prefs.KeyState.READABLE
        openAiKeyLayout.hint = getString(
            if (hasKey) R.string.settings_openai_key_new_hint else R.string.settings_openai_key_hint
        )
        openAiKeyHelper.text = getString(
            when (keyState) {
                Prefs.KeyState.READABLE -> R.string.settings_openai_key_helper_saved
                Prefs.KeyState.UNREADABLE -> R.string.settings_key_unreadable
                Prefs.KeyState.MISSING -> R.string.settings_openai_key_helper_new
            }
        )
        openAiKeyHelper.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (keyState == Prefs.KeyState.UNREADABLE) R.color.ds_color_error
                else R.color.ds_color_text_secondary
            )
        )
        openAiKeyWarning.visibility = View.GONE
        openAiRemoveButton.visibility = if (keyState != Prefs.KeyState.MISSING) View.VISIBLE else View.GONE
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
        creditSaveButton.isEnabled = creditAmountIsValid()
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
                        getString(R.string.settings_test_network_detail, result.detail),
                        StatusKind.ERROR
                    )
                    is ApiResult.Unexpected -> showDeepSeekStatus(
                        getString(R.string.settings_test_unexpected_detail, result.detail),
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
        // La sostituzione della key cancella il reale in cache: la riga sparisce.
        refreshCreditRealLine(prefs)
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
        val start = (nowSec / 86_400L - 7L) * 86_400L
        val api = OpenAiCostsApi(maxPages = 1)
        executor.execute {
            val result = api.fetch(key, start, limit = 1)
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
                        getString(R.string.settings_test_network_detail, result.detail),
                        StatusKind.ERROR
                    )
                    is CostsResult.Unexpected -> showOpenAiStatus(
                        getString(R.string.settings_test_unexpected_detail, result.detail),
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

    // --- Credito residuo OpenAI -----------------------------------------

    private fun bindOpenAiCreditFields(prefs: Prefs) {
        if (!isAdded) return
        val today = LocalDate.now(ZoneOffset.UTC)
        val anchorUsd = prefs.getOpenAiCreditAnchorUsd()
        val anchorTs = prefs.getOpenAiCreditAnchorTs()
        val hasAnchor = anchorUsd != null && anchorTs > 0L

        if (hasAnchor) {
            creditAmountInput.setText(anchorUsd!!.stripTrailingZeros().toPlainString())
            creditRefDate = Instant.ofEpochMilli(anchorTs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
        } else {
            creditAmountInput.setText("")
            creditRefDate = today
        }
        creditAmountLayout.error = null
        renderCreditDate()
        creditRemoveButton.visibility = if (hasAnchor) View.VISIBLE else View.GONE
        refreshCreditRealLine(prefs)
        creditStatusRow.visibility = View.GONE
        // Un riferimento salvato nel futuro resta modificabile ma non è usato
        // dalla stima: lo segnaliamo qui, senza banner in Dashboard.
        if (hasAnchor && creditRefDate.isAfter(today)) {
            showCreditStatus(
                getString(R.string.settings_openai_credit_error_clock),
                StatusKind.ERROR
            )
        }
    }

    private fun parseCreditAmount(): BigDecimal? =
        creditAmountInput.text?.toString()
            ?.trim()
            ?.replace(',', '.')
            ?.toBigDecimalOrNull()

    private fun creditAmountErrorRes(): Int? {
        val raw = creditAmountInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val amount = parseCreditAmount() ?: return R.string.settings_openai_credit_error_zero
        if (amount.signum() <= 0) return R.string.settings_openai_credit_error_zero
        val decimals = amount.stripTrailingZeros().scale().coerceAtLeast(0)
        if (decimals > 2) return R.string.settings_openai_credit_error_decimals
        return null
    }

    private fun creditAmountIsValid(): Boolean {
        val raw = creditAmountInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val amount = parseCreditAmount() ?: return false
        return amount.signum() > 0 &&
            amount.stripTrailingZeros().scale().coerceAtLeast(0) <= 2
    }

    /** Errore inline mentre si digita, evitando i passaggi intermedi ("0", "0,"). */
    private fun updateCreditAmountError() {
        val raw = creditAmountInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty() || raw == "0" || raw.endsWith(",") || raw.endsWith(".")) {
            creditAmountLayout.error = null
            return
        }
        val error = creditAmountErrorRes()
        creditAmountLayout.error = error?.let { getString(it) }
    }

    private fun renderCreditDate() {
        if (!isAdded) return
        creditDateValue.text = Format.dayLabel(creditRefDate)
        creditDateRow.contentDescription = getString(
            R.string.settings_openai_credit_date_cd,
            fullCreditDate(creditRefDate)
        )
    }

    private fun showCreditDatePicker() {
        if (!isAdded) return
        val today = LocalDate.now(ZoneOffset.UTC)
        val min = today.minusDays(30)
        val selection = when {
            creditRefDate.isBefore(min) -> min
            creditRefDate.isAfter(today) -> today
            else -> creditRefDate
        }
        val constraints = CalendarConstraints.Builder()
            .setStart(utcMillis(min))
            .setEnd(utcMillis(today.plusDays(1)) - 1L)
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.settings_openai_credit_date)
            .setSelection(utcMillis(selection))
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            creditRefDate = Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            creditAmountLayout.error = null
            creditStatusRow.visibility = View.GONE
            renderCreditDate()
            updateButtons()
        }
        picker.show(childFragmentManager, "openai_credit_date")
    }

    private fun saveCreditAnchor() {
        if (!isAdded) return
        val error = creditAmountErrorRes() ?: run {
            if (!creditAmountIsValid()) R.string.settings_openai_credit_error_zero else null
        }
        val amount = parseCreditAmount()
        if (error != null || amount == null) {
            creditAmountLayout.error = getString(
                error ?: R.string.settings_openai_credit_error_zero
            )
            return
        }

        val today = LocalDate.now(ZoneOffset.UTC)
        when {
            creditRefDate.isAfter(today) -> {
                showCreditStatus(
                    getString(R.string.settings_openai_credit_error_future),
                    StatusKind.ERROR
                )
                return
            }
            creditRefDate.isBefore(today.minusDays(30)) -> {
                showCreditStatus(
                    getString(R.string.settings_openai_credit_error_old),
                    StatusKind.ERROR
                )
                return
            }
        }

        Prefs(requireContext()).setOpenAiCreditAnchor(amount, utcMillis(creditRefDate))
        creditAmountLayout.error = null
        creditStatusRow.visibility = View.GONE
        creditRemoveButton.visibility = View.VISIBLE
        updateButtons()
        Snackbar.make(
            requireView(),
            R.string.settings_openai_credit_saved,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun confirmRemoveCreditAnchor() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_openai_credit_remove_title)
            .setMessage(R.string.settings_openai_credit_remove_body)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_remove_confirm) { _, _ -> removeCreditAnchor() }
            .show()
    }

    private fun removeCreditAnchor() {
        if (!isAdded) return
        Prefs(requireContext()).clearOpenAiCreditAnchor()
        binding = true
        creditAmountInput.setText("")
        binding = false
        creditRefDate = LocalDate.now(ZoneOffset.UTC)
        creditAmountLayout.error = null
        creditStatusRow.visibility = View.GONE
        creditRemoveButton.visibility = View.GONE
        renderCreditDate()
        updateButtons()
        Snackbar.make(
            requireView(),
            R.string.settings_openai_credit_removed,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun refreshCreditRealLine(prefs: Prefs) {
        if (!isAdded) return
        val realUsd = prefs.getOpenAiCreditRealUsd()
        val realTs = prefs.getOpenAiCreditRealTs()
        if (realUsd == null || realTs <= 0L) {
            creditRealLine.visibility = View.GONE
            return
        }
        val amount = Format.money(realUsd)
        creditRealLine.visibility = View.VISIBLE
        creditRealLine.text = monoParts(
            getString(R.string.settings_openai_credit_real_available, amount),
            amount
        )
    }

    private fun showCreditStatus(text: String, kind: StatusKind) {
        showStatusRow(creditStatusRow, creditStatusDot, creditStatusText, text, kind)
    }

    private fun utcMillis(day: LocalDate): Long =
        day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun fullCreditDate(day: LocalDate): String =
        day.format(FULL_DATE_FORMATTER)

    private fun monoParts(text: CharSequence, vararg parts: String): CharSequence {
        val full = text.toString()
        val span = SpannableString(full)
        val typeface = ResourcesCompat.getFont(requireContext(), R.font.plex_mono_regular)
            ?: Typeface.MONOSPACE
        var from = 0
        for (part in parts) {
            if (part.isEmpty()) continue
            val start = full.indexOf(part, from)
            if (start < 0) continue
            span.setSpan(
                MonoTypefaceSpan(typeface),
                start,
                start + part.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            from = start + part.length
        }
        return span
    }

    private class MonoTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.typeface = typeface
        }

        override fun updateMeasureState(tp: TextPaint) {
            tp.typeface = typeface
        }
    }

    private fun fallbackAfterRemoval(prefs: Prefs, removed: Provider) {
        val other = Provider.entries.firstOrNull {
            it != removed && prefs.hasApiKey(it)
        }
        if (other != null) {
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
        private const val ARG_FOCUS_CREDIT = "settings_focus_credit"
        private const val KEY_TAB = "settings_tab"
        private val FULL_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM", Locale.ITALY)

        fun newInstance(
            provider: Provider,
            focusCredit: Boolean = false
        ): SettingsFragment = SettingsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PROVIDER, provider.storageKey)
                putBoolean(ARG_FOCUS_CREDIT, focusCredit)
            }
        }
    }
}
