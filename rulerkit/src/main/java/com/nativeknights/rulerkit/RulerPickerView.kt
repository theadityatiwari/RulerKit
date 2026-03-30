package com.nativeknights.rulerkit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class RulerPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /** Full configuration. Setting this redraws the view entirely. */
    var config: RulerConfig = readAttrs(attrs)
        set(value) {
            field = value
            engine.updateConfig(value)
            applyConfig()
            requestLayout()
            invalidate()
        }

    /** Called on every step boundary crossing while scrolling. */
    var onValueChanged: ((value: Float, unit: String) -> Unit)? = null

    /** Called once when the user first touches the ruler. */
    var onScrollStart: (() -> Unit)? = null

    /** Called when the ruler settles on a value after drag or fling. */
    var onScrollEnd: ((value: Float, unit: String) -> Unit)? = null

    /** Set a value programmatically. */
    fun setValue(value: Float, animate: Boolean = true) {
        if (animate) {
            val targetX = engine.run {
                setValue(value)
                currentScrollerX(pixelsPerStep)
            }
            val startX = engine.currentScrollerX(pixelsPerStep)
            engine.setValue(value)  // reset so currentScrollerX reflects current
            scroller.startScroll(startX, 0, targetX - startX, 0, 300)
        } else {
            engine.setValue(value)
        }
        invalidate()
    }

    /**
     * Switch units at runtime, converting the current value automatically.
     * e.g. `setUnit(WeightUnit.LB)` while displaying 70 kg → shows 154.3 lb.
     * Silently ignored if [newUnit] is incompatible with the current [InputType].
     */
    fun setUnit(newUnit: RulerUnit) {
        val (newInputType, newValue) =
            config.inputType.switchUnit(newUnit, engine.currentValue) ?: return
        config = config.copy(inputType = newInputType)
        engine.updateConfig(config, newValue)
        invalidate()
    }

    /** Current selected value (snapped to the nearest step). */
    fun getValue(): Float = engine.snappedValue

    // ─────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────

    private val engine: ScrollEngine = ScrollEngine(config)

    // Pixel layout — computed in onSizeChanged
    private var pixelsPerStep = 0f
    private var centerX       = 0f
    private var tickBaseY     = 0f   // Y where each tick line starts (its foot)
    private var tickDirection = -1f  // -1 = up, +1 = down
    private var valueLabelY   = 0f   // baseline Y for the value + unit text
    private var tickLabelY    = 0f   // baseline Y for tick number labels
    private var indicatorTipY = 0f   // Y of the sharp tip of the indicator

    // Paints
    private val majorPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mediumPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val minorPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint        = Paint()

    // Scroll / touch
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchX   = 0f
    private var isDragging   = false
    private var scrollNotified = false  // guards onScrollStart firing once

    // Haptic
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Reusable path for triangle / arrow / pin drawing
    private val indicatorPath = Path()

    // ─────────────────────────────────────────────
    // Attribute reading
    // ─────────────────────────────────────────────

    private fun readAttrs(attrs: AttributeSet?): RulerConfig {
        val defaults = RulerConfig()
        attrs ?: return defaults
        val ta = context.obtainStyledAttributes(attrs, R.styleable.RulerPickerView)
        val d  = resources.displayMetrics.density
        val sp = resources.displayMetrics.scaledDensity
        try {
            val inputType = resolveInputType(ta, defaults)
            return RulerConfig(
                inputType             = inputType,
                minValue              = if (ta.hasValue(R.styleable.RulerPickerView_rulerMinValue))
                                            ta.getFloat(R.styleable.RulerPickerView_rulerMinValue, defaults.effectiveMin)
                                        else null,
                maxValue              = if (ta.hasValue(R.styleable.RulerPickerView_rulerMaxValue))
                                            ta.getFloat(R.styleable.RulerPickerView_rulerMaxValue, defaults.effectiveMax)
                                        else null,
                initialValue          = if (ta.hasValue(R.styleable.RulerPickerView_rulerInitialValue))
                                            ta.getFloat(R.styleable.RulerPickerView_rulerInitialValue, defaults.effectiveInitialValue)
                                        else null,
                indicatorType         = IndicatorType.values()[
                                            ta.getInt(R.styleable.RulerPickerView_indicatorType, defaults.indicatorType.ordinal)],
                indicatorPosition     = IndicatorPosition.values()[
                                            ta.getInt(R.styleable.RulerPickerView_indicatorPosition, defaults.indicatorPosition.ordinal)],
                indicatorColor        = ta.getColor(R.styleable.RulerPickerView_indicatorColor, defaults.indicatorColor),
                indicatorSizeDp       = ta.getDimension(R.styleable.RulerPickerView_indicatorSize, defaults.indicatorSizeDp * d) / d,
                indicatorWidthDp      = ta.getDimension(R.styleable.RulerPickerView_indicatorWidth, defaults.indicatorWidthDp * d) / d,
                majorLineHeightDp     = ta.getDimension(R.styleable.RulerPickerView_majorLineHeight, defaults.majorLineHeightDp * d) / d,
                majorLineWidthDp      = ta.getDimension(R.styleable.RulerPickerView_majorLineWidth, defaults.majorLineWidthDp * d) / d,
                majorLineColor        = ta.getColor(R.styleable.RulerPickerView_majorLineColor, defaults.majorLineColor),
                mediumLineHeightDp    = ta.getDimension(R.styleable.RulerPickerView_mediumLineHeight, defaults.mediumLineHeightDp * d) / d,
                mediumLineWidthDp     = ta.getDimension(R.styleable.RulerPickerView_mediumLineWidth, defaults.mediumLineWidthDp * d) / d,
                mediumLineColor       = ta.getColor(R.styleable.RulerPickerView_mediumLineColor, defaults.mediumLineColor),
                minorLineHeightDp     = ta.getDimension(R.styleable.RulerPickerView_minorLineHeight, defaults.minorLineHeightDp * d) / d,
                minorLineWidthDp      = ta.getDimension(R.styleable.RulerPickerView_minorLineWidth, defaults.minorLineWidthDp * d) / d,
                minorLineColor        = ta.getColor(R.styleable.RulerPickerView_minorLineColor, defaults.minorLineColor),
                showLabels            = ta.getBoolean(R.styleable.RulerPickerView_showLabels, defaults.showLabels),
                labelTextSizeSp       = ta.getDimension(R.styleable.RulerPickerView_labelTextSize, defaults.labelTextSizeSp * sp) / sp,
                labelTextColor        = ta.getColor(R.styleable.RulerPickerView_labelTextColor, defaults.labelTextColor),
                showValueLabel        = ta.getBoolean(R.styleable.RulerPickerView_showValueLabel, defaults.showValueLabel),
                valueLabelTextSizeSp  = ta.getDimension(R.styleable.RulerPickerView_valueLabelTextSize, defaults.valueLabelTextSizeSp * sp) / sp,
                valueLabelTextColor   = ta.getColor(R.styleable.RulerPickerView_valueLabelTextColor, defaults.valueLabelTextColor),
                backgroundColor       = ta.getColor(R.styleable.RulerPickerView_rulerBackgroundColor, defaults.backgroundColor),
                tickSpacingDp         = ta.getDimension(R.styleable.RulerPickerView_tickSpacing, defaults.tickSpacingDp * d) / d,
                flipVertically        = ta.getBoolean(R.styleable.RulerPickerView_flipVertically, defaults.flipVertically),
                enableHapticFeedback  = ta.getBoolean(R.styleable.RulerPickerView_enableHapticFeedback, defaults.enableHapticFeedback),
            )
        } finally {
            ta.recycle()
        }
    }

    private fun resolveInputType(ta: android.content.res.TypedArray, defaults: RulerConfig): InputType {
        val typeOrdinal = ta.getInt(R.styleable.RulerPickerView_rulerInputType, -1)
        val unitOrdinal = ta.getInt(R.styleable.RulerPickerView_rulerUnit, -1)
        return when (typeOrdinal) {
            0 -> InputType.Weight(
                when (unitOrdinal) { 1 -> WeightUnit.LB; else -> WeightUnit.KG }
            )
            1 -> InputType.Height(
                when (unitOrdinal) { 3 -> HeightUnit.INCH; else -> HeightUnit.CM }
            )
            2 -> InputType.Distance(
                when (unitOrdinal) { 5 -> DistanceUnit.MILES; 6 -> DistanceUnit.METERS; else -> DistanceUnit.KM }
            )
            3 -> InputType.Custom(
                min         = ta.getFloat(R.styleable.RulerPickerView_rulerMinValue, 0f),
                max         = ta.getFloat(R.styleable.RulerPickerView_rulerMaxValue, 100f),
                step        = ta.getFloat(R.styleable.RulerPickerView_rulerCustomStep, 1f),
                unitLabel   = ta.getString(R.styleable.RulerPickerView_rulerCustomUnitLabel) ?: "",
                majorEvery  = ta.getInt(R.styleable.RulerPickerView_rulerCustomMajorEvery, 10),
                mediumEvery = ta.getInt(R.styleable.RulerPickerView_rulerCustomMediumEvery, 5),
            )
            else -> defaults.inputType
        }
    }

    // ─────────────────────────────────────────────
    // Paint setup
    // ─────────────────────────────────────────────

    private fun applyConfig() {
        val d  = resources.displayMetrics.density
        val sp = resources.displayMetrics.scaledDensity

        bgPaint.color = config.backgroundColor

        majorPaint.apply {
            color       = config.majorLineColor
            strokeWidth = config.majorLineWidthDp * d
            style       = Paint.Style.STROKE
            strokeCap   = Paint.Cap.ROUND
        }
        mediumPaint.apply {
            color       = config.mediumLineColor
            strokeWidth = config.mediumLineWidthDp * d
            style       = Paint.Style.STROKE
            strokeCap   = Paint.Cap.ROUND
        }
        minorPaint.apply {
            color       = config.minorLineColor
            strokeWidth = config.minorLineWidthDp * d
            style       = Paint.Style.STROKE
            strokeCap   = Paint.Cap.ROUND
        }
        tickLabelPaint.apply {
            color     = config.labelTextColor
            textSize  = config.labelTextSizeSp * sp
            textAlign = Paint.Align.CENTER
        }
        valuePaint.apply {
            color     = config.valueLabelTextColor
            textSize  = config.valueLabelTextSizeSp * sp
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        indicatorPaint.apply {
            color       = config.indicatorColor
            style       = if (config.indicatorType == IndicatorType.LINE)
                              Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = config.indicatorWidthDp * d
            strokeCap   = Paint.Cap.ROUND
        }
    }

    // ─────────────────────────────────────────────
    // Layout
    // ─────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        pixelsPerStep = config.tickSpacingDp * resources.displayMetrics.density
        applyConfig()
        computeLayout(w, h)
        // Position engine at initial / current value
        post { engine.setValue(engine.snappedValue) }
    }

    private fun computeLayout(w: Int, h: Int) {
        val d          = resources.displayMetrics.density
        val majorH     = config.majorLineHeightDp * d
        val indSize    = config.indicatorSizeDp * d
        val valTxtH    = valuePaint.textSize
        val labelTxtH  = tickLabelPaint.textSize
        val gap        = 8f * d

        tickDirection = if (config.flipVertically) 1f else -1f

        if (!config.flipVertically) {
            // Compute tickBaseY so the value label (baseline - ~ascent) never clips the top edge.
            // Text ascent ≈ textSize * 0.8; add topPad so it breathes.
            val topPad       = 8f * d
            val minTickBaseY = majorH + gap + valTxtH * 1.3f + topPad
            tickBaseY        = maxOf(h * 0.55f, minTickBaseY)

            // Ticks go UP  │  value label above  │  indicator below
            valueLabelY  = tickBaseY - majorH - gap - valTxtH / 2f
            tickLabelY   = tickBaseY + labelTxtH + gap
            indicatorTipY = when (config.indicatorPosition) {
                IndicatorPosition.CENTER_BOTTOM -> tickBaseY + gap + indSize
                IndicatorPosition.CENTER_TOP    -> tickBaseY - majorH - gap - indSize
            }
        } else {
            // Ticks go DOWN │ indicator above │ value label below
            // Clamp so ticks don't start above the top edge.
            val topPad    = 8f * d
            tickBaseY     = minOf(h * 0.45f, h - majorH - gap - valTxtH * 1.3f - topPad)
            valueLabelY   = tickBaseY + majorH + gap + valTxtH
            tickLabelY   = tickBaseY - gap
            indicatorTipY = when (config.indicatorPosition) {
                IndicatorPosition.CENTER_BOTTOM -> tickBaseY - gap - indSize
                IndicatorPosition.CENTER_TOP    -> tickBaseY + majorH + gap + indSize
            }
        }
    }

    // ─────────────────────────────────────────────
    // Drawing
    // ─────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawTicks(canvas)
        if (config.showValueLabel) drawValueLabel(canvas)
        drawIndicator(canvas)
    }

    private fun drawTicks(canvas: Canvas) {
        val unit      = config.inputType.unit
        val step      = unit.defaultStep
        val d         = resources.displayMetrics.density
        val visRange  = engine.visibleValueRange(centerX, pixelsPerStep)

        val startIdx = ceil((visRange.start - engine.min) / step).toInt().coerceAtLeast(0)
        val endIdx   = floor((visRange.endInclusive - engine.min) / step).toInt()
        if (startIdx > endIdx) return

        for (idx in startIdx..endIdx) {
            val tickValue = engine.min + idx * step
            val x         = centerX + engine.valueToPixelOffset(tickValue, pixelsPerStep)

            val isMajor  = engine.isMajorTick(tickValue, unit)
            val isMedium = engine.isMediumTick(tickValue, unit)

            val lineHeightPx = when {
                isMajor  -> config.majorLineHeightDp * d
                isMedium -> config.mediumLineHeightDp * d
                else     -> config.minorLineHeightDp * d
            }
            val paint = when {
                isMajor  -> majorPaint
                isMedium -> mediumPaint
                else     -> minorPaint
            }

            // Alpha fade toward edges
            val dist  = abs(x - centerX)
            val alpha = (255 * (1f - (dist / centerX).coerceIn(0f, 1f))).toInt().coerceAtLeast(40)
            paint.alpha = alpha

            val startY = tickBaseY
            val endY   = tickBaseY + tickDirection * lineHeightPx
            canvas.drawLine(x, startY, x, endY, paint)

            // Tick labels on major ticks
            if (isMajor && config.showLabels) {
                tickLabelPaint.alpha = alpha
                canvas.drawText(unit.formatValue(tickValue), x, tickLabelY, tickLabelPaint)
            }
        }
    }

    private fun drawValueLabel(canvas: Canvas) {
        val value    = engine.currentValue
        val unit     = config.inputType.unit
        val text     = unit.formatValue(value)
        val unitText = unit.label
        val spacing  = valuePaint.measureText(text) / 2f + 8f * resources.displayMetrics.density

        canvas.drawText(text, centerX - spacing / 4f, valueLabelY, valuePaint)

        // Unit label beside value in a slightly smaller paint
        val unitPaint = Paint(valuePaint).apply {
            textSize  = valuePaint.textSize * 0.6f
            isFakeBoldText = false
        }
        canvas.drawText(unitText, centerX + spacing, valueLabelY, unitPaint)
    }

    private fun drawIndicator(canvas: Canvas) {
        val d = resources.displayMetrics.density
        // Flip indicator direction based on where it sits relative to ticks
        val pointsUp = when (config.indicatorPosition) {
            IndicatorPosition.CENTER_BOTTOM -> !config.flipVertically
            IndicatorPosition.CENTER_TOP    ->  config.flipVertically
        }
        when (config.indicatorType) {
            IndicatorType.LINE     -> drawLineIndicator(canvas, d)
            IndicatorType.TRIANGLE -> drawTriangleIndicator(canvas, d, pointsUp)
            IndicatorType.ARROW    -> drawArrowIndicator(canvas, d, pointsUp)
            IndicatorType.PIN      -> drawPinIndicator(canvas, d, pointsUp)
        }
    }

    private fun drawLineIndicator(canvas: Canvas, d: Float) {
        val halfH = config.indicatorSizeDp * d / 2f
        canvas.drawLine(centerX, indicatorTipY - halfH, centerX, indicatorTipY + halfH, indicatorPaint)
    }

    private fun drawTriangleIndicator(canvas: Canvas, d: Float, pointsUp: Boolean) {
        val size    = config.indicatorSizeDp * d
        val baseW   = size * 0.8f
        val tipY    = indicatorTipY
        val baseY   = if (pointsUp) tipY + size else tipY - size

        indicatorPath.reset()
        indicatorPath.moveTo(centerX, tipY)
        indicatorPath.lineTo(centerX - baseW / 2f, baseY)
        indicatorPath.lineTo(centerX + baseW / 2f, baseY)
        indicatorPath.close()
        canvas.drawPath(indicatorPath, indicatorPaint)
    }

    private fun drawArrowIndicator(canvas: Canvas, d: Float, pointsUp: Boolean) {
        val size   = config.indicatorSizeDp * d
        val baseW  = size * 0.7f
        val stemH  = size * 0.6f
        val tipY   = indicatorTipY
        val baseY  = if (pointsUp) tipY + size else tipY - size
        val stemEndY = if (pointsUp) baseY + stemH else baseY - stemH

        // Triangle head
        indicatorPath.reset()
        indicatorPath.moveTo(centerX, tipY)
        indicatorPath.lineTo(centerX - baseW / 2f, baseY)
        indicatorPath.lineTo(centerX + baseW / 2f, baseY)
        indicatorPath.close()
        canvas.drawPath(indicatorPath, indicatorPaint)

        // Stem
        val stemPaint = Paint(indicatorPaint).apply {
            style       = Paint.Style.STROKE
            strokeWidth = config.indicatorWidthDp * d * 2f
        }
        canvas.drawLine(centerX, baseY, centerX, stemEndY, stemPaint)
    }

    private fun drawPinIndicator(canvas: Canvas, d: Float, pointsUp: Boolean) {
        val size     = config.indicatorSizeDp * d
        val stemH    = size * 0.7f
        val circleR  = size * 0.4f
        val tipY     = indicatorTipY
        val stemEndY = if (pointsUp) tipY + stemH else tipY - stemH
        val circleCY = if (pointsUp) stemEndY + circleR else stemEndY - circleR

        // Stem
        val stemPaint = Paint(indicatorPaint).apply {
            style       = Paint.Style.STROKE
            strokeWidth = config.indicatorWidthDp * d * 1.5f
        }
        canvas.drawLine(centerX, tipY, centerX, stemEndY, stemPaint)

        // Circle head
        canvas.drawCircle(centerX, circleCY, circleR, indicatorPaint)
    }

    // ─────────────────────────────────────────────
    // Touch handling
    // ─────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) scroller.abortAnimation()
                lastTouchX = event.x
                isDragging = true
                // Prevent parent ScrollView from intercepting horizontal drags.
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!scrollNotified) {
                    scrollNotified = true
                    onScrollStart?.invoke()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return true
                val deltaX     = event.x - lastTouchX
                lastTouchX     = event.x
                val deltaValue = -deltaX / pixelsPerStep * config.inputType.unit.defaultStep
                engine.applyDrag(deltaValue)
                triggerHapticIfNeeded()
                notifyValueChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return true
                isDragging = false
                // Re-allow parent to intercept once the ruler gesture is done.
                parent?.requestDisallowInterceptTouchEvent(false)

                velocityTracker?.computeCurrentVelocity(1000)
                val velX = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                if (abs(velX) > 300f) {
                    startFling(velX)
                } else {
                    snapAndSettle()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startFling(velocityX: Float) {
        val (minPx, maxPx) = engine.scrollBoundsPixels(pixelsPerStep)
        scroller.fling(
            engine.currentScrollerX(pixelsPerStep), 0,
            -velocityX.toInt(), 0,
            minPx, maxPx, 0, 0
        )
        invalidate()
    }

    private fun snapAndSettle() {
        val snapped   = engine.snapToNearest()
        val targetX   = (snapped / config.inputType.unit.defaultStep * pixelsPerStep).toInt()
        val currentX  = engine.currentScrollerX(pixelsPerStep)
        scroller.startScroll(currentX, 0, targetX - currentX, 0, 200)
        invalidate()
        scrollNotified = false
        onScrollEnd?.invoke(snapped, config.inputType.unit.label)
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return
        engine.applyScrollerPosition(scroller.currX.toFloat(), pixelsPerStep)
        triggerHapticIfNeeded()
        notifyValueChanged()
        if (scroller.isFinished) {
            val snapped = engine.snapToNearest()
            scrollNotified = false
            onScrollEnd?.invoke(snapped, config.inputType.unit.label)
        }
        invalidate()
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private fun notifyValueChanged() {
        onValueChanged?.invoke(engine.currentValue, config.inputType.unit.label)
    }

    private fun triggerHapticIfNeeded() {
        if (!config.enableHapticFeedback) return
        if (!engine.shouldTriggerHaptic()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12)
        }
    }
}
