package de.markusfisch.android.screentime.activity

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import android.widget.SeekBar
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.data.drawUsageChart
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class MainActivity : Activity(), CoroutineScope {
	override val coroutineContext: CoroutineContext
		get() = Dispatchers.IO + job

	private val job = SupervisorJob()
	private val updateUsageRunnable = Runnable {
		scheduleUsageUpdate()
		update(dayBar.progress)
	}
	private var paused = true

	private lateinit var usageView: ImageView
	private lateinit var dayBar: SeekBar

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_main)
		usageView = findViewById(R.id.graph)
		dayBar = findViewById(R.id.days)

		dayBar.setOnSeekBarChangeListener(object :
			SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(
				seekBar: SeekBar,
				progress: Int,
				fromUser: Boolean
			) {
				if (fromUser) {
					// Post to queue changes.
					dayBar.post {
						update(progress)
					}
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {}

			override fun onStopTrackingTouch(seekBar: SeekBar) {}
		})
	}

	override fun onResume() {
		super.onResume()
		paused = false
		// Run update() after layout.
		usageView.post {
			update(dayBar.progress)
		}
	}

	override fun onPause() {
		super.onPause()
		paused = true
		cancelUsageUpdate()
	}

	override fun onDestroy() {
		super.onDestroy()
		coroutineContext.cancelChildren()
	}

	private fun update(
		days: Int,
		timestamp: Long = System.currentTimeMillis()
	) {
		launch {
			generateUsageChart(days, timestamp)
		}
	}

	private suspend fun generateUsageChart(
		days: Int,
		timestamp: Long
	) = withContext(Dispatchers.IO) {
		val width = usageView.measuredWidth
		val height = usageView.measuredHeight
		if (width < 1 || height < 1) {
			usageView.postDelayed({
				update(days, timestamp)
			}, 1000)
			return@withContext
		}
		val dp = resources.displayMetrics.density
		val padding = (16f * dp).roundToInt() * 2
		val bitmap = drawUsageChart(
			width - padding,
			height - padding,
			timestamp,
			days,
			if (days > 0) {
				getString(R.string.last_x_days, days + 1)
			} else {
				getString(R.string.today)
			},
			resources.getColor(R.color.usage),
			resources.getColor(R.color.dial),
			resources.getColor(R.color.text)
		)
		withContext(Dispatchers.Main) {
			usageView.setImageBitmap(bitmap)
			if (!paused) {
				scheduleUsageUpdate()
			}
		}
	}

	private fun scheduleUsageUpdate() {
		cancelUsageUpdate()
		usageView.postDelayed(updateUsageRunnable, 10000)
	}

	private fun cancelUsageUpdate() {
		usageView.removeCallbacks(updateUsageRunnable)
	}
}
