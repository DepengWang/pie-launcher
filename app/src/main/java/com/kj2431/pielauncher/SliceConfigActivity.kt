package com.kj2431.pielauncher

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kj2431.pielauncher.ui.ThemeHelper
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.kj2431.pielauncher.ui.EdgeToEdge
import com.kj2431.pielauncher.action.ActionId
import com.kj2431.pielauncher.prefs.Prefs

/**
 * The pie's slice list: view, reorder, remove, add app/command slices, and set
 * an optional long-press action per slice. Token grammar: PRIMARY[##LONG].
 * Persists to Prefs.KEY_SLICES; the service reads the same list.
 */
class SliceConfigActivity : AppCompatActivity() {

    private val slices = mutableListOf<String>()
    private lateinit var adapter: SliceAdapter

    /** When set, the next picker result becomes the long-press of this slice. */
    private var pendingLongFor: Int? = null

    private val pickApp = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK) { pendingLongFor = null; return@registerForActivityResult }
        val pkgs = r.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_PACKAGES).orEmpty()
        val target = pendingLongFor
        if (target != null) {
            pkgs.firstOrNull()?.let { setLong(target, "app:$it") }
            pendingLongFor = null
        } else {
            pkgs.forEach { slices += "app:$it" }
            persist()
        }
    }
    private val pickCmd = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK) { pendingLongFor = null; return@registerForActivityResult }
        val token = r.data?.getStringExtra(CommandPickerActivity.EXTRA_TOKEN)
        val target = pendingLongFor
        if (target != null) {
            token?.let { setLong(target, it) }
            pendingLongFor = null
        } else {
            token?.let { slices += it }
            persist()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        setContentView(R.layout.activity_slice_config)
        val tb = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        tb.setNavigationOnClickListener { finish() }
        title = getString(R.string.configure_pie)
        EdgeToEdge.setup(this, findViewById(R.id.root))

        slices.addAll(Prefs.slices(this))
        adapter = SliceAdapter()
        findViewById<ListView>(R.id.sliceList).apply {
            adapter = this@SliceConfigActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> editDialog(pos) }
        }
        findViewById<Button>(R.id.addApp).setOnClickListener {
            pendingLongFor = null; pickApp.launch(Intent(this, AppPickerActivity::class.java))
        }
        findViewById<Button>(R.id.addCommand).setOnClickListener {
            pendingLongFor = null; pickCmd.launch(Intent(this, CommandPickerActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun persist() { Prefs.setSlices(this, slices); adapter.notifyDataSetChanged() }

    private fun setLong(pos: Int, longToken: String) {
        slices[pos] = primaryOf(slices[pos]) + "##" + longToken
        persist()
    }
    private fun clearLong(pos: Int) { slices[pos] = primaryOf(slices[pos]); persist() }

    private fun editDialog(pos: Int) {
        val hasLong = slices[pos].contains("##")
        val labels = mutableListOf(
            getString(R.string.move_up),
            getString(R.string.move_down),
            getString(R.string.set_longpress)
        )
        if (hasLong) labels += getString(R.string.clear_longpress)
        labels += getString(R.string.remove)

        AlertDialog.Builder(this)
            .setTitle(labelFor(primaryOf(slices[pos])))
            .setItems(labels.toTypedArray()) { _, which ->
                when (labels[which]) {
                    getString(R.string.move_up) -> if (pos > 0) { slices.add(pos - 1, slices.removeAt(pos)); persist() }
                    getString(R.string.move_down) -> if (pos < slices.size - 1) { slices.add(pos + 1, slices.removeAt(pos)); persist() }
                    getString(R.string.set_longpress) -> chooseLongSource(pos)
                    getString(R.string.clear_longpress) -> clearLong(pos)
                    getString(R.string.remove) -> { slices.removeAt(pos); persist() }
                }
            }.show()
    }

    private fun chooseLongSource(pos: Int) {
        val opts = arrayOf(getString(R.string.add_app), getString(R.string.add_command))
        AlertDialog.Builder(this)
            .setTitle(R.string.set_longpress)
            .setItems(opts) { _, which ->
                pendingLongFor = pos
                if (which == 0) pickApp.launch(Intent(this, AppPickerActivity::class.java))
                else pickCmd.launch(Intent(this, CommandPickerActivity::class.java))
            }.show()
    }

    // ---- token helpers ------------------------------------------------------
    private fun primaryOf(token: String) = token.substringBefore("##")
    private fun longOf(token: String): String? = if (token.contains("##")) token.substringAfter("##") else null

    private fun labelFor(token: String): String = when {
        token.startsWith("app:") -> {
            val pkg = token.removePrefix("app:")
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
        }
        token.startsWith("act:") -> {
            val body = token.removePrefix("act:")
            val id = body.substringBefore("|")
            val arg = if (body.contains("|")) "  (" + body.substringAfter("|") + ")" else ""
            (runCatching { getString(ActionId.valueOf(id).label) }.getOrDefault(id)) + arg
        }
        else -> token
    }

    private fun iconFor(token: String): Drawable? = when {
        token.startsWith("app:") ->
            runCatching { packageManager.getApplicationIcon(token.removePrefix("app:")) }.getOrNull()
        token.startsWith("act:") -> {
            val id = runCatching { ActionId.valueOf(token.removePrefix("act:").substringBefore("|")) }.getOrNull()
            id?.let { ContextCompat.getDrawable(this, it.iconRes) }
        }
        else -> null
    }

    private fun controlColor(): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorControlNormal, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private inner class SliceAdapter : BaseAdapter() {
        override fun getCount() = slices.size
        override fun getItem(p: Int) = slices[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val v = cv ?: layoutInflater.inflate(R.layout.row_slice, parent, false)
            val token = slices[p]
            val primary = primaryOf(token)
            val isApp = primary.startsWith("app:")
            v.findViewById<TextView>(R.id.pos).text = (p + 1).toString()
            v.findViewById<TextView>(R.id.label).text = labelFor(primary)
            val type = getString(if (isApp) R.string.type_app else R.string.type_command)
            val longLabel = longOf(token)?.let { getString(R.string.long_prefix, labelFor(it)) } ?: ""
            v.findViewById<TextView>(R.id.type).text = type + longLabel
            val iv = v.findViewById<ImageView>(R.id.icon)
            iv.setImageDrawable(iconFor(primary))
            iv.imageTintList = if (isApp) null
                else android.content.res.ColorStateList.valueOf(controlColor())
            return v
        }
    }
}
