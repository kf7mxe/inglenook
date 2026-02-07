package com.kf7mxe.inglenook

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.ComposeShader
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import com.lightningkite.kiteui.models.Color
import com.lightningkite.kiteui.models.HSVColor
import com.lightningkite.kiteui.models.ThemeAndBack
import com.lightningkite.kiteui.views.RContext
import com.lightningkite.kiteui.views.RView
import com.lightningkite.reactive.core.MutableReactiveValue

actual class ColorPicker actual constructor(context: RContext) : RView(context) {

    private val displayMetrics = context.activity.resources.displayMetrics
    private fun dp(value: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, displayMetrics).toInt()

    override val native = View(context.activity).apply {
        isClickable = true
        isFocusable = true
        minimumWidth = dp(48f)
        minimumHeight = dp(48f)
        elevation = dp(4f).toFloat()
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
        }

        setOnClickListener {
            showColorPickerDialog(this.context)
        }
    }

    actual val color: MutableReactiveValue<Color> = object : MutableReactiveValue<Color> {
        private var _value = Color.black
        private val listeners = ArrayList<() -> Unit>()

        override var value: Color
            get() = _value
            set(v) {
                _value = v
                updateViewColor(v)
                listeners.forEach { it() }
            }

        override suspend fun set(value: Color) {
            this.value = value
        }

        override fun addListener(listener: () -> Unit): () -> Unit {
            listeners.add(listener)
            return { listeners.remove(listener) }
        }
    }

    init {
        updateViewColor(color.value)
    }

    private fun updateViewColor(color: Color) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color.toInt())
            cornerRadius = dp(8f).toFloat()
            setStroke(dp(1f), AndroidColor.parseColor("#BDBDBD"))
        }
        native.background = drawable
    }

    actual var enabled: Boolean
        get() = native.isEnabled
        set(value) {
            native.isEnabled = value
            native.alpha = if (value) 1.0f else 0.5f
        }

    override fun applyTheme(theme: ThemeAndBack) {
        super.applyTheme(theme)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showColorPickerDialog(androidContext: Context) {
        if (!enabled) return

        // 1. Convert current RGB Color to HSV for the UI logic
        val initialColor = color.value
        val initialHsv = initialColor.toHSV()

        // Mutable state for the dialog
        var currentHue = initialHsv.hue.degrees
        var currentSat = initialHsv.saturation
        var currentVal = initialHsv.value
        var currentAlpha = initialHsv.alpha

        // Layout Container
        val layout = LinearLayout(androidContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            clipToPadding = false
        }

        // --- 2. THE COLOR AREA (Sat/Val Box) ---
        // We need a custom view to draw the cursor and handle 2D touch
        val colorAreaView = object : View(androidContext) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2f).toFloat()
                color = AndroidColor.WHITE
            }
            val cursorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2f).toFloat()
                color = AndroidColor.BLACK
            }

            // Draw backgrounds (Hue + White Grad + Black Grad)
            val satGrad = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(AndroidColor.WHITE, AndroidColor.TRANSPARENT))
            val valGrad = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(AndroidColor.TRANSPARENT, AndroidColor.BLACK))
            val hueBase = GradientDrawable().apply { setColor(AndroidColor.RED) } // placeholder
            val combinedDrawable = LayerDrawable(arrayOf(hueBase, satGrad, valGrad))

            init {
                background = combinedDrawable
            }

            fun updateHueBase(h: Float) {
                // H is 0..360, we need a Color
                val hueColor = HSVColor(hue = com.lightningkite.kiteui.models.Angle(h / 360f), saturation = 1f, value = 1f).toRGB().toInt()
                hueBase.setColor(hueColor)
                invalidate()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                // Draw Cursor based on currentSat and currentVal
                val x = currentSat * width
                val y = (1f - currentVal) * height

                // Draw a ring (White inner, Black outer) so it's visible on any color
                canvas.drawCircle(x, y, dp(6f).toFloat(), cursorShadowPaint)
                canvas.drawCircle(x, y, dp(5f).toFloat(), cursorPaint)
            }
        }

        // Set layout params for the box (Square-ish)
        colorAreaView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200f)).apply {
            bottomMargin = dp(16f)
        }

        // --- 3. PREVIEW STRIP ---
        // Shows Old Color vs New Color
        val previewView = View(androidContext).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40f)).apply {
                bottomMargin = dp(16f)
            }
            // Checkered background for alpha visibility
            val check = GradientDrawable().apply {
                setColor(AndroidColor.WHITE)
                setStroke(1, AndroidColor.LTGRAY)
            }
            background = check
        }

        // We use a drawable to show the actual color on top of the checkered board
        val previewDrawable = GradientDrawable().apply {
            setColor(initialColor.toInt())
            cornerRadius = dp(4f).toFloat()
        }
        previewView.foreground = previewDrawable

        fun updateCalculatedColor() {
            // Reconstruct color
            val hsv = HSVColor(
                hue = com.lightningkite.kiteui.models.Angle(currentHue / 360f),
                saturation = currentSat,
                value = currentVal,
                alpha = currentAlpha
            )
            val rgb = hsv.toRGB()
            previewDrawable.setColor(rgb.toInt())
        }

        // Color Area Touch Logic
        colorAreaView.setOnTouchListener { v, event ->
            val w = v.width.toFloat()
            val h = v.height.toFloat()

            when(event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // X axis is Saturation
                    currentSat = (event.x / w).coerceIn(0f, 1f)
                    // Y axis is Value (inverted: top is 1, bottom is 0)
                    currentVal = (1f - (event.y / h)).coerceIn(0f, 1f)

                    v.invalidate() // Redraw cursor
                    updateCalculatedColor()
                    true
                }
                else -> true
            }
        }

        // Initialize Hue Background
        colorAreaView.updateHueBase(currentHue)

        layout.addView(colorAreaView)
        layout.addView(previewView)

        // --- 4. HUE SLIDER ---
        // A gradient of all colors
        val hueSlider = SeekBar(androidContext).apply {
            max = 360
            progress = currentHue.toInt()
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))

            val rainbow = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                AndroidColor.RED, AndroidColor.YELLOW, AndroidColor.GREEN,
                AndroidColor.CYAN, AndroidColor.BLUE, AndroidColor.MAGENTA, AndroidColor.RED
            ))
            rainbow.cornerRadius = dp(4f).toFloat()
            progressDrawable = rainbow
            thumbTintList = ColorStateList.valueOf(AndroidColor.DKGRAY) // Simple dark thumb

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    currentHue = p.toFloat()
                    colorAreaView.updateHueBase(currentHue) // Updates the color of the big box
                    updateCalculatedColor()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(hueSlider)

        // --- 5. ALPHA SLIDER ---
        val alphaSlider = SeekBar(androidContext).apply {
            max = 255
            progress = (currentAlpha * 255).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16f)
            }

            // Simple gray gradient for alpha
            val alphaGrad = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                AndroidColor.TRANSPARENT, AndroidColor.BLACK
            ))
            alphaGrad.cornerRadius = dp(4f).toFloat()
            progressDrawable = alphaGrad

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    currentAlpha = p / 255f
                    updateCalculatedColor()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(alphaSlider)


        AlertDialog.Builder(androidContext, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Select Color")
            .setView(layout)
            .setPositiveButton("Select") { dialog: DialogInterface, id: Int ->
                // Final conversion back to RGB
                val finalHsv = HSVColor(
                    hue = com.lightningkite.kiteui.models.Angle(currentHue / 360f),
                    saturation = currentSat,
                    value = currentVal,
                    alpha = currentAlpha
                )
                color.value = finalHsv.toRGB()
            }
            .setNegativeButton("Cancel", null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(AndroidColor.parseColor("#6200EE"))
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(AndroidColor.GRAY)
            }
    }
}