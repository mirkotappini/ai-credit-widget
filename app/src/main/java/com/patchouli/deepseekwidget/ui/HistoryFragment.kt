package com.patchouli.deepseekwidget.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.patchouli.deepseekwidget.MainActivity
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.data.Snapshot
import com.patchouli.deepseekwidget.logic.SpendCalculator
import com.patchouli.deepseekwidget.net.CostBucket
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.repo.BalanceRepository
import com.patchouli.deepseekwidget.repo.OpenAiCostsRepository
import com.patchouli.deepseekwidget.util.Format
import com.patchouli.deepseekwidget.view.BalanceChartView
import com.patchouli.deepseekwidget.view.DailySpendChartView
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HistoryFragment : Fragment() {

    private var rangeDays = 7
    private var displayedCount = PAGE_SIZE

    private lateinit var scrollView: NestedScrollView
    private lateinit var providerGroup: MaterialButtonToggleGroup
    private lateinit var providerSelector: ProviderSelector
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rangeGroup: MaterialButtonToggleGroup
    private lateinit var balanceCard: View
    private lateinit var spendCard: View
    private lateinit var balanceChart: BalanceChartView
    private lateinit var spendChart: DailySpendChartView
    private lateinit var snapshotList: LinearLayout
    private lateinit var snapshotSection: View
    private lateinit var emptyContainer: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var emptyAction: MaterialButton
    private lateinit var emptyActionSecondary: MaterialButton
    private lateinit var snapshotHeader: TextView
    private lateinit var dataSince: TextView
    private lateinit var currentBalance: TextView
    private lateinit var minValue: TextView
    private lateinit var maxValue: TextView
    private lateinit var chartCaption: TextView
    private lateinit var minMaxRow: View
    private lateinit var spendTotal: TextView
    private lateinit var loadMore: MaterialButton
    private lateinit var statusStrip: View
    private lateinit var statusDot: ImageView
    private lateinit var statusText: TextView
    private lateinit var statusAction: MaterialButton

    private lateinit var openAiHistory: View
    private lateinit var openAiPeriodTotal: TextView
    private lateinit var openAiAverage: TextView
    private lateinit var openAiPeriodCaption: TextView
    private lateinit var openAiChartPeriod: TextView
    private lateinit var openAiChart: DailySpendChartView
    private lateinit var openAiListCount: TextView
    private lateinit var openAiCostList: LinearLayout

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRefreshing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rangeDays = savedInstanceState?.getInt(KEY_RANGE, 7) ?: 7
        displayedCount = PAGE_SIZE

        scrollView = view.findViewById(R.id.history_scroll)
        providerGroup = view.findViewById(R.id.provider_group)
        swipeRefresh = view.findViewById(R.id.history_swipe)
        rangeGroup = view.findViewById(R.id.range_group)
        balanceCard = view.findViewById(R.id.balance_card)
        spendCard = view.findViewById(R.id.spend_card)
        balanceChart = view.findViewById(R.id.balance_chart)
        spendChart = view.findViewById(R.id.spend_chart)
        snapshotList = view.findViewById(R.id.snapshot_list)
        snapshotSection = view.findViewById(R.id.snapshot_section)
        emptyContainer = view.findViewById(R.id.history_empty)
        emptyTitle = view.findViewById(R.id.empty_title)
        emptyBody = view.findViewById(R.id.empty_body)
        emptyAction = view.findViewById(R.id.empty_action)
        emptyActionSecondary = view.findViewById(R.id.empty_action_secondary)
        snapshotHeader = view.findViewById(R.id.snapshot_header)
        dataSince = view.findViewById(R.id.data_since)
        currentBalance = view.findViewById(R.id.current_balance)
        minValue = view.findViewById(R.id.min_value)
        maxValue = view.findViewById(R.id.max_value)
        chartCaption = view.findViewById(R.id.chart_caption)
        minMaxRow = view.findViewById(R.id.min_max_row)
        spendTotal = view.findViewById(R.id.spend_total)
        loadMore = view.findViewById(R.id.load_more)
        statusStrip = view.findViewById(R.id.history_status)
        statusDot = view.findViewById(R.id.history_status_dot)
        statusText = view.findViewById(R.id.history_status_text)
        statusAction = view.findViewById(R.id.history_status_action)

        openAiHistory = view.findViewById(R.id.openai_history)
        openAiPeriodTotal = view.findViewById(R.id.openai_period_total)
        openAiAverage = view.findViewById(R.id.openai_average)
        openAiPeriodCaption = view.findViewById(R.id.openai_period_caption)
        openAiChartPeriod = view.findViewById(R.id.openai_chart_period)
        openAiChart = view.findViewById(R.id.openai_history_chart)
        openAiListCount = view.findViewById(R.id.openai_list_count)
        openAiCostList = view.findViewById(R.id.openai_cost_list)

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

        rangeGroup.contentDescription = getString(R.string.history_range_cd)
        rangeGroup.check(if (rangeDays == 30) R.id.range_30 else R.id.range_7)
        rangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            rangeDays = if (checkedId == R.id.range_30) 30 else 7
            displayedCount = PAGE_SIZE
            load()
        }

        loadMore.setOnClickListener {
            displayedCount += PAGE_SIZE
            load()
        }
        statusAction.setOnClickListener { openSettings(Prefs(requireContext()).getActiveProvider()) }

        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_RANGE, rangeDays)
        super.onSaveInstanceState(outState)
    }

    private fun onProviderSelected(provider: Provider) {
        displayedCount = PAGE_SIZE
        scrollView.smoothScrollTo(0, 0)
        load()
    }

    private fun refreshFromNetwork() {
        if (!isAdded || isRefreshing) return
        val context = requireContext()
        val prefs = Prefs(context)
        val provider = prefs.activeProviderOrNull()
        if (provider == null || !prefs.hasApiKey(provider)) {
            swipeRefresh.isRefreshing = false
            load()
            return
        }
        isRefreshing = true
        swipeRefresh.isRefreshing = true
        val appContext = context.applicationContext
        executor.execute {
            try {
                when (provider) {
                    Provider.DEEPSEEK -> BalanceRepository(appContext).refresh()
                    Provider.OPENAI -> OpenAiCostsRepository(appContext).refresh()
                }
            } finally {
                mainHandler.post {
                    if (!isAdded || view == null) return@post
                    isRefreshing = false
                    swipeRefresh.isRefreshing = false
                    load()
                }
            }
        }
    }

    private fun load() {
        if (!isAdded) return
        val prefs = Prefs(requireContext())
        val active = prefs.activeProviderOrNull()
        providerSelector.render(active)
        when (active) {
            null -> showNeutral(prefs)
            Provider.DEEPSEEK -> loadDeepSeek(prefs)
            Provider.OPENAI -> loadCost(prefs, Provider.OPENAI)
        }
    }

    // --- DeepSeek -------------------------------------------------------

    private fun loadDeepSeek(prefs: Prefs) {
        showDeepSeekViews()
        val repository = BalanceRepository(requireContext())
        val today = LocalDate.now()
        val fromDate = today.minusDays((rangeDays - 1).toLong())
        val snapshots = repository.snapshotsForDays(rangeDays)

        val daily = snapshots
            .groupBy { dayOf(it.ts) }
            .mapNotNull { (_, values) -> values.maxByOrNull { it.ts } }
            .sortedBy { it.ts }

        balanceChart.setData(daily.map { it.ts to it.total })
        currentBalance.text = getString(
            R.string.history_current,
            daily.lastOrNull()?.let { Format.money(it.total) } ?: getString(R.string.value_unknown)
        )
        if (daily.isEmpty()) {
            minMaxRow.visibility = View.GONE
            chartCaption.visibility = View.GONE
            balanceChart.contentDescription = getString(R.string.history_chart_no_data)
        } else {
            val min = daily.minOf { it.total }
            val max = daily.maxOf { it.total }
            minValue.text = getString(R.string.history_min, Format.money(min))
            maxValue.text = getString(R.string.history_max, Format.money(max))
            minMaxRow.visibility = View.VISIBLE
            chartCaption.visibility = if (daily.size == 1) View.VISIBLE else View.GONE
            if (daily.size == 1) {
                chartCaption.text = getString(R.string.history_one_point)
                balanceChart.contentDescription = getString(
                    R.string.history_chart_cd_one,
                    Format.money(daily.first().total)
                )
            } else {
                balanceChart.contentDescription = getString(
                    R.string.history_chart_cd,
                    Format.dayLabel(dayOf(daily.first().ts)),
                    Format.dayLabel(dayOf(daily.last().ts)),
                    Format.money(min),
                    Format.money(max),
                    Format.money(daily.last().total)
                )
            }
        }

        val days = (rangeDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val spends = SpendCalculator.spendByDay(snapshots, days)
        spendChart.setData(days.map { day -> day to (spends[day] ?: BigDecimal.ZERO) })
        val totalSpent = spends.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        spendTotal.text = getString(R.string.history_spend_total, Format.money(totalSpent))
        val maxSpend = spends.maxByOrNull { it.value }
        spendChart.contentDescription = if (totalSpent.signum() > 0 && maxSpend != null) {
            getString(
                R.string.history_spend_cd,
                Format.money(totalSpent),
                Format.money(maxSpend.value),
                Format.dayLabel(maxSpend.key)
            )
        } else {
            getString(R.string.history_spend_cd_none)
        }

        dataSince.visibility = if (snapshots.isNotEmpty() && dayOf(snapshots.first().ts) > fromDate) {
            dataSince.text = getString(
                R.string.history_data_since,
                Format.dayLabel(dayOf(snapshots.first().ts))
            )
            View.VISIBLE
        } else {
            View.GONE
        }

        snapshotHeader.text = resources.getQuantityString(
            R.plurals.history_snapshot_count,
            snapshots.size,
            snapshots.size
        )
        emptyContainer.visibility = if (snapshots.isEmpty()) View.VISIBLE else View.GONE
        if (snapshots.isEmpty()) {
            emptyTitle.text = getString(R.string.history_empty_title)
            emptyBody.text = getString(
                if (prefs.hasApiKey(Provider.DEEPSEEK)) R.string.history_empty_body
                else R.string.history_empty_body_no_key
            )
            emptyAction.text = getString(
                if (prefs.hasApiKey(Provider.DEEPSEEK)) R.string.history_empty_dashboard
                else R.string.history_empty_add_key
            )
            emptyAction.setOnClickListener {
                val main = activity as? MainActivity ?: return@setOnClickListener
                if (Prefs(requireContext()).hasApiKey(Provider.DEEPSEEK)) main.showDashboard()
                else main.showSettings(Provider.DEEPSEEK)
            }
            emptyActionSecondary.visibility = View.GONE
            snapshotSection.visibility = View.GONE
            loadMore.visibility = View.GONE
        } else {
            snapshotSection.visibility = View.VISIBLE
        }

        renderList(snapshots, spends)
        renderDeepSeekStatus(prefs, snapshots.isNotEmpty())
        swipeRefresh.isEnabled = prefs.hasApiKey(Provider.DEEPSEEK)
    }

    private fun renderList(snapshots: List<Snapshot>, spends: Map<LocalDate, BigDecimal>) {
        snapshotList.removeAllViews()
        val ordered = snapshots.sortedWith(
            compareByDescending<Snapshot> { it.ts }.thenByDescending { it.id }
        )
        val page = ordered.take(displayedCount)
        val grouped = page.groupBy { dayOf(it.ts) }
        var rowIndex = 0
        grouped.forEach { (day, items) ->
            val header = layoutInflater.inflate(R.layout.item_day_header, snapshotList, false)
            header.findViewById<TextView>(R.id.day_header).text = dayHeader(day)
            val spent = spends[day] ?: BigDecimal.ZERO
            header.findViewById<TextView>(R.id.day_spend).text = if (spent.signum() > 0) {
                getString(R.string.history_day_spend, Format.money(spent))
            } else {
                getString(R.string.value_unknown)
            }
            snapshotList.addView(header)

            items.forEach { snapshot ->
                val row = layoutInflater.inflate(R.layout.item_snapshot, snapshotList, false)
                val time = Format.shortTime(snapshot.ts)
                val total = Format.money(snapshot.total)
                row.findViewById<TextView>(R.id.snapshot_time).text = time
                row.findViewById<TextView>(R.id.snapshot_total).text = total
                row.contentDescription = getString(R.string.history_snapshot_cd, time, total)
                if (rowIndex == page.lastIndex) {
                    row.findViewById<View>(R.id.snapshot_divider).visibility = View.GONE
                }
                snapshotList.addView(row)
                rowIndex++
            }
        }

        val hasMore = ordered.size > displayedCount
        loadMore.visibility = if (hasMore) View.VISIBLE else View.GONE
        if (hasMore) {
            loadMore.text = getString(R.string.history_load_more)
            loadMore.isEnabled = true
        }
    }

    // --- Provider a soli costi (OpenAI) --------------------------------

    private fun loadCost(prefs: Prefs, provider: Provider) {
        showOpenAiViews()
        val hasKey = prefs.hasApiKey(provider)
        val error = prefs.getLastError(provider)
        val todayUtc = LocalDate.now(ZoneOffset.UTC)
        val from = todayUtc.minusDays((rangeDays - 1).toLong())
        val since = from.atStartOfDay(ZoneOffset.UTC).toInstant().epochSecond
        val daily = OpenAiCostsRepository(requireContext())
            .dailyBucketsSince(since)
            .filter { dayOfUtc(it.startTsUtc) in from..todayUtc }

        swipeRefresh.isEnabled = hasKey
        openAiChartPeriod.text = getString(
            if (rangeDays == 30) R.string.openai_days_30 else R.string.openai_days_7
        )
        openAiListCount.text = getString(
            if (rangeDays == 30) R.string.openai_days_30 else R.string.openai_days_7
        )

        if (!hasKey) {
            openAiHistory.visibility = View.GONE
            emptyContainer.visibility = View.VISIBLE
            emptyTitle.text = getString(R.string.openai_no_key_title)
            emptyBody.text = getString(R.string.openai_no_key_body)
            emptyAction.text = getString(R.string.openai_add_admin_key)
            emptyAction.setOnClickListener { openSettings(provider) }
            emptyActionSecondary.visibility = View.GONE
            statusStrip.visibility = View.GONE
            return
        }

        if (daily.isEmpty()) {
            openAiHistory.visibility = View.GONE
            emptyContainer.visibility = View.VISIBLE
            emptyTitle.text = when (error) {
                Prefs.ERROR_FORBIDDEN -> getString(R.string.openai_forbidden_title)
                Prefs.ERROR_AUTH -> getString(R.string.openai_auth_title)
                Prefs.ERROR_NETWORK -> getString(R.string.openai_network_title)
                null -> getString(R.string.openai_no_data_title)
                else -> getString(R.string.openai_empty_period_title)
            }
            emptyBody.text = when (error) {
                Prefs.ERROR_FORBIDDEN -> getString(R.string.openai_forbidden_body)
                Prefs.ERROR_AUTH -> getString(R.string.openai_auth_body)
                Prefs.ERROR_NETWORK -> getString(R.string.openai_network_body)
                null -> getString(R.string.openai_no_data_body)
                else -> getString(R.string.openai_empty_period_body)
            }
            emptyAction.text = getString(R.string.openai_retry)
            emptyAction.setOnClickListener { refreshFromNetwork() }
            if (error == Prefs.ERROR_FORBIDDEN || error == Prefs.ERROR_AUTH) {
                emptyActionSecondary.text = getString(R.string.openai_open_settings)
                emptyActionSecondary.visibility = View.VISIBLE
                emptyActionSecondary.setOnClickListener { openSettings(provider) }
            } else {
                emptyActionSecondary.visibility = View.GONE
            }
            statusStrip.visibility = View.GONE
            return
        }

        openAiHistory.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        renderOpenAiStatus(prefs, daily, provider)

        val byDay = daily
            .groupBy { dayOfUtc(it.startTsUtc) }
            .mapValues { (_, buckets) -> buckets.fold(BigDecimal.ZERO) { a, b -> a.add(b.amount) } }
        val days = (rangeDays - 1 downTo 0).map { todayUtc.minusDays(it.toLong()) }
        val total = byDay.values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }
        val daysWithData = days.count { byDay.containsKey(it) }
        openAiPeriodTotal.text = Format.money(total)
        openAiAverage.text = if (daysWithData > 0) {
            Format.money(total.divide(BigDecimal(daysWithData), 2, RoundingMode.HALF_UP))
        } else {
            getString(R.string.value_unknown)
        }
        if (daysWithData in 1 until rangeDays) {
            openAiPeriodCaption.text = getString(
                R.string.openai_partial_days,
                daysWithData,
                rangeDays
            )
            openAiPeriodCaption.visibility = View.VISIBLE
        } else {
            openAiPeriodCaption.visibility = View.GONE
        }

        openAiChart.setCompact(false)
        openAiChart.setMissingDays(days.filter { !byDay.containsKey(it) }.toSet())
        openAiChart.setData(days.map { day -> day to (byDay[day] ?: BigDecimal.ZERO) })
        openAiChart.contentDescription = getString(
            R.string.history_spend_cd,
            Format.money(total),
            Format.money(byDay.values.maxOrNull() ?: BigDecimal.ZERO),
            Format.dayLabel(byDay.maxByOrNull { it.value }?.key ?: todayUtc)
        )

        renderOpenAiList(byDay, todayUtc)
    }

    private fun renderOpenAiList(byDay: Map<LocalDate, BigDecimal>, todayUtc: LocalDate) {
        openAiCostList.removeAllViews()
        val ordered = byDay.entries.sortedByDescending { it.key }
        ordered.forEachIndexed { index, entry ->
            val day = entry.key
            val isToday = day == todayUtc
            val isYesterday = day == todayUtc.minusDays(1)
            val row = layoutInflater.inflate(R.layout.item_cost_day, openAiCostList, false)
            val label = when {
                isToday -> getString(
                    R.string.openai_today_row,
                    Format.dayLabel(day).uppercase(Locale.ITALY)
                )
                isYesterday -> getString(
                    R.string.openai_yesterday_row,
                    Format.dayLabel(day).uppercase(Locale.ITALY)
                )
                else -> Format.dayLabel(day).uppercase(Locale.ITALY)
            }
            row.layoutParams = row.layoutParams.apply {
                height = resources.getDimensionPixelSize(
                    if (isToday) R.dimen.ds_cost_row_today else R.dimen.ds_cost_row
                )
            }
            row.findViewById<TextView>(R.id.cost_day_label).text = label
            row.findViewById<TextView>(R.id.cost_day_amount).text = Format.money(entry.value)
            val sub = row.findViewById<TextView>(R.id.cost_day_sub)
            if (isToday) {
                sub.text = getString(R.string.openai_in_progress)
                sub.visibility = View.VISIBLE
            } else {
                sub.visibility = View.GONE
            }
            row.contentDescription = "$label, ${Format.money(entry.value)}"
            if (index == ordered.lastIndex) {
                row.findViewById<View>(R.id.cost_day_divider).visibility = View.GONE
            }
            openAiCostList.addView(row)
        }
    }

    // --- Stati ----------------------------------------------------------

    private fun showNeutral(prefs: Prefs) {
        hideAllBodies()
        providerSelector.render(null)
        emptyContainer.visibility = View.VISIBLE
        emptyTitle.text = getString(R.string.provider_none_title)
        emptyBody.text = getString(R.string.provider_none_body)
        emptyAction.text = getString(R.string.provider_configure_deepseek)
        emptyAction.setOnClickListener { openSettings(Provider.DEEPSEEK) }
        emptyActionSecondary.text = getString(R.string.provider_configure_openai)
        emptyActionSecondary.visibility = View.VISIBLE
        emptyActionSecondary.setOnClickListener { openSettings(Provider.OPENAI) }
        statusStrip.visibility = View.GONE
        swipeRefresh.isEnabled = false
    }

    private fun showDeepSeekViews() {
        openAiHistory.visibility = View.GONE
        balanceCard.visibility = View.VISIBLE
        spendCard.visibility = View.VISIBLE
        dataSince.visibility = View.VISIBLE
        snapshotSection.visibility = View.VISIBLE
        snapshotList.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        emptyActionSecondary.visibility = View.GONE
    }

    private fun showOpenAiViews() {
        balanceCard.visibility = View.GONE
        spendCard.visibility = View.GONE
        dataSince.visibility = View.GONE
        snapshotSection.visibility = View.GONE
        snapshotList.visibility = View.GONE
        loadMore.visibility = View.GONE
        openAiHistory.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        emptyActionSecondary.visibility = View.GONE
    }

    private fun hideAllBodies() {
        openAiHistory.visibility = View.GONE
        balanceCard.visibility = View.GONE
        spendCard.visibility = View.GONE
        dataSince.visibility = View.GONE
        snapshotSection.visibility = View.GONE
        snapshotList.visibility = View.GONE
        loadMore.visibility = View.GONE
    }

    private fun renderDeepSeekStatus(prefs: Prefs, hasData: Boolean) {
        val error = prefs.getLastError(Provider.DEEPSEEK)
        if (!prefs.hasApiKey(Provider.DEEPSEEK)) {
            statusStrip.visibility = View.GONE
            return
        }
        val (background, dot, text) = when (error) {
            Prefs.ERROR_NETWORK -> Triple(
                R.drawable.bg_banner_warning,
                R.drawable.dot_warning,
                getString(
                    if (hasData) R.string.history_status_offline
                    else R.string.history_status_offline_empty
                )
            )
            Prefs.ERROR_AUTH -> Triple(
                R.drawable.bg_banner_error,
                R.drawable.dot_error,
                getString(
                    if (hasData) R.string.history_status_auth
                    else R.string.history_status_auth_empty
                )
            )
            Prefs.ERROR_UNEXPECTED -> Triple(
                R.drawable.bg_banner_error,
                R.drawable.dot_error,
                getString(
                    if (hasData) R.string.history_status_unexpected
                    else R.string.history_status_unexpected_empty
                )
            )
            else -> {
                statusStrip.visibility = View.GONE
                return
            }
        }
        statusStrip.visibility = View.VISIBLE
        statusStrip.setBackgroundResource(background)
        statusDot.setImageResource(dot)
        statusText.text = text
        statusText.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (error == Prefs.ERROR_NETWORK) R.color.ds_color_text_secondary
                else R.color.ds_color_error
            )
        )
        statusAction.visibility = if (error == Prefs.ERROR_AUTH) View.VISIBLE else View.GONE
        if (error == Prefs.ERROR_AUTH) {
            statusAction.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.ds_color_error)
            )
        }
    }

    private fun renderOpenAiStatus(
        prefs: Prefs,
        daily: List<CostBucket>,
        provider: Provider
    ) {
        val error = prefs.getLastError(provider)
        val lastEndMs = (daily.maxOfOrNull { it.endTsUtc } ?: 0L) * 1000L
        val stale = lastEndMs > 0L &&
            System.currentTimeMillis() - lastEndMs > 36L * 60L * 60L * 1000L
        val whenText = if (lastEndMs > 0L) {
            getString(
                R.string.openai_stale_body,
                Format.utcDayLabel(lastEndMs),
                Format.utcTime(lastEndMs)
            )
        } else {
            getString(R.string.error_data_none)
        }
        val (background, dot, text, actionVisible, textColor) = when {
            error == Prefs.ERROR_FORBIDDEN -> StatusStrip(
                R.drawable.bg_banner_error,
                R.drawable.dot_error,
                getString(R.string.openai_forbidden_banner),
                true,
                R.color.ds_color_error
            )
            error == Prefs.ERROR_AUTH -> StatusStrip(
                R.drawable.bg_banner_error,
                R.drawable.dot_error,
                getString(R.string.openai_auth_banner),
                true,
                R.color.ds_color_error
            )
            error == Prefs.ERROR_NETWORK -> StatusStrip(
                R.drawable.bg_banner_warning,
                R.drawable.dot_warning,
                getString(
                    R.string.openai_offline_banner,
                    Format.utcDayLabel(lastEndMs),
                    Format.utcTime(lastEndMs)
                ),
                false,
                R.color.ds_color_text_secondary
            )
            error == Prefs.ERROR_UNEXPECTED -> StatusStrip(
                R.drawable.bg_banner_error,
                R.drawable.dot_error,
                getString(R.string.openai_unexpected_banner),
                false,
                R.color.ds_color_error
            )
            stale -> StatusStrip(
                R.drawable.bg_banner_warning,
                R.drawable.dot_warning,
                getString(R.string.openai_stale_title) + ". " + whenText,
                false,
                R.color.ds_color_text_secondary
            )
            else -> {
                statusStrip.visibility = View.GONE
                return
            }
        }
        statusStrip.visibility = View.VISIBLE
        statusStrip.setBackgroundResource(background)
        statusDot.setImageResource(dot)
        statusText.text = text
        statusText.setTextColor(ContextCompat.getColor(requireContext(), textColor))
        statusAction.visibility = if (actionVisible) View.VISIBLE else View.GONE
        statusAction.text = getString(R.string.openai_settings)
        statusAction.setTextColor(ContextCompat.getColor(requireContext(), R.color.ds_color_error))
        statusAction.setOnClickListener { openSettings(provider) }
    }

    private fun dayHeader(day: LocalDate): String {
        val label = Format.dayLabel(day).uppercase(Locale.ITALY)
        val today = LocalDate.now()
        return when (day) {
            today -> getString(R.string.history_day_today, label)
            today.minusDays(1) -> getString(R.string.history_day_yesterday, label)
            else -> getString(R.string.history_day, label)
        }
    }

    private fun dayOf(ts: Long): LocalDate =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun dayOfUtc(tsSec: Long): LocalDate =
        Instant.ofEpochSecond(tsSec).atZone(ZoneOffset.UTC).toLocalDate()

    private fun openSettings(provider: Provider) {
        (activity as? MainActivity)?.showSettings(provider)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private data class StatusStrip(
        val background: Int,
        val dot: Int,
        val text: String,
        val action: Boolean,
        val textColor: Int
    )

    companion object {
        private const val PAGE_SIZE = 50
        private const val KEY_RANGE = "history_range"
    }
}
