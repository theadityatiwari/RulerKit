package com.nativeknights.rulerkit

// KMP note: zero Android imports — move to commonMain as-is in Phase 4.
// This class owns all value/step math. RulerPickerView owns all pixel math and
// animation (OverScroller). The only bridge between them is pixelsPerStep, which
// the View computes once (tickSpacingDp * density) and passes in as needed.

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manages scroll state and value math for [RulerPickerView].
 *
 * All internal state is in **value space** (e.g. 54.5 for 54.5 kg).
 * Pixel ↔ value conversion is the View's responsibility.
 *
 * Typical View usage:
 * ```
 * // On drag:
 * val deltaValue = -deltaX / pixelsPerStep * step
 * engine.applyDrag(deltaValue)
 *
 * // On fling end / ACTION_UP:
 * val snapped = engine.snapToNearest()
 *
 * // For drawing each tick:
 * val xOffset = engine.valueToPixelOffset(tickValue, pixelsPerStep)
 * ```
 */
class ScrollEngine(config: RulerConfig) {

    // ─────────────────────────────────────────────
    // Config-derived state (updated on unit switch)
    // ─────────────────────────────────────────────

    internal var step: Float = config.inputType.unit.defaultStep
        private set

    /** Effective minimum value (resolved from config). */
    var min: Float = config.effectiveMin
        private set

    /** Effective maximum value (resolved from config). */
    var max: Float = config.effectiveMax
        private set

    // ─────────────────────────────────────────────
    // Scroll state
    // ─────────────────────────────────────────────

    /**
     * Raw scroll position in value space. Moves freely during drag and fling.
     * Always kept within [min]..[max].
     */
    private var rawValue: Float = config.effectiveInitialValue.coerceIn(
        config.effectiveMin, config.effectiveMax
    )

    /**
     * The most recent snapped value. Updated on every call to [snapToNearest].
     * This is what the view should expose to [RulerPickerView.onValueChanged].
     */
    var snappedValue: Float = rawValue
        private set

    /**
     * Live value during scroll (rawValue clamped to bounds).
     * Read this for drawing — it reflects the ruler's current center position.
     */
    val currentValue: Float
        get() = rawValue.coerceIn(min, max)

    /** True when the ruler is at its minimum position (for edge feedback). */
    val isAtMin: Boolean get() = rawValue <= min

    /** True when the ruler is at its maximum position (for edge feedback). */
    val isAtMax: Boolean get() = rawValue >= max

    // ─────────────────────────────────────────────
    // Drag
    // ─────────────────────────────────────────────

    /**
     * Apply a drag delta in value units.
     *
     * The View converts a pixel delta to a value delta before calling this:
     * ```
     * val deltaValue = -deltaX / pixelsPerStep * step
     * engine.applyDrag(deltaValue)
     * ```
     *
     * @param deltaValue Positive = scroll right (value increases), negative = scroll left.
     */
    fun applyDrag(deltaValue: Float) {
        rawValue = (rawValue + deltaValue).coerceIn(min, max)
    }

    // ─────────────────────────────────────────────
    // Fling (OverScroller bridge)
    // ─────────────────────────────────────────────

    /**
     * Converts a pixel-space fling velocity (from [VelocityTracker]) to a value-space
     * velocity, so the View can feed it to [OverScroller.fling] after converting back.
     *
     * Flow:
     * 1. VelocityTracker gives velocityX in px/s
     * 2. View calls: `val valueVel = engine.pixelVelocityToValue(velocityX, pixelsPerStep)`
     * 3. View converts back: `val pixelVel = (valueVel / step * pixelsPerStep).toInt()`
     * 4. View sets OverScroller bounds via [scrollBoundsPixels]
     */
    fun pixelVelocityToValue(pixelVelocity: Float, pixelsPerStep: Float): Float {
        if (pixelsPerStep == 0f) return 0f
        return pixelVelocity / pixelsPerStep * step
    }

    /**
     * Returns the [min,max] bounds as pixel offsets for [OverScroller.fling].
     *
     * @param pixelsPerStep Pixels per one step increment (tickSpacingDp * density).
     * @return Pair(minScrollPx, maxScrollPx) to pass to OverScroller.
     */
    fun scrollBoundsPixels(pixelsPerStep: Float): Pair<Int, Int> {
        val minPx = (min / step * pixelsPerStep).toInt()
        val maxPx = (max / step * pixelsPerStep).toInt()
        return minPx to maxPx
    }

    /**
     * Apply a scroll position produced by [OverScroller.currX] back to value space.
     *
     * ```
     * if (scroller.computeScrollOffset()) {
     *     engine.applyScrollerPosition(scroller.currX.toFloat(), pixelsPerStep)
     *     invalidate()
     * }
     * ```
     */
    fun applyScrollerPosition(scrollerCurrentX: Float, pixelsPerStep: Float) {
        if (pixelsPerStep == 0f) return
        rawValue = (scrollerCurrentX / pixelsPerStep * step).coerceIn(min, max)
    }

    /**
     * Returns the OverScroller start position for the current value.
     * Used when starting a fling: `scroller.fling(engine.currentScrollerX(pps), ...)`.
     */
    fun currentScrollerX(pixelsPerStep: Float): Int {
        return (rawValue / step * pixelsPerStep).toInt()
    }

