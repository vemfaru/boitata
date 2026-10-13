package br.com.faru.boitata.core.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
public actual fun rememberSystemReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
