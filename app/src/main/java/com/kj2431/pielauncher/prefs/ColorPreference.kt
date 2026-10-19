package com.kj2431.pielauncher.prefs

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kj2431.pielauncher.R
import com.kj2431.pielauncher.service.PieOverlay
import com.kj2431.pielauncher.ui.ColorWheelView

/**
 * Color setting with a swatch and a picker dialog (hue/saturation wheel,
 * brightness, opacity, hex entry and the old preset colors). Stores the same
 * strings the previous list did: "system" or "#AARRGGBB".
 */
class ColorPreference @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : Preference(ctx, attrs) {

    private var value = SYSTEM

    init { widgetLayoutResource = R.layout.pref_color_swatch }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? = a.getString(index)

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedString(defaultValue as? String ?: SYSTEM) ?: SYSTEM
        summary = label(value)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.swatch)?.background = swatch(resolve(value), oval = true)
    }

    override fun onClick() = showPicker()

    private fun commit(v: String) {
        if (!callChangeListener(v)) return
        value = v
        persistString(v)
        summary = label(v)
        notifyChanged()
    }

    /** Preset name when the value is one of the old list entries, else the hex code. */
    private fun label(v: String): String {
        val res = context.resources
        val values = res.getStringArray(R.array.color_values)
        val names = res.getStringArray(R.array.color_entries)
        val i = values.indexOfFirst { it.equals(v, ignoreCase = true) }
        return if (i >= 0) names[i] else v.uppercase()
    }

    /** The color a value draws with; "system" maps to this slot's system default. */
    private fun resolve(v: String): Int {
        if (v != SYSTEM) runCatching { return Color.parseColor(v) }
        val d = PieOverlay.systemColors(context)
        return when (key) {
            Prefs.KEY_PIE_BG -> d.bg
            Prefs.KEY_PIE_LONG -> d.longc
            Prefs.KEY_PIE_BORDER -> d.border
            else -> d.primary
        }
    }

    private fun swatch(color: Int, oval: Boolean) = GradientDrawable().apply {
        shape = if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        if (!oval) cornerRadius = dp(8).toFloat()
        setColor(color)
        setStroke(dp(1), 0x80808080.toInt())
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun showPicker() {
        val ctx = context
        val start = resolve(value)
        val hsv = FloatArray(3).also { Color.colorToHSV(start, it) }
        var alpha = Color.alpha(start)
        var keepSystem = value == SYSTEM   // OK without touching anything keeps "system"
        var updating = false

        val wheel = ColorWheelView(ctx)
        val preview = View(ctx)
        val hex = EditText(ctx).apply {
            setHint(R.string.color_hex_hint)
            isSingleLine = true
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(9))
        }
        val valueBar = SeekBar(ctx).apply { max = 100 }
        val alphaBar = SeekBar(ctx).apply { max = 255 }

        fun current() = Color.HSVToColor(alpha, hsv)
        fun refresh(fromHex: Boolean = false) {
            updating = true
            val c = current()
            preview.background = swatch(c, oval = false)
            if (!fromHex) hex.setText(String.format("#%08X", c))
            wheel.set(hsv[0], hsv[1])
            wheel.brightness = hsv[2]
            valueBar.progress = (hsv[2] * 100).toInt()
            alphaBar.progress = alpha
            updating = false
        }
        fun pick(c: Int) {
            Color.colorToHSV(c, hsv)
            alpha = Color.alpha(c)
            keepSystem = false
            refresh()
        }

        wheel.onPick = { h, s -> hsv[0] = h; hsv[1] = s; keepSystem = false; refresh() }
        valueBar.setOnSeekBarChangeListener(onSeek { hsv[2] = it / 100f; keepSystem = false; refresh() })
        alphaBar.setOnSeekBarChangeListener(onSeek { alpha = it; keepSystem = false; refresh() })
        hex.doAfterTextChanged { s ->
            if (updating) return@doAfterTextChanged
            val t = s?.toString()?.trim().orEmpty().let { if (it.startsWith("#")) it else "#$it" }
            if (t.length != 7 && t.length != 9) return@doAfterTextChanged
            val c = runCatching { Color.parseColor(t) }.getOrNull() ?: return@doAfterTextChanged
            Color.colorToHSV(c, hsv)
            alpha = Color.alpha(c)
            keepSystem = false
            refresh(fromHex = true)
        }

        val presets = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        ctx.resources.getStringArray(R.array.color_values).filter { it != SYSTEM }.forEach { v ->
            val c = Color.parseColor(v)
            presets.addView(View(ctx).apply {
                background = swatch(c, oval = true)
                setOnClickListener { pick(c) }
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8) })
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(preview, LinearLayout.LayoutParams(dp(56), dp(36)).apply { marginEnd = dp(16) })
                addView(hex, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(wheel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            addView(caption(R.string.color_brightness))
            addView(valueBar)
            addView(caption(R.string.color_opacity))
            addView(alphaBar)
            addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                addView(presets)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        }
        refresh()

        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(ScrollView(ctx).apply { addView(content) })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                commit(if (keepSystem) SYSTEM else String.format("#%08X", current()))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.color_system) { _, _ -> commit(SYSTEM) }
            .show()
    }

    private fun caption(res: Int) = TextView(context).apply {
        setText(res)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun onSeek(onUser: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onUser(progress)
        }
        override fun onStartTrackingTouch(bar: SeekBar) {}
        override fun onStopTrackingTouch(bar: SeekBar) {}
    }

    companion object {
        private const val SYSTEM = "system"
    }
}