    // ─────────────────────────────────────────────
    // Snap
    // ─────────────────────────────────────────────

    /**
     * Snap [rawValue] to the nearest valid step increment.
     *
     * Call this on ACTION_UP (when fling velocity is too low) and
     * when [OverScroller] finishes its animation.
     *
     * @return The snapped value. Also stored in [snappedValue].
     */
    fun snapToNearest(): Float {
        val snapped = nearestStep(rawValue)
        rawValue = snapped
        snappedValue = snapped
        return snapped
    }

    /**
     * Compute the nearest valid step to [value] without updating state.
     * Useful for previewing the snap target.
     */
    fun nearestStep(value: Float): Float {
        if (step == 0f) return value.coerceIn(min, max)
        val stepsFromMin = ((value - min) / step).roundToInt()
        return (min + stepsFromMin * step).coerceIn(min, max)
    }

    // ─────────────────────────────────────────────
    // Programmatic set
    // ─────────────────────────────────────────────

    /**
     * Directly set the current value (programmatic, e.g. `rulerPicker.setValue(70f)`).
     * Snaps to the nearest valid step automatically.
     *
     * @param value The desired value. Clamped to [min]..[max].
     */
    fun setValue(value: Float) {
        val snapped = nearestStep(value.coerceIn(min, max))
        rawValue = snapped
        snappedValue = snapped
    }

    // ─────────────────────────────────────────────
    // Drawing helpers
    // ─────────────────────────────────────────────

    /**
     * Pixel offset of [value] from the center of the ruler view.
     *
     * - Positive → value is to the right of center (draw right of indicator)
     * - Negative → value is to the left of center (draw left of indicator)
     *
     * @param value     The tick value to position.
     * @param pixelsPerStep Pixels per one step increment (tickSpacingDp * density).
     */
    fun valueToPixelOffset(value: Float, pixelsPerStep: Float): Float {
        if (step == 0f) return 0f
        return (value - currentValue) / step * pixelsPerStep
    }

    /**
     * Returns the range of values currently visible in the ruler, given the
     * view's half-width. Used to cull which ticks to draw.
     *
     * @param halfWidthPx   Half of the view's width in pixels.
     * @param pixelsPerStep Pixels per one step increment.
     * @return The range [leftEdgeValue .. rightEdgeValue], clamped to [min]..[max].
     */
    fun visibleValueRange(halfWidthPx: Float, pixelsPerStep: Float): ClosedFloatingPointRange<Float> {
        if (pixelsPerStep == 0f) return min..max
        val halfValueRange = halfWidthPx / pixelsPerStep * step
        val left  = (currentValue - halfValueRange).coerceAtLeast(min)
        val right = (currentValue + halfValueRange).coerceAtMost(max)
        return left..right
    }

    /**
     * True if [value] is a major tick (falls on [RulerUnit.majorLineEvery] steps from min).
     */
    fun isMajorTick(value: Float, unit: RulerUnit): Boolean {
        if (step == 0f) return false
        val stepIndex = ((value - min) / step).roundToInt()
        return stepIndex % unit.majorLineEvery == 0
    }

    /**
     * True if [value] is a medium tick (falls on [RulerUnit.mediumLineEvery] steps from min),
     * but is NOT a major tick.
     */
    fun isMediumTick(value: Float, unit: RulerUnit): Boolean {
        if (step == 0f) return false
        val stepIndex = ((value - min) / step).roundToInt()
        return stepIndex % unit.mediumLineEvery == 0 &&
               stepIndex % unit.majorLineEvery  != 0
    }

    // ─────────────────────────────────────────────
    // Config update (unit switch)
    // ─────────────────────────────────────────────

    /**
     * Update the engine when the unit or config changes at runtime.
     *
     * @param config          The new [RulerConfig] (with updated [InputType] / [RulerUnit]).
     * @param convertedValue  The current value already converted to the new unit.
     *                        If null, the existing value is clamped to the new range.
     */
    fun updateConfig(config: RulerConfig, convertedValue: Float? = null) {
        step = config.inputType.unit.defaultStep
        min  = config.effectiveMin
        max  = config.effectiveMax
        val newValue = convertedValue ?: currentValue.coerceIn(min, max)
        setValue(newValue)
    }

    // ─────────────────────────────────────────────
    // Compose bridge
    // ─────────────────────────────────────────────

    /**
     * Sync the internal raw value to match an external animated position (used by [RulerPicker]).
     * Does NOT snap — just clamps and stores. Call this from a SideEffect or draw block.
     */
    internal fun syncRaw(value: Float) {
        rawValue = value.coerceIn(min, max)
    }

    // ─────────────────────────────────────────────
    // Haptic threshold helper
    // ─────────────────────────────────────────────

    private var lastHapticValue: Float = snappedValue

    /**
     * Returns true if the value has crossed a step boundary since the last haptic.
     * The View calls this every time it draws to decide whether to vibrate.
     */
    fun shouldTriggerHaptic(): Boolean {
        val crossed = abs(currentValue - lastHapticValue) >= step - (step * 0.01f)
        if (crossed) lastHapticValue = nearestStep(currentValue)
        return crossed
    }
}
