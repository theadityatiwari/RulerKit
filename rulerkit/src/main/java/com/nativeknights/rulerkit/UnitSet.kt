package com.nativeknights.rulerkit

// KMP note: String.format is JVM-only. When migrating to commonMain, replace with
// an expect/actual ValueFormatter or use kotlin-multiplatform-utils formatting.

/**
 * Common interface for all ruler units.
 * Every unit must declare its display label, default range, step precision,
 * and where major/medium tick marks fall.
 */
interface RulerUnit {
    /** Display label shown on the ruler (e.g. "kg", "cm", "km"). */
    val label: String

    /** Default minimum value for this unit. */
    val defaultMin: Float

    /** Default maximum value for this unit. */
    val defaultMax: Float

    /**
     * Smallest increment per step.
     * e.g. 0.1 for kg (each tick = 0.1 kg), 1.0 for cm (each tick = 1 cm).
     */
    val defaultStep: Float

    /**
     * Draw a MAJOR line every N steps.
     * e.g. for KG with step=0.1, majorLineEvery=50 → major line every 5 kg.
     */
    val majorLineEvery: Int

    /**
     * Draw a MEDIUM line every N steps.
     * e.g. for KG with step=0.1, mediumLineEvery=10 → medium line every 1 kg.
     */
    val mediumLineEvery: Int

    /** Format a value for display above the ruler indicator. */
    fun formatValue(value: Float): String
}

// ─────────────────────────────────────────────
// Weight
// ─────────────────────────────────────────────

enum class WeightUnit : RulerUnit {
    KG, LB;

    override val label get() = when (this) { KG -> "kg";  LB -> "lb" }
    override val defaultMin get() = 0f
    override val defaultMax get() = when (this) { KG -> 250f; LB -> 550f }
    override val defaultStep get() = when (this) { KG -> 0.1f; LB -> 0.5f }
    // KG: major every 50 steps × 0.1 = every 5 kg | medium every 10 steps = every 1 kg
    // LB: major every 20 steps × 0.5 = every 10 lb | medium every 2 steps  = every 1 lb
    override val majorLineEvery get() = when (this) { KG -> 50; LB -> 20 }
    override val mediumLineEvery get() = when (this) { KG -> 10; LB -> 2 }
    override fun formatValue(value: Float) = String.format("%.1f", value)

    fun convertTo(value: Float, target: WeightUnit): Float = when (this) {
        KG -> when (target) { KG -> value;            LB -> value * 2.20462f }
        LB -> when (target) { LB -> value;            KG -> value / 2.20462f }
    }
}

// ─────────────────────────────────────────────
// Height
// ─────────────────────────────────────────────

enum class HeightUnit : RulerUnit {
    CM, INCH;

    override val label get() = when (this) { CM -> "cm"; INCH -> "in" }
    override val defaultMin get() = when (this) { CM -> 50f;  INCH -> 20f }
    override val defaultMax get() = when (this) { CM -> 250f; INCH -> 98f }
    override val defaultStep get() = when (this) { CM -> 1f;  INCH -> 0.5f }
    // CM:   major every 10 steps × 1   = every 10 cm  | medium every 5 steps  = every 5 cm
    // INCH: major every 24 steps × 0.5 = every 12 in  | medium every 2 steps  = every 1 in
    override val majorLineEvery get() = when (this) { CM -> 10; INCH -> 24 }
    override val mediumLineEvery get() = when (this) { CM -> 5; INCH -> 2 }
    override fun formatValue(value: Float): String = when (this) {
        CM   -> String.format("%.0f", value)
        INCH -> {
            val totalInches = value.toInt()
            val feet = totalInches / 12
            val inches = totalInches % 12
            if (feet > 0) "${feet}' ${inches}\"" else "${inches}\""
        }
    }

    fun convertTo(value: Float, target: HeightUnit): Float = when (this) {
        CM   -> when (target) { CM -> value;            INCH -> value / 2.54f }
        INCH -> when (target) { INCH -> value;          CM   -> value * 2.54f }
    }
}

// ─────────────────────────────────────────────
// Distance
// ─────────────────────────────────────────────

enum class DistanceUnit : RulerUnit {
    KM, MILES, METERS;

    override val label get() = when (this) { KM -> "km"; MILES -> "mi"; METERS -> "m" }
    override val defaultMin get() = 0f
    override val defaultMax get() = when (this) { KM -> 1000f; MILES -> 621f; METERS -> 1000f }
    override val defaultStep get() = when (this) { KM -> 0.1f; MILES -> 0.1f; METERS -> 1f }
    // KM/MILES: major every 50 steps × 0.1 = every 5 units | medium every 10 = every 1 unit
    // METERS:   major every 100 steps × 1  = every 100 m    | medium every 10 = every 10 m
    override val majorLineEvery get() = when (this) { KM -> 50; MILES -> 50; METERS -> 100 }
    override val mediumLineEvery get() = when (this) { KM -> 10; MILES -> 10; METERS -> 10 }
    override fun formatValue(value: Float): String = when (this) {
        METERS -> String.format("%.0f", value)
        else   -> String.format("%.1f", value)
    }

    fun convertTo(value: Float, target: DistanceUnit): Float = when (this) {
        KM     -> when (target) {
            KM     -> value
            MILES  -> value * 0.621371f
            METERS -> value * 1000f
        }
        MILES  -> when (target) {
            MILES  -> value
            KM     -> value / 0.621371f
            METERS -> value / 0.621371f * 1000f
        }
        METERS -> when (target) {
            METERS -> value
            KM     -> value / 1000f
            MILES  -> value / 1000f * 0.621371f
        }
    }
}
