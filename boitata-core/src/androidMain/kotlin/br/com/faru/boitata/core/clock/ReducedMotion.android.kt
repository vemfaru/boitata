package br.com.faru.boitata.core.clock

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
public actual fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    // "Remove animations" sets the animator duration scale to 0.
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
