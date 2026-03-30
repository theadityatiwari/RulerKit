package com.nativeknights.rulerkit

// KMP note: zero Android imports — move to commonMain as-is in Phase 4.
// Colors are ARGB-packed Ints (same representation Compose's Color uses internally).
// Dimensions are in dp/sp as Floats; RulerPickerView converts to px using screen density.

/**
 * Immutable configuration for [RulerPickerView].
 *
 * All properties have sensible defaults. Override only what you need:
 * ```kotlin
 * val config = RulerConfig(
 *     inputType      = InputType.Weight(WeightUnit.KG),
 *     indicatorColor = 0xFF00BCD4.toInt(),
 *     showLabels     = false
 * )
 * rulerPicker.config = config
 * ```
 *
 * Or use the companion factories for the most common cases:
 * ```kotlin
 * RulerConfig.weight()
 * RulerConfig.height(HeightUnit.INCH)
 * RulerConfig.distance(DistanceUnit.MILES)
 * ```
 */
data class RulerConfig(

    // ─────────────────────────────────────────────
    // Input type & range
    // ─────────────────────────────────────────────

    /** What the ruler is measuring and which unit it uses. Drives all defaults. */
    val inputType: InputType = InputType.Weight(),

    /**
     * Override the minimum value. If null, uses [RulerUnit.defaultMin].
     * Must be < [maxValue] if both are set.
     */
    val minValue: Float? = null,

    /**
     * Override the maximum value. If null, uses [RulerUnit.defaultMax].
     * Must be > [minValue] if both are set.
     */
    val maxValue: Float? = null,

    /**
     * The value the ruler snaps to on first draw.
     * If null, defaults to [effectiveMin].
     */
    val initialValue: Float? = null,

    // ─────────────────────────────────────────────
    // Indicator
    // ─────────────────────────────────────────────

    /** Shape of the center indicator. */
    val indicatorType: IndicatorType = IndicatorType.TRIANGLE,

    /** Whether the indicator sits above or below the tick marks. */
    val indicatorPosition: IndicatorPosition = IndicatorPosition.CENTER_BOTTOM,

    /** Indicator color as ARGB-packed Int (e.g. 0xFFE91E63.toInt()). */
    val indicatorColor: Int = 0xFFE91E63.toInt(),

    /**
     * Height of the indicator tip in dp.
     * For LINE: this is the line height.
     * For TRIANGLE/ARROW/PIN: this is the tip-to-base distance.
     */
    val indicatorSizeDp: Float = 20f,

    /** Stroke width of the indicator in dp (applies to LINE and ARROW stem). */
    val indicatorWidthDp: Float = 2f,

    // ─────────────────────────────────────────────
    // Tick lines
    // ─────────────────────────────────────────────

    /** Height of a major tick in dp. Major ticks fall every [RulerUnit.majorLineEvery] steps. */
    val majorLineHeightDp: Float = 56f,

    /** Stroke width of major ticks in dp. */
    val majorLineWidthDp: Float = 2f,

    /** Color of major ticks as ARGB-packed Int. */
    val majorLineColor: Int = 0xFF212121.toInt(),

    /** Height of a medium tick in dp. Medium ticks fall every [RulerUnit.mediumLineEvery] steps. */
    val mediumLineHeightDp: Float = 36f,

    /** Stroke width of medium ticks in dp. */
    val mediumLineWidthDp: Float = 1.5f,

    /** Color of medium ticks as ARGB-packed Int. */
    val mediumLineColor: Int = 0xFF757575.toInt(),

    /** Height of a minor tick in dp. Minor ticks fall on every step. */
    val minorLineHeightDp: Float = 20f,

    /** Stroke width of minor ticks in dp. */
    val minorLineWidthDp: Float = 1f,

    /** Color of minor ticks as ARGB-packed Int. */
    val minorLineColor: Int = 0xFFBDBDBD.toInt(),

    // ─────────────────────────────────────────────
    // Labels (numbers under major ticks)
    // ─────────────────────────────────────────────

    /** Whether to draw number labels below major ticks. */
    val showLabels: Boolean = true,

    /** Text size of tick labels in sp. */
    val labelTextSizeSp: Float = 11f,

    /** Color of tick labels as ARGB-packed Int. */
    val labelTextColor: Int = 0xFF424242.toInt(),

    // ─────────────────────────────────────────────
    // Selected value display
    // ─────────────────────────────────────────────

    /** Whether to draw the selected value + unit above/below the ruler. */
    val showValueLabel: Boolean = true,

    /** Text size of the selected value in sp. */
    val valueLabelTextSizeSp: Float = 26f,

    /** Color of the selected value text as ARGB-packed Int. */
    val valueLabelTextColor: Int = 0xFF212121.toInt(),

    // ─────────────────────────────────────────────
    // Layout & spacing
    // ─────────────────────────────────────────────

    /** Background color of the ruler view as ARGB-packed Int. */
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),

    /**
     * Horizontal distance between consecutive minor ticks in dp.
     * Increase for a more spread-out ruler; decrease for denser ticks.
     */
    val tickSpacingDp: Float = 12f,

    /**
     * If true, the ruler is flipped: ticks point upward and labels sit at the top.
     * Useful when the view is anchored at the bottom of the screen.
     */
    val flipVertically: Boolean = false,

    /** If true, the device vibrates lightly each time the value changes by one step. */
    val enableHapticFeedback: Boolean = true,

) {

    // ─────────────────────────────────────────────
    // Resolved / effective values
    // ─────────────────────────────────────────────

    /** Resolved minimum — [minValue] if set, otherwise [RulerUnit.defaultMin]. */
    val effectiveMin: Float get() = minValue ?: inputType.unit.defaultMin

    /** Resolved maximum — [maxValue] if set, otherwise [RulerUnit.defaultMax]. */
    val effectiveMax: Float get() = maxValue ?: inputType.unit.defaultMax

    /** Resolved initial value — [initialValue] if set, otherwise [effectiveMin]. */
    val effectiveInitialValue: Float get() = initialValue?.coerceIn(effectiveMin, effectiveMax)
        ?: effectiveMin

    /** Total number of minor ticks from min to max. */
    val totalSteps: Int get() {
        val step = inputType.unit.defaultStep
        return ((effectiveMax - effectiveMin) / step).toInt()
    }

    // ─────────────────────────────────────────────
    // Companion factories
    // ─────────────────────────────────────────────

    companion object {

        /** Quick factory for weight picking. */
        fun weight(unit: WeightUnit = WeightUnit.KG) =
            RulerConfig(inputType = InputType.Weight(unit))

        /** Quick factory for height picking. */
        fun height(unit: HeightUnit = HeightUnit.CM) =
            RulerConfig(inputType = InputType.Height(unit))

        /** Quick factory for distance picking. */
        fun distance(unit: DistanceUnit = DistanceUnit.KM) =
            RulerConfig(inputType = InputType.Distance(unit))

        /** Quick factory for a fully custom ruler. */
        fun custom(
            min: Float,
            max: Float,
            step: Float,
            unitLabel: String = "",
            majorEvery: Int = 10,
            mediumEvery: Int = 5
        ) = RulerConfig(
            inputType = InputType.Custom(
                min         = min,
                max         = max,
                step        = step,
                unitLabel   = unitLabel,
                majorEvery  = majorEvery,
                mediumEvery = mediumEvery
            )
        )
    }
}
