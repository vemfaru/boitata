package br.com.faru.boitata.core.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private fun prefersReducedMotion(): Boolean = js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

@Composable
public actual fun rememberSystemReducedMotion(): Boolean = remember { prefersReducedMotion() }
