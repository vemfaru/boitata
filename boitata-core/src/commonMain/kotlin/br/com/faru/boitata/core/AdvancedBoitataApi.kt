package br.com.faru.boitata.core

/**
 * Marks the low-level drawing functions (`drawRain`, `drawFlame`, `drawCastShadow`, ...).
 *
 * The composables (`Rain`, `Fire`, `CastShadow`, ...) are the safe way in: they read time and quality from the scene's clock, draw only in the draw
 * phase, and get cheaper on their own when frames run long. A low-level function just draws: **you** must pass it a time read inside a draw lambda
 * (never in the composable body), scale its work by the scene's [Quality], and not call it from composition. Opt in only when you draw many things
 * into one canvas and have read `docs/performance.md`.
 */
@RequiresOptIn(
    message = "Low-level drawing: read time and quality in the draw phase yourself. Prefer the composable that wraps it.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class AdvancedBoitataApi
