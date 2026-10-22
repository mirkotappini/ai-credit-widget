package com.patchouli.deepseekwidget.ui

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.patchouli.deepseekwidget.MainActivity
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.data.Snapshot
import com.patchouli.deepseekwidget.logic.CostAggregator
import com.patchouli.deepseekwidget.logic.SpendCalculator
import com.patchouli.deepseekwidget.net.CostBucket
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.repo.AnthropicCostsRepository
import com.patchouli.deepseekwidget.repo.BalanceRepository
import com.patchouli.deepseekwidget.repo.OpenAiCostsRepository
import com.patchouli.deepseekwidget.util.Format
import com.patchouli.deepseekwidget.view.BalanceSplitView
import com.patchouli.deepseekwidget.view.DailySpendChartView
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DashboardFragment : Fragment() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var providerGroup: MaterialButtonToggleGroup
    private lateinit var providerSelector: ProviderSelector
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingContainer: View
    private lateinit var stateContainer: View
    private lateinit var stateIcon: ImageView
    private lateinit var stateTitle: TextView
    private lateinit var stateBody: TextView
    private lateinit var stateHint: TextView
    private lateinit var statePrimary: MaterialButton
    private lateinit var stateSecondary: MaterialButton
    private lateinit var stateTertiary: androidx.appcompat.widget.AppCompatTextView
    private lateinit var dataContainer: View
    private lateinit var openAiData: View
    private lateinit var balanceValue: TextView
    private lateinit var grantedValue: TextView
    private lateinit var toppedValue: TextView
    private lateinit var todayValue: TextView
    private lateinit var todayCaption: TextView
    private lateinit var updatedValue: TextView
    private lateinit var statusDot: ImageView
    private lateinit var splitView: BalanceSplitView
    private lateinit var todayBars: DailySpendChartView
    private lateinit var todayCard: View
    private lateinit var recentList: LinearLayout
    private lateinit var footerNote: TextView
    private lateinit var banner: View
    private lateinit var bannerIcon: ImageView
    private lateinit var bannerTitle: TextView
    private lateinit var bannerBody: TextView
    private lateinit var bannerAction: MaterialButton

    private lateinit var openAiDayLabel: TextView
    private lateinit var openAiSpentValue: TextView
    private lateinit var openAiMeta: TextView
    private lateinit var openAiNote: TextView
    private lateinit var openAiWeekValue: TextView
    private lateinit var openAiWeekCaption: TextView
    private lateinit var openAiMonthValue: TextView
    private lateinit var openAiMonthCaption: TextView
    private lateinit var openAiBars: DailySpendChartView
    private lateinit var openAiFooter: TextView
    private lateinit var openAiBanner: View
    private lateinit var openAiBannerIcon: ImageView
    private lateinit var openAiBannerTitle: TextView
    private lateinit var openAiBannerBody: TextView
    private lateinit var openAiBannerAction: MaterialButton

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRefreshing = false
    private var skeletonAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scrollView = view.findViewById(R.id.dashboard_scroll)
        providerGroup = view.findViewById(R.id.provider_group)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        loadingContainer = view.findViewById(R.id.loading_container)
        stateContainer = view.findViewById(R.id.state_container)
        stateIcon = view.findViewById(R.id.state_icon)
        stateTitle = view.findViewById(R.id.state_title)
        stateBody = view.findViewById(R.id.state_body)
        stateHint = view.findViewById(R.id.state_hint)
        statePrimary = view.findViewById(R.id.state_primary)
        stateSecondary = view.findViewById(R.id.state_secondary)
        stateTertiary = view.findViewById(R.id.state_tertiary)
        dataContainer = view.findViewById(R.id.dashboard_data)
        openAiData = view.findViewById(R.id.openai_data)
        balanceValue = view.findViewById(R.id.balance_value)
        grantedValue = view.findViewById(R.id.granted_value)
        toppedValue = view.findViewById(R.id.topped_value)
        todayValue = view.findViewById(R.id.today_value)
        todayCaption = view.findViewById(R.id.today_caption)
        updatedValue = view.findViewById(R.id.updated_value)
        statusDot = view.findViewById(R.id.status_dot)
        splitView = view.findViewById(R.id.split_view)
        todayBars = view.findViewById(R.id.today_bars)
        todayCard = view.findViewById(R.id.today_card)
        recentList = view.findViewById(R.id.recent_list)
        footerNote = view.findViewById(R.id.footer_note)
        banner = view.findViewById(R.id.dashboard_banner)
        bannerIcon = view.findViewById(R.id.banner_icon)
        bannerTitle = view.findViewById(R.id.banner_title)
        bannerBody = view.findViewById(R.id.banner_body)
        bannerAction = view.findViewById(R.id.banner_action)

        openAiDayLabel = view.findViewById(R.id.openai_day_label)
        openAiSpentValue = view.findViewById(R.id.openai_spent_value)
        openAiMeta = view.findViewById(R.id.openai_meta)
        openAiNote = view.findViewById(R.id.openai_note)
        openAiWeekValue = view.findViewById(R.id.openai_week_value)
        openAiWeekCaption = view.findViewById(R.id.openai_week_caption)
        openAiMonthValue = view.findViewById(R.id.openai_month_value)
        openAiMonthCaption = view.findViewById(R.id.openai_month_caption)
        openAiBars = view.findViewById(R.id.openai_bars)
        openAiFooter = view.findViewById(R.id.openai_footer)
        openAiBanner = view.findViewById(R.id.openai_banner)
        openAiBannerIcon = view.findViewById(R.id.openai_banner_icon)
        openAiBannerTitle = view.findViewById(R.id.openai_banner_title)
        openAiBannerBody = view.findViewById(R.id.openai_banner_body)
        openAiBannerAction = view.findViewById(R.id.openai_banner_action)

        providerSelector = ProviderSelector(
            group = providerGroup,
            prefs = Prefs(requireContext()),
            persistSelection = true,
            onSelected = { provider -> onProviderSelected(provider) },
            onNeedsSettings = { provider -> openSettings(provider) }
        )

        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.ds_color_accent)
        )
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(requireContext(), R.color.ds_color_surface)
        )
        swipeRefresh.setOnRefreshListener { refreshFromNetwork() }

        todayCard.setOnClickListener { (activity as? MainActivity)?.showHistory() }
        view.findViewById<MaterialButton>(R.id.see_all).setOnClickListener {
            (activity as? MainActivity)?.showHistory()
        }
        bannerAction.setOnClickListener { refreshFromNetwork() }
        openAiBannerAction.setOnClickListener { refreshFromNetwork() }

        balanceValue.setOnLongClickListener { copyBalance() }

        render()
        if (savedInstanceState == null && shouldRefreshOnOpen()) {
            refreshFromNetwork()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        if (!isRefreshing && isAdded) {
            val active = Prefs(requireContext()).activeProviderOrNull()
            if (active != null && shouldRefreshOnOpen()) {
                refreshFromNetwork()
            }
        }
    }

    private fun onProviderSelected(provider: Provider) {
        scrollView.smoothScrollTo(0, 0)
        val target = if (provider == Provider.DEEPSEEK) dataContainer else openAiData
        target.alpha = 0f
        render()
        target.animate().alpha(1f).setDuration(120L).start()
        if (shouldRefreshAfterSwitch(provider)) refreshFromNetwork()
    }

    private fun shouldRefreshOnOpen(): Boolean {
        if (!isAdded) return false
        val prefs = Prefs(requireContext())
        val provider = prefs.activeProviderOrNull() ?: return false
        if (!prefs.hasApiKey(provider)) return false
        if (prefs.getLastError(provider) != null) return false
        return when (provider) {
            Provider.DEEPSEEK -> BalanceRepository(requireContext()).latestSnapshot() == null
            Provider.OPENAI -> {
                val repo = OpenAiCostsRepository(requireContext())
                repo.hourlyBucketsSince(0L).isEmpty() && repo.dailyBucketsSince(0L).isEmpty()
            }
            Provider.CLAUDE ->
                AnthropicCostsRepository(requireContext()).dailyBucketsSince(0L).isEmpty()
        }
    }

    private fun shouldRefreshAfterSwitch(provider: Provider): Boolean {
        if (!isAdded) return false
        val prefs = Prefs(requireContext())
        if (!prefs.hasApiKey(provider)) return false
        val lastSuccess = prefs.getLastSuccessTs(provider)
        if (lastSuccess == 0L) return true
        val staleAfter = prefs.getIntervalMinutes() * 2L * 60_000L
        return System.currentTimeMillis() - lastSuccess > staleAfter
    }

    private fun refreshFromNetwork() {
        if (!isAdded || isRefreshing) return
        val context = requireContext()
        val prefs = Prefs(context)
        val provider = prefs.activeProviderOrNull()
        if (provider == null || !prefs.hasApiKey(provider)) {
            render()
            return
        }
        isRefreshing = true
        swipeRefresh.isRefreshing = true
        render()
        val appContext = context.applicationContext
        executor.execute {
            try {
                when (provider) {
                    Provider.DEEPSEEK -> BalanceRepository(appContext).refresh()
                    Provider.OPENAI -> OpenAiCostsRepository(appContext).refresh()
                    Provider.CLAUDE -> AnthropicCostsRepository(appContext).refresh()
                }
            } finally {
                mainHandler.post {
                    if (!isAdded || view == null) return@post
                    isRefreshing = false
                    swipeRefresh.isRefreshing = false
                    render()
                }
            }
        }
    }

    private fun render() {
        if (!isAdded) return
        val prefs = Prefs(requireContext())
        val active = prefs.activeProviderOrNull()
        providerSelector.render(active)
        when (active) {
            null -> showNeutral()
            Provider.DEEPSEEK -> renderDeepSeek(prefs)
            Provider.OPENAI -> renderOpenAi(prefs)
            Provider.CLAUDE -> renderClaude(prefs)
        }
    }

    // --- DeepSeek -------------------------------------------------------

    private fun renderDeepSeek(prefs: Prefs) {
        val repository = BalanceRepository(requireContext())
        val snapshot = repository.latestSnapshot()
        val hasKey = prefs.hasApiKey(Provider.DEEPSEEK)
        val error = prefs.getLastError(Provider.DEEPSEEK)

        when {
            !hasKey -> showState(EmptyState.NO_KEY)
            error == Prefs.ERROR_AUTH -> showState(EmptyState.AUTH)
            snapshot == null && error == Prefs.ERROR_NETWORK -> showState(EmptyState.NETWORK)
            snapshot == null && error == Prefs.ERROR_UNEXPECTED -> showState(EmptyState.UNEXPECTED)
            snapshot == null -> showLoading()
            else -> {
                showData(Provider.DEEPSEEK)
                bindData(snapshot, prefs, repository, error)
            }
        }
    }

    private fun bindData(
        snapshot: Snapshot,
        prefs: Prefs,
        repository: BalanceRepository,
        error: String?
    ) {
        openAiData.visibility = View.GONE
        val full = Format.money(snapshot.total)
        balanceValue.text = symbolSpannable(full)
        balanceValue.contentDescription = getString(R.string.dashboard_balance_cd, full)
        grantedValue.text = Format.money(snapshot.granted)
        toppedValue.text = Format.money(snapshot.topped)
        splitView.setValues(snapshot.granted, snapshot.topped)

        val todaySpent = repository.todaySpent()
        todayValue.text = Format.money(todaySpent)
        todayValue.contentDescription = getString(R.string.dashboard_today_cd, Format.money(todaySpent))
        todayCard.contentDescription = getString(
            R.string.dashboard_today_card_cd,
            Format.money(todaySpent)
        )

        val todaySnapshots = repository.snapshotsForDays(1)
        todayCaption.text = when {
            todaySnapshots.isEmpty() -> getString(R.string.dashboard_today_none)
            todaySnapshots.size == 1 -> getString(R.string.dashboard_today_first)
            else -> getString(
                R.string.dashboard_today_from,
                Format.shortTime(todaySnapshots.first().ts)
            )
        }

        val weekSnapshots = repository.snapshotsForDays(7)
        val today = LocalDate.now()
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val spends = SpendCalculator.spendByDay(weekSnapshots, days)
        todayBars.setCompact(true)
        todayBars.setData(days.map { day -> day to (spends[day] ?: BigDecimal.ZERO) })
        val totalSpent = spends.values.fold(BigDecimal.ZERO) { a, b -> a.add(b) }
        val maxSpend = spends.maxByOrNull { it.value }
        todayBars.contentDescription = if (totalSpent.signum() > 0 && maxSpend != null) {
            getString(
                R.string.history_spend_cd,
                Format.money(totalSpent),
                Format.money(maxSpend.value),
                Format.dayLabel(maxSpend.key)
            )
        } else {
            getString(R.string.history_spend_cd_none)
        }

        val lastSuccess = prefs.getLastSuccessTs(Provider.DEEPSEEK)
        updatedValue.text = updatedLabel(lastSuccess)
        footerNote.text = getString(R.string.dashboard_footer, prefs.getIntervalMinutes())

        val threshold = prefs.getThreshold()
        val belowThreshold = snapshot.total < threshold
        val stale = lastSuccess == 0L ||
            System.currentTimeMillis() - lastSuccess > prefs.getIntervalMinutes() * 2L * 60_000L
        val dot = when {
            isRefreshing -> R.drawable.dot_accent to R.string.cd_status_refreshing
            error == Prefs.ERROR_NETWORK -> R.drawable.dot_warning to R.string.cd_status_stale
            error == Prefs.ERROR_UNEXPECTED -> R.drawable.dot_error to R.string.cd_status_error
            belowThreshold || stale -> R.drawable.dot_warning to R.string.cd_status_stale
            else -> R.drawable.dot_positive to R.string.cd_status_fresh
        }
        statusDot.visibility = View.VISIBLE
        statusDot.setImageResource(dot.first)
        statusDot.contentDescription = getString(dot.second)

        renderRecent(repository)
        renderBanner(prefs, snapshot, lastSuccess, error, belowThreshold)
    }

    private fun renderRecent(repository: BalanceRepository) {
        recentList.removeAllViews()
        val recent = repository.snapshotsForDays(7).takeLast(3).reversed()
        recent.forEachIndexed { index, snapshot ->
            val row = layoutInflater.inflate(R.layout.item_recent, recentList, false)
            row.findViewById<TextView>(R.id.recent_time).text = Format.snapshotTime(snapshot.ts)
            row.findViewById<TextView>(R.id.recent_total).text = Format.money(snapshot.total)
            if (index == recent.lastIndex) {
                row.findViewById<View>(R.id.recent_divider).visibility = View.GONE
            }
            recentList.addView(row)
        }
    }

    private fun renderBanner(
        prefs: Prefs,
        snapshot: Snapshot,
        lastSuccess: Long,
        error: String?,
        belowThreshold: Boolean
    ) {
        val dataTime = if (lastSuccess > 0) {
            if (isToday(lastSuccess)) Format.shortTime(lastSuccess)
            else Format.updatedDate(lastSuccess)
        } else {
            null
        }
        when {
            error == Prefs.ERROR_NETWORK -> {
                val online = isOnline()
                val title = getString(
                    if (online) R.string.error_server_title else R.string.error_offline_title
                )
                val body = when {
                    dataTime == null -> getString(R.string.error_data_none)
                    isToday(lastSuccess) -> getString(R.string.error_data_today, dataTime)
                    else -> getString(R.string.error_data_older, dataTime)
                }
                showBanner(
                    warning = true,
                    icon = R.drawable.ic_cloud_off,
                    title = title,
                    body = body,
                    action = true
                )
            }

            error == Prefs.ERROR_UNEXPECTED -> showBanner(
                warning = false,
                icon = R.drawable.ic_error,
                title = getString(R.string.error_unexpected_title),
                body = getString(R.string.error_unexpected_body),
                action = true
            )

            belowThreshold -> showBanner(
                warning = true,
                icon = R.drawable.ic_warning,
                title = getString(R.string.error_threshold_title),
                body = getString(
                    R.string.error_threshold_body,
                    Format.money(snapshot.total),
                    Format.money(prefs.getThreshold())
                ),
                action = false
            )

            else -> banner.visibility = View.GONE
        }
    }

    // --- OpenAI ---------------------------------------------------------

    private fun renderOpenAi(prefs: Prefs) {
        val hasKey = prefs.hasApiKey(Provider.OPENAI)
        val error = prefs.getLastError(Provider.OPENAI)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val repo = OpenAiCostsRepository(requireContext())
        val localSince = LocalDate.now(zone).minusDays(7)
            .atStartOfDay(zone).toInstant().epochSecond
        val utcSince = LocalDate.now(ZoneOffset.UTC).minusDays(32)
            .atStartOfDay(ZoneOffset.UTC).toInstant().epochSecond
        val hourly = repo.hourlyBucketsSince(localSince)
        val daily = repo.dailyBucketsSince(utcSince)

        when {
            !hasKey -> showOpenAiState(OpenAiState.NO_KEY)
            hourly.isEmpty() && daily.isEmpty() && error == Prefs.ERROR_AUTH ->
                showOpenAiState(OpenAiState.AUTH)
            hourly.isEmpty() && daily.isEmpty() && error == Prefs.ERROR_FORBIDDEN ->
                showOpenAiState(OpenAiState.FORBIDDEN)
            hourly.isEmpty() && daily.isEmpty() && error == Prefs.ERROR_NETWORK ->
                showOpenAiState(OpenAiState.NETWORK)
            hourly.isEmpty() && daily.isEmpty() && error == Prefs.ERROR_UNEXPECTED ->
                showOpenAiState(OpenAiState.UNEXPECTED)
            hourly.isEmpty() && daily.isEmpty() && error == null && isRefreshing ->
                showLoading()
            hourly.isEmpty() && daily.isEmpty() ->
                showOpenAiState(OpenAiState.NO_DATA)
            else -> {
                showData(Provider.OPENAI)
                bindOpenAi(prefs, hourly, daily, error, now, zone)
            }
        }
    }

    private fun bindOpenAi(
        prefs: Prefs,
        hourly: List<CostBucket>,
        daily: List<CostBucket>,
        error: String?,
        now: Long,
        zone: ZoneId
    ) {
        dataContainer.visibility = View.GONE
        openAiNote.text = getString(R.string.openai_delay_note)
        val todayLocal = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayUtc = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        val hourlyToday = hourly.any { overlapsLocalDay(it, todayLocal, zone) }
        val dailyToday = daily.firstOrNull { dayOfUtc(it.startTsUtc) == todayUtc }
        val latest = (hourly.maxByOrNull { it.endTsUtc } ?: daily.maxByOrNull { it.endTsUtc })

        val heroValue: BigDecimal
        if (hourlyToday) {
            heroValue = CostAggregator.spentTodayLocal(hourly, now, zone)
            openAiDayLabel.text = getString(R.string.openai_spent_today)
        } else if (dailyToday != null) {
            heroValue = dailyToday.amount
            openAiDayLabel.text = getString(
                R.string.openai_spent_on_day,
                Format.utcDayLabel(dailyToday.startTsUtc * 1000L).uppercase(Locale.ITALY)
            )
        } else if (latest != null) {
            heroValue = latest.amount
            openAiDayLabel.text = getString(
                R.string.openai_spent_on_day,
                Format.utcDayLabel(latest.startTsUtc * 1000L).uppercase(Locale.ITALY)
            )
        } else {
            heroValue = BigDecimal.ZERO
            openAiDayLabel.text = getString(R.string.openai_spent_today)
        }
        openAiSpentValue.text = Format.money(heroValue)

        val lastEndSec = latest?.endTsUtc ?: 0L
        val ageMs = if (lastEndSec > 0L) {
            (now - lastEndSec * 1000L).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        val lastEndMs = lastEndSec * 1000L
        val metaText = when {
            latest == null -> getString(R.string.openai_no_data_title)
            ageMs > STALE_MS -> getString(
                R.string.openai_meta_stale,
                Format.utcDayLabel(lastEndMs),
                Format.utcTime(lastEndMs)
            )
            hourlyToday -> getString(
                R.string.openai_meta_today_progress,
                Format.utcTime(lastEndMs)
            )
            else -> getString(R.string.openai_meta_costs_until, Format.utcTime(lastEndMs))
        }
        openAiMeta.text = metaText
        openAiMeta.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (ageMs > WARN_MS || error == Prefs.ERROR_NETWORK) R.color.ds_color_warning
                else R.color.ds_color_text_tertiary
            )
        )

        val week = CostAggregator.spentLast7DaysLocal(hourly, now, zone)
        val weekTotal = week.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        val weekDays = week.keys.count { day -> hourly.any { overlapsLocalDay(it, day, zone) } }
        openAiWeekValue.text = Format.money(weekTotal)
        if (weekDays < 7) {
            openAiWeekCaption.text = getString(R.string.openai_partial_days, weekDays, 7)
            openAiWeekCaption.visibility = View.VISIBLE
        } else {
            openAiWeekCaption.text = ""
            openAiWeekCaption.visibility = View.GONE
        }

        val monthTotal = CostAggregator.spentCurrentMonthUtc(daily, now)
        openAiMonthValue.text = Format.money(monthTotal)
        val monthStart = todayUtc.withDayOfMonth(1)
        val monthBuckets = daily.filter { dayOfUtc(it.startTsUtc) in monthStart..todayUtc }
        val firstMonthDay = monthBuckets.minOfOrNull { dayOfUtc(it.startTsUtc) }
        if (firstMonthDay != null && firstMonthDay.isAfter(monthStart)) {
            openAiMonthCaption.text = getString(
                R.string.openai_partial_since,
                Format.dayLabel(firstMonthDay)
            )
            openAiMonthCaption.visibility = View.VISIBLE
        } else {
            openAiMonthCaption.text = ""
            openAiMonthCaption.visibility = View.GONE
        }

        val chartDays = (13 downTo 0).map { todayUtc.minusDays(it.toLong()) }
        val byUtcDay = daily
            .filter { dayOfUtc(it.startTsUtc) in chartDays.first()..todayUtc }
            .groupBy { dayOfUtc(it.startTsUtc) }
            .mapValues { (_, buckets) -> buckets.fold(BigDecimal.ZERO) { a, b -> a.add(b.amount) } }
        openAiBars.setCompact(false)
        openAiBars.setMissingDays(chartDays.filter { !byUtcDay.containsKey(it) }.toSet())
        openAiBars.setData(chartDays.map { day -> day to (byUtcDay[day] ?: BigDecimal.ZERO) })
        openAiBars.contentDescription = getString(
            R.string.history_spend_cd,
            Format.money(monthTotal),
            Format.money(byUtcDay.values.maxOrNull() ?: BigDecimal.ZERO),
            Format.dayLabel(todayUtc)
        )
        openAiFooter.text = getString(
            R.string.openai_footer,
            prefs.getIntervalMinutes()
        )
        openAiSpentValue.contentDescription = getString(
            R.string.dashboard_today_cd,
            Format.money(heroValue)
        )

        renderOpenAiBanner(prefs, error, latest, ageMs, hourlyToday)
    }

    private fun renderOpenAiBanner(
        prefs: Prefs,
        error: String?,
        latest: CostBucket?,
        ageMs: Long,
        hourlyToday: Boolean
    ) {
        val lastEndMs = (latest?.endTsUtc ?: 0L) * 1000L
        val lastLabel = if (lastEndMs > 0L) {
            getString(
                R.string.openai_stale_body,
                Format.utcDayLabel(lastEndMs),
                Format.utcTime(lastEndMs)
            )
        } else {
            getString(R.string.error_data_none)
        }
        when {
            error == Prefs.ERROR_FORBIDDEN -> {
                showOpenAiBanner(
                    warning = false,
                    title = getString(R.string.openai_forbidden_title),
                    body = getString(R.string.openai_forbidden_banner),
                    action = getString(R.string.openai_open_settings),
                    actionOpensSettings = true
                )
            }
            error == Prefs.ERROR_AUTH -> {
                showOpenAiBanner(
                    warning = false,
                    title = getString(R.string.openai_auth_title),
                    body = getString(R.string.openai_auth_banner),
                    action = getString(R.string.openai_open_settings),
                    actionOpensSettings = true
                )
            }
            error == Prefs.ERROR_NETWORK -> {
                val title = if (isOnline()) R.string.error_server_title else R.string.error_offline_title
                val body = if (lastEndMs > 0L) {
                    getString(
                        R.string.openai_offline_banner,
                        Format.utcDayLabel(lastEndMs),
                        Format.utcTime(lastEndMs)
                    )
                } else {
                    getString(R.string.error_data_none)
                }
                showOpenAiBanner(
                    warning = true,
                    title = getString(title),
                    body = body,
                    action = getString(R.string.openai_retry),
                    actionOpensSettings = false
                )
            }
            error == Prefs.ERROR_UNEXPECTED -> {
                showOpenAiBanner(
                    warning = false,
                    title = getString(R.string.error_unexpected_title),
                    body = getString(R.string.openai_unexpected_banner),
                    action = getString(R.string.openai_retry),
                    actionOpensSettings = false
                )
            }
            ageMs > STALE_MS -> {
                showOpenAiBanner(
                    warning = true,
                    title = getString(R.string.openai_stale_title),
                    body = lastLabel,
                    action = getString(R.string.openai_retry),
                    actionOpensSettings = false
                )
            }
            else -> {
                openAiBanner.visibility = View.GONE
                if (hourlyToday) {
                    // Oggi parziale: nessun warning aggiuntivo.
                }
            }
        }
    }

    private fun showOpenAiBanner(
        warning: Boolean,
        title: String,
        body: String,
        action: String,
        actionOpensSettings: Boolean
    ) {
        openAiBanner.visibility = View.VISIBLE
        openAiBanner.setBackgroundResource(
            if (warning) R.drawable.bg_banner_warning else R.drawable.bg_banner_error
        )
        val color = ContextCompat.getColor(
            requireContext(),
            if (warning) R.color.ds_color_warning else R.color.ds_color_error
        )
        openAiBannerIcon.setImageResource(
            if (warning) R.drawable.ic_warning else R.drawable.ic_error
        )
        openAiBannerIcon.imageTintList = ColorStateList.valueOf(color)
        openAiBannerTitle.setTextColor(color)
        openAiBannerTitle.text = title
        openAiBannerBody.text = body
        openAiBannerAction.visibility = View.VISIBLE
        openAiBannerAction.text = action
        openAiBannerAction.setTextColor(color)
        openAiBannerAction.setOnClickListener {
            if (actionOpensSettings) openSettings(Provider.OPENAI) else refreshFromNetwork()
        }
    }

    // --- Claude ---------------------------------------------------------

    private fun renderClaude(prefs: Prefs) {
        val hasKey = prefs.hasApiKey(Provider.CLAUDE)
        val error = prefs.getLastError(Provider.CLAUDE)
        val now = System.currentTimeMillis()
        val todayUtc = LocalDate.now(ZoneOffset.UTC)
        val since = todayUtc.minusDays(32)
            .atStartOfDay(ZoneOffset.UTC).toInstant().epochSecond
        val daily = AnthropicCostsRepository(requireContext()).dailyBucketsSince(since)

        when {
            !hasKey -> showClaudeState(OpenAiState.NO_KEY)
            daily.isEmpty() && error == Prefs.ERROR_AUTH -> showClaudeState(OpenAiState.AUTH)
            daily.isEmpty() && error == Prefs.ERROR_FORBIDDEN ->
                showClaudeState(OpenAiState.FORBIDDEN)
            daily.isEmpty() && error == Prefs.ERROR_NETWORK ->
                showClaudeState(OpenAiState.NETWORK)
            daily.isEmpty() && error == Prefs.ERROR_UNEXPECTED ->
                showClaudeState(OpenAiState.UNEXPECTED)
            daily.isEmpty() && error == null && isRefreshing -> showLoading()
            daily.isEmpty() -> showClaudeState(OpenAiState.NO_DATA)
            else -> {
                showData(Provider.CLAUDE)
                bindClaude(prefs, daily, error, now, todayUtc)
            }
        }
    }

    private fun bindClaude(
        prefs: Prefs,
        daily: List<CostBucket>,
        error: String?,
        now: Long,
        todayUtc: LocalDate
    ) {
        dataContainer.visibility = View.GONE
        openAiNote.text = getString(R.string.claude_note)
        openAiDayLabel.text = getString(R.string.openai_spent_today)
        val byDay = daily
            .filter { dayOfUtc(it.startTsUtc) in todayUtc.minusDays(32)..todayUtc }
            .groupBy { dayOfUtc(it.startTsUtc) }
            .mapValues { (_, buckets) -> buckets.fold(BigDecimal.ZERO) { a, b -> a.add(b.amount) } }
        val todayAmount = byDay[todayUtc]
        val latestDay = byDay.keys.maxOrNull()
        val heroValue = todayAmount ?: (latestDay?.let { byDay[it] } ?: BigDecimal.ZERO)
        if (todayAmount == null && latestDay != null) {
            openAiDayLabel.text = getString(
                R.string.openai_spent_on_day,
                Format.dayLabel(latestDay).uppercase(Locale.ITALY)
            )
        }
        openAiSpentValue.text = Format.money(heroValue)

        val latestBucket = daily.maxByOrNull { it.endTsUtc }
        val lastEndMs = (latestBucket?.endTsUtc ?: 0L) * 1000L
        val ageMs = if (lastEndMs > 0L) {
            (now - lastEndMs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
        val metaText = when {
            latestDay == null -> getString(R.string.openai_no_data_title)
            ageMs > STALE_MS -> getString(
                R.string.claude_meta_stale,
                Format.dayLabel(latestDay)
            )
            else -> getString(R.string.claude_meta_until, Format.dayLabel(latestDay))
        }
        openAiMeta.text = metaText
        openAiMeta.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (ageMs > WARN_MS || error == Prefs.ERROR_NETWORK) R.color.ds_color_warning
                else R.color.ds_color_text_tertiary
            )
        )

        val weekDays = (6 downTo 0).map { todayUtc.minusDays(it.toLong()) }
        val weekTotal = weekDays.fold(BigDecimal.ZERO) { acc, day ->
            acc.add(byDay[day] ?: BigDecimal.ZERO)
        }
        val weekWithData = weekDays.count { byDay.containsKey(it) }
        openAiWeekValue.text = Format.money(weekTotal)
        if (weekWithData < 7) {
            openAiWeekCaption.text = getString(R.string.openai_partial_days, weekWithData, 7)
            openAiWeekCaption.visibility = View.VISIBLE
        } else {
            openAiWeekCaption.visibility = View.GONE
        }

        val monthTotal = CostAggregator.spentCurrentMonthUtc(daily, now)
        openAiMonthValue.text = Format.money(monthTotal)
        val monthStart = todayUtc.withDayOfMonth(1)
        val firstMonthDay = byDay.keys.filter { it in monthStart..todayUtc }.minOrNull()
        if (firstMonthDay != null && firstMonthDay.isAfter(monthStart)) {
            openAiMonthCaption.text = getString(
                R.string.openai_partial_since,
                Format.dayLabel(firstMonthDay)
            )
            openAiMonthCaption.visibility = View.VISIBLE
        } else {
            openAiMonthCaption.visibility = View.GONE
        }

        val chartDays = (13 downTo 0).map { todayUtc.minusDays(it.toLong()) }
        openAiBars.setCompact(false)
        openAiBars.setMissingDays(chartDays.filter { !byDay.containsKey(it) }.toSet())
        openAiBars.setData(chartDays.map { day -> day to (byDay[day] ?: BigDecimal.ZERO) })
        openAiBars.contentDescription = getString(
            R.string.history_spend_cd,
            Format.money(monthTotal),
            Format.money(byDay.values.maxOrNull() ?: BigDecimal.ZERO),
            Format.dayLabel(todayUtc)
        )
        openAiFooter.text = getString(R.string.claude_footer, prefs.getIntervalMinutes())
        openAiSpentValue.contentDescription = getString(
            R.string.dashboard_today_cd,
            Format.money(heroValue)
        )
        renderClaudeBanner(error, latestBucket, latestDay, ageMs)
    }

    private fun renderClaudeBanner(
        error: String?,
        latest: CostBucket?,
        latestDay: LocalDate?,
        ageMs: Long
    ) {
        val lastDate = latestDay?.let { Format.dayLabel(it) } ?: ""
        when {
            error == Prefs.ERROR_FORBIDDEN -> showOpenAiBanner(
                warning = false,
                title = getString(R.string.claude_forbidden_title),
                body = getString(R.string.claude_forbidden_banner),
                action = getString(R.string.claude_settings),
                actionOpensSettings = true
            )
            error == Prefs.ERROR_AUTH -> showOpenAiBanner(
                warning = false,
                title = getString(R.string.openai_auth_title),
                body = getString(R.string.openai_auth_banner),
                action = getString(R.string.claude_settings),
                actionOpensSettings = true
            )
            error == Prefs.ERROR_NETWORK -> showOpenAiBanner(
                warning = true,
                title = getString(
                    if (isOnline()) R.string.claude_server_title else R.string.error_offline_title
                ),
                body = if (lastDate.isNotBlank()) {
                    getString(R.string.claude_offline_banner, lastDate)
                } else {
                    getString(R.string.error_data_none)
                },
                action = getString(R.string.openai_retry),
                actionOpensSettings = false
            )
            error == Prefs.ERROR_UNEXPECTED -> showOpenAiBanner(
                warning = false,
                title = getString(R.string.error_unexpected_title),
                body = getString(R.string.openai_unexpected_banner),
                action = getString(R.string.openai_retry),
                actionOpensSettings = false
            )
            ageMs > STALE_MS -> showOpenAiBanner(
                warning = true,
                title = getString(R.string.openai_stale_title),
                body = getString(R.string.claude_meta_stale, lastDate),
                action = getString(R.string.openai_retry),
                actionOpensSettings = false
            )
            else -> openAiBanner.visibility = View.GONE
        }
    }

    private fun showClaudeState(state: OpenAiState) {
        stopSkeleton()
        loadingContainer.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        dataContainer.visibility = View.GONE
        openAiData.visibility = View.GONE
        stateHint.visibility = View.GONE
        stateSecondary.visibility = View.GONE
        stateTertiary.visibility = View.GONE
        val warning = ContextCompat.getColor(requireContext(), R.color.ds_color_warning)
        val error = ContextCompat.getColor(requireContext(), R.color.ds_color_error)
        val secondary = ContextCompat.getColor(requireContext(), R.color.ds_color_text_secondary)
        when (state) {
            OpenAiState.NO_KEY -> {
                stateIcon.setImageResource(R.drawable.ic_key)
                stateIcon.imageTintList = ColorStateList.valueOf(secondary)
                stateTitle.text = getString(R.string.claude_no_key_title)
                stateBody.text = getString(R.string.claude_no_key_body)
                stateHint.text = getString(R.string.claude_no_key_hint)
                stateHint.visibility = View.VISIBLE
                statePrimary.text = getString(R.string.claude_add_admin_key)
                statePrimary.setOnClickListener { openSettings(Provider.CLAUDE) }
                swipeRefresh.isEnabled = false
            }
            OpenAiState.FORBIDDEN -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(error)
                stateTitle.text = getString(R.string.claude_forbidden_title)
                stateBody.text = getString(R.string.claude_forbidden_body)
                statePrimary.text = getString(R.string.claude_settings)
                statePrimary.setOnClickListener { openSettings(Provider.CLAUDE) }
                stateSecondary.text = getString(R.string.openai_retry)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { refreshFromNetwork() }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.AUTH -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(error)
                stateTitle.text = getString(R.string.openai_auth_title)
                stateBody.text = getString(R.string.openai_auth_body)
                statePrimary.text = getString(R.string.claude_settings)
                statePrimary.setOnClickListener { openSettings(Provider.CLAUDE) }
                stateSecondary.text = getString(R.string.openai_retry)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { refreshFromNetwork() }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.NETWORK -> {
                stateIcon.setImageResource(R.drawable.ic_cloud_off)
                stateIcon.imageTintList = ColorStateList.valueOf(warning)
                stateTitle.text = getString(R.string.openai_network_title)
                stateBody.text = getString(R.string.openai_network_body)
                statePrimary.text = getString(R.string.openai_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                stateSecondary.text = getString(R.string.claude_settings)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { openSettings(Provider.CLAUDE) }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.UNEXPECTED -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(error)
                stateTitle.text = getString(R.string.error_unexpected_title)
                stateBody.text = getString(R.string.error_unexpected_body)
                statePrimary.text = getString(R.string.openai_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.NO_DATA -> {
                stateIcon.setImageResource(R.drawable.ic_warning)
                stateIcon.imageTintList = ColorStateList.valueOf(secondary)
                stateTitle.text = getString(R.string.openai_no_data_title)
                stateBody.text = getString(R.string.openai_no_data_body)
                statePrimary.text = getString(R.string.openai_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                stateSecondary.text = getString(R.string.claude_settings)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { openSettings(Provider.CLAUDE) }
                swipeRefresh.isEnabled = true
            }
        }
    }

    // --- Stati comuni ---------------------------------------------------

    private fun showLoading() {
        loadingContainer.contentDescription = getString(R.string.openai_loading_cd)
        loadingContainer.visibility = View.VISIBLE
        stateContainer.visibility = View.GONE
        dataContainer.visibility = View.GONE
        openAiData.visibility = View.GONE
        swipeRefresh.isEnabled = false
        startSkeleton()
    }

    private fun showNeutral() {
        stopSkeleton()
        loadingContainer.visibility = View.GONE
        dataContainer.visibility = View.GONE
        openAiData.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateHint.visibility = View.GONE
        stateIcon.setImageResource(R.drawable.ic_key)
        stateIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.ds_color_text_secondary)
        )
        stateTitle.text = getString(R.string.provider_none_title)
        val prefsForState = Prefs(requireContext())
        val hasUnreadableKey = Provider.entries.any {
            prefsForState.keyState(it) == Prefs.KeyState.UNREADABLE
        }
        stateBody.text = getString(
            if (hasUnreadableKey) R.string.provider_none_body_unreadable
            else R.string.provider_none_body
        )
        statePrimary.text = getString(R.string.provider_configure_deepseek)
        statePrimary.setOnClickListener { openSettings(Provider.DEEPSEEK) }
        stateSecondary.text = getString(R.string.provider_configure_openai)
        stateSecondary.visibility = View.VISIBLE
        stateSecondary.setOnClickListener { openSettings(Provider.OPENAI) }
        stateTertiary.text = getString(R.string.provider_configure_claude)
        stateTertiary.visibility = View.VISIBLE
        stateTertiary.setOnClickListener { openSettings(Provider.CLAUDE) }
        swipeRefresh.isEnabled = false
    }

    private fun showState(state: EmptyState) {
        stopSkeleton()
        loadingContainer.visibility = View.GONE
        dataContainer.visibility = View.GONE
        openAiData.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        stateHint.visibility = View.GONE
        stateSecondary.visibility = View.GONE
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.ds_color_text_secondary)
        val provider = Prefs(requireContext()).activeProviderOrNull() ?: Provider.DEEPSEEK
        when (state) {
            EmptyState.NO_KEY -> {
                stateIcon.setImageResource(R.drawable.ic_key)
                stateIcon.imageTintList = ColorStateList.valueOf(secondaryColor)
                stateTitle.text = getString(R.string.state_no_key_title)
                stateBody.text = getString(R.string.state_no_key_body)
                stateHint.text = getString(R.string.state_no_key_hint)
                stateHint.visibility = View.VISIBLE
                statePrimary.text = getString(R.string.state_add_key)
                statePrimary.setOnClickListener { openSettings(provider) }
                swipeRefresh.isEnabled = false
            }
            EmptyState.NETWORK -> {
                stateIcon.setImageResource(R.drawable.ic_cloud_off)
                stateIcon.imageTintList = ColorStateList.valueOf(secondaryColor)
                stateTitle.text = getString(R.string.state_network_title)
                stateBody.text = getString(R.string.state_network_body)
                statePrimary.text = getString(R.string.state_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                stateSecondary.text = getString(R.string.state_open_settings)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { openSettings(provider) }
                swipeRefresh.isEnabled = true
            }
            EmptyState.AUTH -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.ds_color_error)
                )
                stateTitle.text = getString(R.string.state_auth_title)
                stateBody.text = getString(R.string.state_auth_body)
                statePrimary.text = getString(R.string.state_open_settings)
                statePrimary.setOnClickListener { openSettings(provider) }
                swipeRefresh.isEnabled = false
            }
            EmptyState.UNEXPECTED -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.ds_color_error)
                )
                stateTitle.text = getString(R.string.error_unexpected_title)
                stateBody.text = getString(R.string.error_unexpected_body)
                statePrimary.text = getString(R.string.state_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                stateSecondary.text = getString(R.string.state_open_settings)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { openSettings(provider) }
                swipeRefresh.isEnabled = true
            }
        }
    }

    private fun showOpenAiState(state: OpenAiState) {
        stopSkeleton()
        loadingContainer.visibility = View.GONE
        stateContainer.visibility = View.VISIBLE
        dataContainer.visibility = View.GONE
        openAiData.visibility = View.GONE
        stateHint.visibility = View.GONE
        stateSecondary.visibility = View.GONE
        val prefs = Prefs(requireContext())
        val warning = ContextCompat.getColor(requireContext(), R.color.ds_color_warning)
        val error = ContextCompat.getColor(requireContext(), R.color.ds_color_error)
        val secondary = ContextCompat.getColor(requireContext(), R.color.ds_color_text_secondary)
        when (state) {
            OpenAiState.NO_KEY -> {
                stateIcon.setImageResource(R.drawable.ic_key)
                stateIcon.imageTintList = ColorStateList.valueOf(secondary)
                stateTitle.text = getString(R.string.openai_no_key_title)
                stateBody.text = getString(R.string.openai_no_key_body)
                stateHint.text = getString(R.string.openai_no_key_hint)
                stateHint.visibility = View.VISIBLE
                statePrimary.text = getString(R.string.openai_add_admin_key)
                statePrimary.setOnClickListener { openSettings(Provider.OPENAI) }
                swipeRefresh.isEnabled = false
            }
            OpenAiState.FORBIDDEN -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(error)
                stateTitle.text = getString(R.string.openai_forbidden_title)
                stateBody.text = getString(R.string.openai_forbidden_body)
                statePrimary.text = getString(R.string.openai_open_settings)
                statePrimary.setOnClickListener { openSettings(Provider.OPENAI) }
                stateSecondary.text = getString(R.string.openai_retry)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { refreshFromNetwork() }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.AUTH -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(error)
                stateTitle.text = getString(R.string.openai_auth_title)
                stateBody.text = getString(R.string.openai_auth_body)
                statePrimary.text = getString(R.string.openai_open_settings)
                statePrimary.setOnClickListener { openSettings(Provider.OPENAI) }
                stateSecondary.text = getString(R.string.openai_retry)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { refreshFromNetwork() }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.NETWORK -> {
                stateIcon.setImageResource(R.drawable.ic_cloud_off)
                stateIcon.imageTintList = ColorStateList.valueOf(warning)
                stateTitle.text = getString(R.string.openai_network_title)
                stateBody.text = getString(R.string.openai_network_body)
                statePrimary.text = getString(R.string.openai_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                stateSecondary.text = getString(R.string.openai_open_settings)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { openSettings(Provider.OPENAI) }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.UNEXPECTED -> {
                stateIcon.setImageResource(R.drawable.ic_error)
                stateIcon.imageTintList = ColorStateList.valueOf(error)
                stateTitle.text = getString(R.string.error_unexpected_title)
                stateBody.text = getString(R.string.error_unexpected_body)
                statePrimary.text = getString(R.string.openai_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                swipeRefresh.isEnabled = true
            }
            OpenAiState.NO_DATA -> {
                stateIcon.setImageResource(R.drawable.ic_warning)
                stateIcon.imageTintList = ColorStateList.valueOf(secondary)
                stateTitle.text = getString(R.string.openai_no_data_title)
                stateBody.text = getString(R.string.openai_no_data_body)
                statePrimary.text = getString(R.string.openai_retry)
                statePrimary.setOnClickListener { refreshFromNetwork() }
                stateSecondary.text = getString(R.string.openai_open_settings)
                stateSecondary.visibility = View.VISIBLE
                stateSecondary.setOnClickListener { openSettings(Provider.OPENAI) }
                swipeRefresh.isEnabled = prefs.hasApiKey(Provider.OPENAI)
            }
        }
    }

    private fun showData(provider: Provider) {
        stopSkeleton()
        loadingContainer.visibility = View.GONE
        stateContainer.visibility = View.GONE
        if (provider != Provider.DEEPSEEK) {
            dataContainer.visibility = View.GONE
            openAiData.visibility = View.VISIBLE
            swipeRefresh.isEnabled = Prefs(requireContext()).hasApiKey(provider)
        } else {
            openAiData.visibility = View.GONE
            dataContainer.visibility = View.VISIBLE
            swipeRefresh.isEnabled = Prefs(requireContext()).hasApiKey(Provider.DEEPSEEK) &&
                Prefs(requireContext()).getLastError(Provider.DEEPSEEK) != Prefs.ERROR_AUTH
        }
    }

    private fun showBanner(
        warning: Boolean,
        icon: Int,
        title: String,
        body: String,
        action: Boolean
    ) {
        banner.visibility = View.VISIBLE
        banner.setBackgroundResource(
            if (warning) R.drawable.bg_banner_warning else R.drawable.bg_banner_error
        )
        val color = ContextCompat.getColor(
            requireContext(),
            if (warning) R.color.ds_color_warning else R.color.ds_color_error
        )
        bannerIcon.setImageResource(icon)
        bannerIcon.imageTintList = ColorStateList.valueOf(color)
        bannerTitle.setTextColor(color)
        bannerTitle.text = title
        bannerBody.text = body
        bannerAction.visibility = if (action) View.VISIBLE else View.GONE
        bannerAction.setTextColor(color)
    }

    private fun updatedLabel(lastSuccess: Long): String {
        if (isRefreshing) return getString(R.string.dashboard_refreshing, getString(R.string.currency_usd))
        if (lastSuccess == 0L) return getString(R.string.dashboard_never_updated)
        return when {
            isToday(lastSuccess) -> getString(
                R.string.dashboard_updated_today,
                Format.shortTime(lastSuccess),
                getString(R.string.currency_usd)
            )
            isYesterday(lastSuccess) -> getString(
                R.string.dashboard_updated_yesterday,
                Format.shortTime(lastSuccess),
                getString(R.string.currency_usd)
            )
            else -> getString(
                R.string.dashboard_updated_older,
                Format.updatedDate(lastSuccess),
                getString(R.string.currency_usd)
            )
        }
    }

    private fun copyBalance(): Boolean {
        if (!isAdded) return false
        val snapshot = BalanceRepository(requireContext()).latestSnapshot() ?: return false
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.label_balance_total),
                "${getString(R.string.currency_usd)} ${Format.money(snapshot.total).removePrefix("\$")}"
            )
        )
        balanceValue.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        Snackbar.make(requireView(), R.string.dashboard_copied, Snackbar.LENGTH_SHORT).show()
        return true
    }

    private fun symbolSpannable(value: String): SpannableString {
        val text = SpannableString(value)
        val index = value.indexOf('$')
        if (index >= 0) {
            text.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(requireContext(), R.color.ds_color_text_secondary)
                ),
                index,
                index + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return text
    }

    private fun openSettings(provider: Provider) {
        (activity as? MainActivity)?.showSettings(provider)
    }

    private fun isOnline(): Boolean {
        val cm = requireContext().getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isToday(ts: Long): Boolean =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

    private fun isYesterday(ts: Long): Boolean =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now().minusDays(1)

    private fun dayOfUtc(tsSec: Long): LocalDate =
        Instant.ofEpochSecond(tsSec).atZone(ZoneOffset.UTC).toLocalDate()

    private fun overlapsLocalDay(bucket: CostBucket, day: LocalDate, zone: ZoneId): Boolean {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val bStart = bucket.startTsUtc * 1000L
        val bEnd = bucket.endTsUtc * 1000L
        return bEnd > start && bStart < end
    }

    private fun startSkeleton() {
        if (skeletonAnimator?.isRunning == true) return
        val scale = Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        if (scale == 0f) {
            loadingContainer.alpha = 0.5f
            return
        }
        skeletonAnimator = ValueAnimator.ofFloat(0.35f, 0.6f).apply {
            duration = 900L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { loadingContainer.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopSkeleton() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        loadingContainer.alpha = 1f
    }

    override fun onDestroyView() {
        stopSkeleton()
        super.onDestroyView()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private enum class EmptyState {
        NO_KEY,
        NETWORK,
        AUTH,
        UNEXPECTED
    }

    private enum class OpenAiState {
        NO_KEY,
        FORBIDDEN,
        AUTH,
        NETWORK,
        UNEXPECTED,
        NO_DATA
    }

    companion object {
        private const val WARN_MS = 12L * 60L * 60L * 1000L
        private const val STALE_MS = 36L * 60L * 60L * 1000L
    }
}
