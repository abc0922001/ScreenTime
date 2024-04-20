package de.markusfisch.android.screentime.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.graphics.PI2
import de.markusfisch.android.screentime.graphics.TAU
import de.markusfisch.android.screentime.graphics.loadColor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RAD_PER_HOUR = Math.PI / 12f

class UsageGraphView : View {
	private val viewRect = Rect()
	private val usageGraphRect = Rect()
	private val dp = resources.displayMetrics.density
	private val markerSizeRadius = 6f * dp
	private val markerPos = PointF()
	private val markerColor = context.loadColor(R.color.accent)
	private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
	}
	private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = 2f * dp
		color = 0x1a000000 or (context.loadColor(R.color.text) and 0xffffff)
	}
	private val touchRadius = 24f * dp

	private var usageGraph: Bitmap? = null
	private var markerRadius = 0f
	private var markerGrabbed = false

	var onDayChangeChanged: (() -> Unit)? = null
	var onDayChangeChange: ((Int) -> Unit)? = null
	var onStopTrackingTouch: (() -> Unit)? = null

	constructor(context: Context, attrs: AttributeSet, defStyle: Int) :
			super(context, attrs, defStyle)

	constructor(context: Context, attrs: AttributeSet) :
			this(context, attrs, 0)

	fun setGraphBitmap(bitmap: Bitmap) {
		usageGraph = bitmap
		usageGraphRect.set(0, 0, bitmap.width, bitmap.height)
		markerRadius = min(usageGraphRect.centerX(), usageGraphRect.centerY()) * .95f
		markerPos.setFromHour(prefs.hourOfDayChange)
		invalidate()
	}

	override fun onLayout(
		changed: Boolean,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int
	) {
		super.onLayout(changed, left, top, right, bottom)
		viewRect.set(left, top, right, bottom)
		val padding = (16f * dp).roundToInt()
		viewRect.inset(padding, padding)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(0)
		usageGraph?.let {
			canvas.drawBitmap(it, usageGraphRect, viewRect, null)
		}
		if (markerGrabbed) {
			canvas.drawCircle(
				viewRect.centerX().toFloat(),
				viewRect.centerY().toFloat(),
				markerRadius,
				rangePaint
			)
			canvas.drawCircle(
				markerPos.x,
				markerPos.y,
				markerSizeRadius * 3f,
				markerPaint.apply {
					color = 0x1a000000 or (markerColor and 0xffffff)
				}
			)
		}
		canvas.drawCircle(
			markerPos.x,
			markerPos.y,
			markerSizeRadius * if (markerGrabbed) 1.5f else 1f,
			markerPaint.apply {
				color = markerColor
			}
		)
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent?): Boolean {
		event ?: return super.onTouchEvent(null)
		return when (event.action and MotionEvent.ACTION_MASK) {
			MotionEvent.ACTION_DOWN -> {
				markerGrabbed = abs(
					hypot(
						event.x - viewRect.centerX(),
						event.y - viewRect.centerY()
					) - markerRadius
				) < touchRadius
				invalidate()
				true
			}

			MotionEvent.ACTION_MOVE -> {
				if (markerGrabbed) {
					val angle = angleFromPos(event.x, event.y)
					markerPos.setFromAngle(angle)
					onDayChangeChange?.invoke(hourFromAngle(angle))
					invalidate()
				}
				true
			}

			MotionEvent.ACTION_UP -> {
				if (ungrab()) {
					prefs.hourOfDayChange =
						markerPos.setHourFromPos(event.x, event.y)
					onDayChangeChanged?.invoke()
					performClick()
				}
				true
			}

			MotionEvent.ACTION_CANCEL -> {
				ungrab()
				true
			}

			else -> super.onTouchEvent(event)
		}
	}

	private fun ungrab() = if (markerGrabbed) {
		markerGrabbed = false
		onStopTrackingTouch?.invoke()
		invalidate()
		true
	} else {
		false
	}

	private fun PointF.setHourFromPos(x: Float, y: Float): Int {
		val hour = hourFromAngle(angleFromPos(x, y))
		setFromHour(hour)
		return hour
	}

	private fun hourFromAngle(angle: Float): Int {
		val positiveAngle = (angle + TAU) % TAU
		return ((positiveAngle / RAD_PER_HOUR).roundToInt() + 6) % 24
	}

	private fun PointF.setFromHour(hours: Int) {
		setFromAngle((RAD_PER_HOUR * hours - PI2).toFloat())
	}

	private fun PointF.setFromAngle(angle: Float) {
		x = viewRect.centerX() + cos(angle) * markerRadius
		y = viewRect.centerY() + sin(angle) * markerRadius
	}

	private fun angleFromPos(x: Float, y: Float): Float = atan2(
		y - viewRect.centerY(),
		x - viewRect.centerX()
	)
}
