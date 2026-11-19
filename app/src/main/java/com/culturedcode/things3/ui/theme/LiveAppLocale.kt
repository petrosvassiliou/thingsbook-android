package com.culturedcode.things3.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * Wraps the host [Context] (e.g. `MainActivity`) so `findHostActivity()` still unwraps to the
 * live `Activity` instance while [getResources] and [getAssets] serve the selected locale in-place
 * with zero Activity recreation or screen flash.
 */
internal class LocalizedActivityContextWrapper(
    base: Context,
    private val localizedContext: Context
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedContext.resources
    override fun getAssets(): AssetManager = localizedContext.assets
}

@Immutable
data class LiveAppLocaleOverride(
    val languageTag: String,
    val locale: Locale,
    val configuration: Configuration,
    val localizedContext: Context,
    val layoutDirection: LayoutDirection
)

val LocalLiveAppLocaleOverride = compositionLocalOf<LiveAppLocaleOverride?> { null }

fun resolveEffectiveAppLocale(languageTag: String): Locale {
    val normalized = languageTag.trim()
    if (normalized.isNotEmpty()) {
        val parsed = Locale.forLanguageTag(normalized)
        if (parsed.language.isNotBlank()) {
            return parsed
        }
    }
    val sysLocales = runCatching {
        ConfigurationCompat.getLocales(Resources.getSystem().configuration)
    }.getOrNull()
    return sysLocales?.get(0) ?: Locale.getDefault()
}

fun createLocalizedContextForTag(base: Context, languageTag: String): Context {
    val targetLocale = resolveEffectiveAppLocale(languageTag)
    val config = Configuration(base.resources.configuration).apply {
        setLocales(LocaleList(targetLocale))
        setLayoutDirection(targetLocale)
    }
    return runCatching { base.createConfigurationContext(config) }.getOrDefault(base)
}

@Composable
fun ProvideLiveAppLocale(
    languageTag: String,
    systemConfigRevision: Int = 0,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val targetLocale = remember(languageTag, systemConfigRevision, baseConfiguration) {
        resolveEffectiveAppLocale(languageTag)
    }
    val layoutDirection = remember(targetLocale) {
        if (TextUtils.getLayoutDirectionFromLocale(targetLocale) == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }
    val localizedConfiguration = remember(baseConfiguration, targetLocale) {
        Configuration(baseConfiguration).apply {
            setLocales(LocaleList(targetLocale))
            setLayoutDirection(targetLocale)
        }
    }
    val wrappedContext = remember(baseContext, localizedConfiguration) {
        val unwrappedBase = unwrapLocalizedWrapper(baseContext)
        val configCtx = unwrappedBase.createConfigurationContext(localizedConfiguration)
        LocalizedActivityContextWrapper(unwrappedBase, configCtx)
    }
    val overrideState = remember(
        languageTag,
        targetLocale,
        localizedConfiguration,
        wrappedContext,
        layoutDirection
    ) {
        LiveAppLocaleOverride(
            languageTag = languageTag,
            locale = targetLocale,
            configuration = localizedConfiguration,
            localizedContext = wrappedContext,
            layoutDirection = layoutDirection
        )
    }
    CompositionLocalProvider(
        LocalLiveAppLocaleOverride provides overrideState,
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides wrappedContext,
        LocalLayoutDirection provides layoutDirection,
        content = content
    )
}

/**
 * Re-applies the active [LiveAppLocaleOverride] inside Compose `Dialog` and `Popup` subcompositions,
 * where Android's `AbstractComposeView` re-provides `LocalConfiguration` and `LocalContext` from
 * the raw window context.
 */
@Composable
fun ApplyInheritedLiveAppLocaleIfNeeded(content: @Composable () -> Unit) {
    val override = LocalLiveAppLocaleOverride.current
    if (override == null || LocalContext.current === override.localizedContext) {
        content()
    } else {
        CompositionLocalProvider(
            LocalConfiguration provides override.configuration,
            LocalContext provides override.localizedContext,
            LocalLayoutDirection provides override.layoutDirection,
            content = content
        )
    }
}

private tailrec fun unwrapLocalizedWrapper(context: Context): Context =
    if (context is LocalizedActivityContextWrapper) {
        unwrapLocalizedWrapper(context.baseContext)
    } else {
        context
    }
