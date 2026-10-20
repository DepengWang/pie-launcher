package com.kj2431.pielauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kj2431.pielauncher.ui.ThemeHelper
import androidx.appcompat.widget.Toolbar
import com.kj2431.pielauncher.ui.EdgeToEdge
import com.kj2431.pielauncher.action.ActionId
import com.kj2431.pielauncher.action.Mechanism

/** Lists every command; returns a slice token "act:ID" or "act:ID|arg". */
class CommandPickerActivity : AppCompatActivity() {

    private val commands = ActionId.entries.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        setContentView(R.layout.activity_command_picker)
        val tb = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        tb.setNavigationOnClickListener { finish() }
        title = getString(R.string.add_command)
        EdgeToEdge.setup(this, findViewById(R.id.root))

        val list = findViewById<ListView>(R.id.cmdList)
        list.adapter = CmdAdapter()
        list.setOnItemClickListener { _, _, pos, _ -> pick(commands[pos]) }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun pick(id: ActionId) {
        if (!id.needsArg) { ret("act:${id.name}"); return }
        val input = EditText(this).apply {
            id.argHint?.let { setHint(it) }
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(id.label)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val arg = input.text.toString().trim()
                ret(if (arg.isEmpty()) "act:${id.name}" else "act:${id.name}|$arg")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ret(token: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TOKEN, token)); finish()
    }

    private fun mechanismLabel(m: Mechanism): Int = when (m) {
        Mechanism.NONE -> R.string.mech_none
        Mechanism.ACCESSIBILITY -> R.string.mech_accessibility
        Mechanism.ROOT -> R.string.mech_root
    }

    private inner class CmdAdapter : BaseAdapter() {
        override fun getCount() = commands.size
        override fun getItem(p: Int) = commands[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val v = cv ?: layoutInflater.inflate(R.layout.row_command, parent, false)
            val c = commands[p]
            v.findViewById<ImageView>(R.id.icon).setImageResource(c.iconRes)
            v.findViewById<TextView>(R.id.label).setText(c.label)
            v.findViewById<TextView>(R.id.desc).text =
                getString(c.description) + "  [" + c.mechanisms.joinToString("|") { getString(mechanismLabel(it)) } + "]"
            return v
        }
    }

    companion object { const val EXTRA_TOKEN = "token" }
}
