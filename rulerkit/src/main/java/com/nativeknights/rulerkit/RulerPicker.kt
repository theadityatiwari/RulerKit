package com.nativeknights.rulerkit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * State holder for [RulerPicker]. Create with [rememberRulerPickerState].
 */
class RulerPickerState(initialConfig: RulerConfig) {
    internal val engine = ScrollEngine(initialConfig)

    /** Current configuration. Changes trigger a recomposition of [RulerPicker]. */
    var config: RulerConfig by mutableStateOf(initialConfig)
        internal set

    internal val animatable = Animatable(engine.currentValue)

    /** Live value updated every animation frame. */
    val currentValue: Float get() = animatable.value

    /** Last settled value — stable after each gesture ends. */
    val snappedValue: Float get() = engine.snappedValue

    /**
     * Programmatically move the ruler to [value]. Must be called from a coroutine.
     *
     * ```kotlin
     * val scope = rememberCoroutineScope()
     * scope.launch { state.setValue(75f) }
     * ```
     */
    suspend fun setValue(value: Float, animate: Boolean = true) {
        engine.setValue(value)
        if (animate) animatable.animateTo(engine.currentValue, tween(300))
        else animatable.snapTo(engine.currentValue)
    }

    /**
     * Switch units at runtime, converting the current value automatically.
     * Must be called from a coroutine.
     *
     * ```kotlin
     * scope.launch { state.setUnit(WeightUnit.LB) }
     * ```
     */
    suspend fun setUnit(newUnit: RulerUnit) {
        val (newInputType, newValue) =
            config.inputType.switchUnit(newUnit, engine.currentValue) ?: return
        config = config.copy(inputType = newInputType)
        engine.updateConfig(config, newValue)
        animatable.snapTo(engine.currentValue)
    }
}

/** Create and remember a [RulerPickerState] for use with [RulerPicker]. */
@Composable
fun rememberRulerPickerState(config: RulerConfig = RulerConfig()): RulerPickerState =
    remember { RulerPickerState(config) }

/**
 * Jetpack Compose ruler picker. Mirrors the full feature set of [RulerPickerView].
 *
 * ```kotlin
 * val state = rememberRulerPickerState(RulerConfig.weight())
 *
 * RulerPicker(
 *     state    = state,
 *     modifier = Modifier.fillMaxWidth().height(160.dp),
 *     onValueChanged = { value, unit -> println("$value $unit") },
 *     onScrollEnd    = { value, unit -> println("Settled: $value $unit") }
 * )
 *
 * // Unit switch from a button:
 * val scope = rememberCoroutineScope()
 * Button(onClick = { scope.launch { state.setUnit(WeightUnit.LB) } }) { Text("lb") }
 * ```
 */
