package br.com.faru.boitata.core.clock

import androidx.compose.runtime.Composable

/** The desktop has no system-wide setting to read; force it with [SceneConfig.reducedMotion] if your app has its own. */
@Composable
public actual fun rememberSystemReducedMotion(): Boolean = false
