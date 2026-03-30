package com.nativeknights.rulerkit

// KMP note: plain Kotlin enums — move to commonMain as-is in Phase 4.

/**
 * Visual style of the center indicator that marks the selected value.
 *
 * ```
 * LINE      — a simple vertical line
 * TRIANGLE  — a filled triangle pointing toward the ruler
 * ARROW     — a triangle with a stem (like a pin without the circle head)
 * PIN       — a circle on a stem, like a map pin
 * ```
 */
enum class IndicatorType {
    LINE,
    TRIANGLE,
    ARROW,
    PIN
}

/**
 * Whether the indicator sits above or below the ruler track.
 *
 * ```
 * CENTER_TOP    — indicator draws above the tick marks, points downward
 * CENTER_BOTTOM — indicator draws below the tick marks, points upward
 * ```
 */
enum class IndicatorPosition {
    CENTER_TOP,
    CENTER_BOTTOM
}
