package com.patchouli.deepseekwidget.ui

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.provider.Provider

/**
 * Selettore provider condiviso. In Dashboard/Storico salva il provider attivo;
 * in Impostazioni cambia solo il pannello visibile. Un provider senza key non
 * diventa attivo: il tap porta alle sue impostazioni.
 */
class ProviderSelector(
    private val group: MaterialButtonToggleGroup,
    private val prefs: Prefs,
    private val persistSelection: Boolean,
    private val onSelected: (Provider) -> Unit,
    private val onNeedsSettings: (Provider) -> Unit
) {

    private var selected: Provider? = null
    private var binding = false

    init {
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (binding || !isChecked) return@addOnButtonCheckedListener
            val provider = providerFor(checkedId) ?: return@addOnButtonCheckedListener
            val configured = prefs.hasApiKey(provider)
            if (!configured && persistSelection) {
                onNeedsSettings(provider)
                render(prefs.activeProviderOrNull(), selected)
                return@addOnButtonCheckedListener
            }
            selected = provider
            if (persistSelection) prefs.setActiveProvider(provider)
            onSelected(provider)
        }
    }

    fun render(active: Provider?, selectedTab: Provider? = active) {
        if (binding) return
        binding = true
        selected = selectedTab
        val checked = selectedTab ?: active
        // Con nessun provider attivo il gruppo deve poter restare senza selezione;
        // con un attivo si impedisce che un tap lo deselezioni.
        group.isSelectionRequired = checked != null
        group.clearChecked()
        if (checked != null) {
            group.check(idFor(checked))
        }
        val activeProvider = active
        val configuredTab = selectedTab
        for (provider in Provider.entries) {
            styleButton(buttonFor(provider), provider, activeProvider, configuredTab)
        }
        binding = false
    }

    private fun styleButton(
        button: MaterialButton,
        provider: Provider,
        active: Provider?,
        selectedTab: Provider?
    ) {
        val configured = prefs.hasApiKey(provider)
        val isActive = provider == active
        button.isEnabled = true
        button.isCheckable = true

        if (!configured) {
            button.setTextColor(color(R.color.ds_color_text_tertiary))
            button.backgroundTintList = ColorStateList.valueOf(color(R.color.ds_color_surface))
            button.strokeColor = ColorStateList.valueOf(color(R.color.ds_color_border))
            button.setIconResource(R.drawable.dot_provider_warning)
            button.iconSize = resources(provider).getDimensionPixelSize(R.dimen.ds_provider_dot)
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            button.iconPadding = resources(provider).getDimensionPixelSize(R.dimen.ds_space_4)
            button.iconTint = ColorStateList.valueOf(color(R.color.ds_color_warning))
        } else {
            button.icon = null
            button.setTextColor(ContextCompat.getColorStateList(context(button), R.color.ds_segment_text))
            button.backgroundTintList =
                ContextCompat.getColorStateList(context(button), R.color.ds_segment_bg)
            button.strokeColor =
                ContextCompat.getColorStateList(context(button), R.color.ds_segment_stroke)
        }

        button.contentDescription = when {
            isActive -> context(button).getString(
                R.string.provider_cd_active,
                context(button).getString(labelRes(provider))
            )
            !configured -> context(button).getString(
                R.string.provider_cd_missing,
                context(button).getString(labelRes(provider))
            )
            else -> context(button).getString(
                R.string.provider_cd_inactive,
                context(button).getString(labelRes(provider))
            )
        }
        button.visibility = View.VISIBLE
    }

    private fun context(button: MaterialButton) = button.context
    private fun color(res: Int) = ContextCompat.getColor(group.context, res)
    private fun resources(provider: Provider) = group.context.resources

    private fun idFor(provider: Provider): Int = when (provider) {
        Provider.DEEPSEEK -> R.id.provider_deepseek
        Provider.OPENAI -> R.id.provider_openai
        Provider.CLAUDE -> R.id.provider_claude
    }

    private fun buttonFor(provider: Provider): MaterialButton = group.findViewById(idFor(provider))

    private fun providerFor(id: Int): Provider? = when (id) {
        R.id.provider_deepseek -> Provider.DEEPSEEK
        R.id.provider_openai -> Provider.OPENAI
        R.id.provider_claude -> Provider.CLAUDE
        else -> null
    }

    private fun labelRes(provider: Provider): Int = when (provider) {
        Provider.DEEPSEEK -> R.string.provider_deepseek
        Provider.OPENAI -> R.string.provider_openai
        Provider.CLAUDE -> R.string.provider_claude
    }
}