@Composable
fun RulerPicker(
    state: RulerPickerState,
    modifier: Modifier = Modifier,
    onValueChanged: ((value: Float, unit: String) -> Unit)? = null,
    onScrollEnd: ((value: Float, unit: String) -> Unit)? = null,
) {
    val density     = LocalDensity.current
    val haptic      = LocalHapticFeedback.current
    val scope       = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val config      = state.config
    val engine      = state.engine

    // Reading animatable.value during composition makes Compose recompose — and redraw — on
    // every animation frame, giving smooth scrolling without polling.
    val rawValue = state.animatable.value

    // Keep engine in sync after each recompose so tick-math functions stay accurate.
    SideEffect { engine.syncRaw(rawValue) }

    // Update animatable bounds whenever unit changes (new min/max).
    LaunchedEffect(config) {
        state.animatable.updateBounds(engine.min, engine.max)
    }

    // Value-change callbacks and haptic — one persistent collector per state instance.
    LaunchedEffect(state) {
        snapshotFlow { state.animatable.value }
            .collect { value ->
                engine.syncRaw(value)
                onValueChanged?.invoke(value, state.config.inputType.unit.label)
                if (state.config.enableHapticFeedback && engine.shouldTriggerHaptic()) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    val pxPerDp = density.density
    val pxPerSp = density.fontScale * density.density

    Canvas(
        modifier = modifier.pointerInput(config) {
            val velTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch { state.animatable.stop() }
                velTracker.resetTracking()
                velTracker.addPosition(down.uptimeMillis, down.position)

                drag(down.id) { change ->
                    velTracker.addPosition(change.uptimeMillis, change.position)
                    val pps   = config.tickSpacingDp * pxPerDp
                    val dragX = (change.position - change.previousPosition).x
                    val delta = -dragX / pps * config.inputType.unit.defaultStep
                    scope.launch {
                        state.animatable.snapTo(
                            (state.animatable.value + delta).coerceIn(engine.min, engine.max)
                        )
                    }
                    change.consume()
                }

                val velX     = velTracker.calculateVelocity().x
                val pps      = config.tickSpacingDp * pxPerDp
                val valueVel = -velX / pps * config.inputType.unit.defaultStep

                scope.launch {
                    if (abs(velX) > 300f) {
                        state.animatable.animateDecay(
                            initialVelocity = valueVel,
                            animationSpec   = exponentialDecay(frictionMultiplier = 2f)
                        )
                    }
                    engine.syncRaw(state.animatable.value)
                    val snapped = engine.snapToNearest()
                    state.animatable.animateTo(snapped, tween(150))
                    onScrollEnd?.invoke(snapped, state.config.inputType.unit.label)
                }
            }
        }
    ) {
        val w   = size.width
        val h   = size.height
        val cx  = w / 2f
        val pps = config.tickSpacingDp * pxPerDp

        val majorH    = config.majorLineHeightDp    * pxPerDp
        val indSize   = config.indicatorSizeDp      * pxPerDp
        val valTxtSp  = config.valueLabelTextSizeSp.sp
        val labelTxtSp = config.labelTextSizeSp.sp
        val gap       = 8f * pxPerDp
        val dir       = if (config.flipVertically) 1f else -1f

        // Approximate text heights for layout (sp → px, similar to View version)
        val valTxtH   = config.valueLabelTextSizeSp * pxPerSp
        val labelTxtH = config.labelTextSizeSp      * pxPerSp

        // ── Layout (mirrors RulerPickerView.computeLayout) ──────────────────
        val tickBaseY: Float
        val valueLabelY: Float
        val tickLabelY: Float
        val indicatorTipY: Float

        if (!config.flipVertically) {
            val minBase = majorH + gap + valTxtH * 1.3f + gap
            tickBaseY     = maxOf(h * 0.55f, minBase)
            valueLabelY   = tickBaseY - majorH - gap - valTxtH / 2f
            tickLabelY    = tickBaseY + gap
            indicatorTipY = when (config.indicatorPosition) {
                IndicatorPosition.CENTER_BOTTOM -> tickBaseY + gap + indSize
                IndicatorPosition.CENTER_TOP    -> tickBaseY - majorH - gap - indSize
            }
        } else {
            tickBaseY     = minOf(h * 0.45f, h - majorH - gap - valTxtH * 1.3f - gap)
            valueLabelY   = tickBaseY + majorH + gap
            tickLabelY    = tickBaseY - gap - labelTxtH
            indicatorTipY = when (config.indicatorPosition) {
                IndicatorPosition.CENTER_BOTTOM -> tickBaseY - gap - indSize
                IndicatorPosition.CENTER_TOP    -> tickBaseY + majorH + gap + indSize
            }
        }

        // ── Background ──────────────────────────────────────────────────────
        drawRect(Color(config.backgroundColor))

        // ── Ticks ───────────────────────────────────────────────────────────
        val unit     = config.inputType.unit
        val step     = unit.defaultStep
        val vis      = engine.visibleValueRange(cx, pps)
        val startIdx = ceil((vis.start - engine.min) / step).toInt().coerceAtLeast(0)
        val endIdx   = floor((vis.endInclusive - engine.min) / step).toInt()

        for (idx in startIdx..endIdx) {
            val tv      = engine.min + idx * step
            val x       = cx + engine.valueToPixelOffset(tv, pps)
            val isMajor = engine.isMajorTick(tv, unit)
            val isMed   = !isMajor && engine.isMediumTick(tv, unit)

            val lineH = when { isMajor -> config.majorLineHeightDp; isMed -> config.mediumLineHeightDp; else -> config.minorLineHeightDp } * pxPerDp
            val sw    = when { isMajor -> config.majorLineWidthDp;  isMed -> config.mediumLineWidthDp;  else -> config.minorLineWidthDp  } * pxPerDp
            val col   = Color(when { isMajor -> config.majorLineColor; isMed -> config.mediumLineColor; else -> config.minorLineColor })
            val fade  = (1f - (abs(x - cx) / cx).coerceIn(0f, 1f)).coerceAtLeast(40f / 255f)

            drawLine(col.copy(alpha = fade), Offset(x, tickBaseY), Offset(x, tickBaseY + dir * lineH), sw, StrokeCap.Round)

            if (isMajor && config.showLabels) {
                val label   = unit.formatValue(tv)
                val measured = textMeasurer.measure(
                    text  = label,
                    style = TextStyle(
                        color      = Color(config.labelTextColor).copy(alpha = fade),
                        fontSize   = labelTxtSp,
                        fontWeight = FontWeight.Normal,
                    )
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x - measured.size.width / 2f, tickLabelY)
                )
            }
        }

        // ── Value label ─────────────────────────────────────────────────────
        if (config.showValueLabel) {
            val valueText = unit.formatValue(rawValue)
            val unitText  = unit.label

            val valueMeasured = textMeasurer.measure(
                text  = valueText,
                style = TextStyle(
                    color      = Color(config.valueLabelTextColor),
                    fontSize   = valTxtSp,
                    fontWeight = FontWeight.Bold,
                )
            )
            val unitMeasured = textMeasurer.measure(
                text  = unitText,
                style = TextStyle(
                    color    = Color(config.valueLabelTextColor),
                    fontSize = (config.valueLabelTextSizeSp * 0.6f).sp,
                )
            )

            val totalW  = valueMeasured.size.width + 6f * pxPerDp + unitMeasured.size.width
            val startX  = cx - totalW / 2f
            val baselineOffset = valueMeasured.size.height * 0.15f  // align unit baseline roughly

            drawText(valueMeasured, topLeft = Offset(startX, valueLabelY))
            drawText(
                unitMeasured,
                topLeft = Offset(
                    startX + valueMeasured.size.width + 6f * pxPerDp,
                    valueLabelY + baselineOffset + (valueMeasured.size.height - unitMeasured.size.height) / 2f
                )
            )
        }

        // ── Indicator ───────────────────────────────────────────────────────
        val pointsUp = when (config.indicatorPosition) {
            IndicatorPosition.CENTER_BOTTOM -> !config.flipVertically
            IndicatorPosition.CENTER_TOP    ->  config.flipVertically
        }
        val indCol = Color(config.indicatorColor)
        val indSW  = config.indicatorWidthDp * pxPerDp

        when (config.indicatorType) {
            IndicatorType.LINE -> {
                val half = indSize / 2f
                drawLine(indCol, Offset(cx, indicatorTipY - half), Offset(cx, indicatorTipY + half), indSW, StrokeCap.Round)
            }
            IndicatorType.TRIANGLE -> {
                val baseW = indSize * 0.8f
                val baseY = if (pointsUp) indicatorTipY + indSize else indicatorTipY - indSize
                drawPath(Path().apply {
                    moveTo(cx, indicatorTipY)
                    lineTo(cx - baseW / 2f, baseY)
                    lineTo(cx + baseW / 2f, baseY)
                    close()
                }, indCol)
            }
            IndicatorType.ARROW -> {
                val baseW    = indSize * 0.7f
                val baseY    = if (pointsUp) indicatorTipY + indSize else indicatorTipY - indSize
                val stemEndY = if (pointsUp) baseY + indSize * 0.6f else baseY - indSize * 0.6f
                drawPath(Path().apply {
                    moveTo(cx, indicatorTipY)
                    lineTo(cx - baseW / 2f, baseY)
                    lineTo(cx + baseW / 2f, baseY)
                    close()
                }, indCol)
                drawLine(indCol, Offset(cx, baseY), Offset(cx, stemEndY), indSW * 2f, StrokeCap.Round)
            }
            IndicatorType.PIN -> {
                val stemEndY = if (pointsUp) indicatorTipY + indSize * 0.7f else indicatorTipY - indSize * 0.7f
                val circleR  = indSize * 0.4f
                val circleCY = if (pointsUp) stemEndY + circleR else stemEndY - circleR
                drawLine(indCol, Offset(cx, indicatorTipY), Offset(cx, stemEndY), indSW * 1.5f, StrokeCap.Round)
                drawCircle(indCol, circleR, Offset(cx, circleCY))
            }
        }
    }
}
