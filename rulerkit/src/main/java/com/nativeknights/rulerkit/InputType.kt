package com.nativeknights.rulerkit

// KMP note: this file has zero Android imports — move to commonMain as-is in Phase 4.

/**
 * Defines what kind of measurement the ruler is picking, and which unit it uses.
 *
 * Each subtype carries a [RulerUnit] that provides all defaults (range, step, labels).
 * [RulerPickerView] reads [unit] to configure itself; override individual values via
 * XML attributes or [RulerConfig] if you need to deviate from the unit's defaults.
 *
 * Usage:
 * ```kotlin
 * // Preset types
 * rulerPicker.inputType = InputType.Weight(WeightUnit.KG)
 * rulerPicker.inputType = InputType.Height(HeightUnit.CM)
 * rulerPicker.inputType = InputType.Distance(DistanceUnit.KM)
 *
 * // Fully custom
 * rulerPicker.inputType = InputType.Custom(
 *     min = 1f, max = 10f, step = 0.5f, unitLabel = "pts"
 * )
 * ```
 */
sealed class InputType {

    /** The [RulerUnit] backing this input type. Provides all defaults. */
    abstract val unit: RulerUnit

    // ─────────────────────────────────────────────
    // Preset types
    // ─────────────────────────────────────────────

    data class Weight(override val unit: WeightUnit = WeightUnit.KG) : InputType()

    data class Height(override val unit: HeightUnit = HeightUnit.CM) : InputType()

    data class Distance(override val unit: DistanceUnit = DistanceUnit.KM) : InputType()

    // ─────────────────────────────────────────────
    // Custom type — user controls everything
    // ─────────────────────────────────────────────

    /**
     * Fully custom input with user-defined range, step, and label.
     *
     * @param min Minimum selectable value.
     * @param max Maximum selectable value.
     * @param step Increment per tick (e.g. 0.5, 1.0, 5.0).
     * @param unitLabel Text shown next to the selected value (e.g. "pts", "reps", "°").
     * @param majorEvery Draw a major (tallest) line every N steps.
     * @param mediumEvery Draw a medium line every N steps.
     * @param valueFormatter How to display the current value. Defaults to 1 decimal place.
     */
    data class Custom(
        val min: Float,
        val max: Float,
        val step: Float,
        val unitLabel: String = "",
        val majorEvery: Int = 10,
        val mediumEvery: Int = 5,
        val valueFormatter: (Float) -> String = { String.format("%.1f", it) }
    ) : InputType() {

        override val unit: RulerUnit = object : RulerUnit {
            override val label          = unitLabel
            override val defaultMin     = min
            override val defaultMax     = max
            override val defaultStep    = step
            override val majorLineEvery = majorEvery
            override val mediumLineEvery = mediumEvery
            override fun formatValue(value: Float) = valueFormatter(value)
        }
    }
}

// ─────────────────────────────────────────────
// Unit switching helpers
// ─────────────────────────────────────────────

/**
 * Convert a value from this [InputType]'s current unit to [targetUnit],
 * returning the new [InputType] with the converted value.
 *
 * Only works for matching measurement types (Weight↔Weight, Height↔Height, etc.).
 * Returns null if the types are incompatible.
 *
 * Usage:
 * ```kotlin
 * val (newType, newValue) = currentType.switchUnit(WeightUnit.LB, currentValue) ?: return
 * ```
 */
fun InputType.switchUnit(targetUnit: RulerUnit, currentValue: Float): Pair<InputType, Float>? {
    return when {
        this is InputType.Weight && targetUnit is WeightUnit -> {
            val converted = this.unit.convertTo(currentValue, targetUnit)
            Pair(InputType.Weight(targetUnit), converted)
        }
        this is InputType.Height && targetUnit is HeightUnit -> {
            val converted = this.unit.convertTo(currentValue, targetUnit)
            Pair(InputType.Height(targetUnit), converted)
        }
        this is InputType.Distance && targetUnit is DistanceUnit -> {
            val converted = this.unit.convertTo(currentValue, targetUnit)
            Pair(InputType.Distance(targetUnit), converted)
        }
        else -> null
    }
}
