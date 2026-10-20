package com.kj2431.pielauncher.action

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.kj2431.pielauncher.R
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.root.RootHelper
import com.kj2431.pielauncher.service.PieAccessibilityService
import java.util.concurrent.Executors
import android.accessibilityservice.AccessibilityService as AS

/**
 * Executes a pie command by choosing the first available mechanism from
 * [ActionId.mechanisms]. Accessibility and plain mechanisms are quick and run
 * on the caller's (main) thread; from the first root mechanism on, the rest
 * moves to [io], so a slow su command never holds up Back or Home.
 */
object ActionRunner {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun run(ctx: Context, id: ActionId, arg: String? = null) = attempt(ctx, id, arg, id.mechanisms, onIo = false)

    private fun attempt(ctx: Context, id: ActionId, arg: String?, list: List<Mechanism>, onIo: Boolean) {
        for ((i, m) in list.withIndex()) {
            if (m == Mechanism.ROOT && !onIo) {
                if (!Prefs.rootActionsEnabled(ctx)) continue
                io.execute { attempt(ctx, id, arg, list.drop(i), onIo = true) }
                return
            }
            if (available(ctx, m) && tryMechanism(ctx, id, arg, m)) return
        }
        toast(ctx, ctx.getString(R.string.action_unavailable, ctx.getString(id.label)))
    }

    private fun available(ctx: Context, m: Mechanism): Boolean = when (m) {
        Mechanism.NONE -> true
        Mechanism.ACCESSIBILITY -> PieAccessibilityService.isAvailable
        Mechanism.ROOT -> Prefs.rootActionsEnabled(ctx) && RootHelper.isAvailable()
    }

    private fun tryMechanism(ctx: Context, id: ActionId, arg: String?, m: Mechanism): Boolean =
        when (m) {
            Mechanism.ACCESSIBILITY -> accessibility(id)
            Mechanism.ROOT -> root(ctx, id, arg)
            Mechanism.NONE -> plain(ctx, id, arg)
        }

    // ---- Accessibility (global actions) ------------------------------------
    private fun accessibility(id: ActionId): Boolean {
        val s = PieAccessibilityService.instance ?: return false
        if (id == ActionId.LAST_APP || id == ActionId.PREV_APP || id == ActionId.NEXT_APP) {
            // Recents twice switches to the previous task on most launchers.
            if (!s.performGlobalAction(AS.GLOBAL_ACTION_RECENTS)) return false
            main.postDelayed({ s.performGlobalAction(AS.GLOBAL_ACTION_RECENTS) }, 250)
            return true
        }
        val action = when (id) {
            ActionId.HOME -> AS.GLOBAL_ACTION_HOME
            ActionId.BACK -> AS.GLOBAL_ACTION_BACK
            ActionId.RECENTS -> AS.GLOBAL_ACTION_RECENTS
            ActionId.NOTIFICATIONS -> AS.GLOBAL_ACTION_NOTIFICATIONS
            ActionId.QUICK_SETTINGS -> AS.GLOBAL_ACTION_QUICK_SETTINGS
            ActionId.POWER_MENU -> AS.GLOBAL_ACTION_POWER_DIALOG
            ActionId.SPLIT_SCREEN -> AS.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            ActionId.LOCK_SCREEN ->
                if (Build.VERSION.SDK_INT >= 28) AS.GLOBAL_ACTION_LOCK_SCREEN else return false
            ActionId.SCREENSHOT ->
                if (Build.VERSION.SDK_INT >= 30) AS.GLOBAL_ACTION_TAKE_SCREENSHOT else return false
            else -> return false
        }
        return s.performGlobalAction(action)
    }

    // ---- Root (su) ---------------------------------------------------------
    private fun root(ctx: Context, id: ActionId, arg: String?): Boolean = when (id) {
        ActionId.HOME -> RootHelper.pressKey(3)
        ActionId.HOME_LONG -> RootHelper.exec("input keyevent --longpress 3")
        ActionId.MENU -> RootHelper.pressKey(82)
        ActionId.MENU_LONG -> RootHelper.exec("input keyevent --longpress 82")
        ActionId.BACK -> RootHelper.pressKey(4)
        ActionId.BACK_LONG -> RootHelper.exec("input keyevent --longpress 4")
        ActionId.RECENTS -> RootHelper.pressKey(187)
        ActionId.SEARCH -> RootHelper.pressKey(84)
        ActionId.LAST_APP, ActionId.PREV_APP -> switchApp(ctx, oldest = false)
        ActionId.NEXT_APP -> switchApp(ctx, oldest = true)
        ActionId.NOTIFICATIONS -> RootHelper.exec("cmd statusbar expand-notifications")
        ActionId.QUICK_SETTINGS -> RootHelper.exec("cmd statusbar expand-settings")
        ActionId.LOCK_SCREEN -> RootHelper.pressKey(26)
        ActionId.SCREENSHOT -> RootHelper.pressKey(120) // KEYCODE_SYSRQ
        ActionId.WIFI -> RootHelper.exec(
            "if [ \"$(settings get global wifi_on)\" = \"1\" ]; then svc wifi disable; else svc wifi enable; fi")
        ActionId.DATA -> RootHelper.exec(
            "if [ \"$(settings get global mobile_data)\" = \"1\" ]; then svc data disable; else svc data enable; fi")
        ActionId.BLUETOOTH -> RootHelper.exec(
            "if [ \"$(settings get global bluetooth_on)\" = \"1\" ]; then svc bluetooth disable; else svc bluetooth enable; fi")
        ActionId.GPS -> RootHelper.exec(
            "if [ \"$(settings get secure location_mode)\" = \"0\" ]; then settings put secure location_mode 3; else settings put secure location_mode 0; fi")
        ActionId.IMMERSIVE -> RootHelper.exec(
            "if settings get global policy_control | grep -q immersive; then settings put global policy_control null; else settings put global policy_control immersive.full=*; fi")
        ActionId.KEY -> arg?.toIntOrNull()?.let { RootHelper.pressKey(it) } ?: false
        ActionId.ACTIVITY -> arg?.let { RootHelper.exec("am start -n $it") } ?: false
        ActionId.SCRIPT -> arg?.let { RootHelper.exec(it) } ?: false
        ActionId.KILL_APP -> RootHelper.exec(
            "p=$(dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | sed -n 's#.* \\([a-zA-Z0-9_.]*\\)/.*#\\1#p'); [ -n \"\$p\" ] && am force-stop \"\$p\"; input keyevent 3")
        ActionId.KILL_ALL -> RootHelper.exec("am kill-all")
        // Root first: some ROMs (e.g. OxygenOS 16) accept the accessibility
        // TOGGLE_SPLIT_SCREEN action but do nothing. WM Shell's own command
        // splits the foreground task to the bottom/right (the previous task
        // fills the other half), or leaves split keeping the foreground task.
        ActionId.SPLIT_SCREEN -> RootHelper.exec(
            "w='dumpsys activity service SystemUIService WMShell'",
            "t=$(dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity | sed -n 's/.* t\\([0-9]*\\)}.*/\\1/p')",
            "if [ -z \"\$t\" ]; then false; elif \$w 2>/dev/null | grep -q 'isSplitActive=true'; then \$w splitscreen exitSplitScreen \$t >/dev/null; else \$w splitscreen moveToSideStage \$t 1 >/dev/null; fi")
        // Relaunch an app's launcher intent with a windowing mode: if a floating
        // window is showing, its task comes back fullscreen (1, maximise);
        // otherwise the foreground app's task floats (5, the ROM's floating
        // window). The home screen is never floated.
        ActionId.FLOAT_WINDOW -> RootHelper.exec(
            "d=$(dumpsys activity activities 2>/dev/null)",
            "f=$(echo \"\$d\" | grep -m1 -E '\\* Task\\{.* type=standard .* visible=true .*mode=freeform' | sed -n 's/.* #\\([0-9]*\\) .*/\\1/p')",
            "if [ -n \"\$f\" ]; then t=\$f; m=1; else t=$(echo \"\$d\" | grep -m1 topResumedActivity | sed -n 's/.* t\\([0-9]*\\)}.*/\\1/p'); m=5; echo \"\$d\" | grep -qE \"\\* Task\\{[^ ]* #\$t type=standard\" || t=; fi",
            "p=; [ -n \"\$t\" ] && p=$(echo \"\$d\" | grep -m1 -E \"ActivityRecord\\{[^}]* t\$t\\}\" | sed -n 's#.* u[0-9]* \\([a-zA-Z0-9_.]*\\)/.*#\\1#p')",
            "c=; [ -n \"\$p\" ] && c=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \"\$p\" 2>/dev/null | tail -1)",
            "case \"\$c\" in */*) am start --windowingMode \$m -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n \"\$c\" >/dev/null 2>&1;; *) false;; esac")
        ActionId.UNPIN -> RootHelper.exec("input keyevent 4", "input keyevent 187") // best-effort
        else -> false
    }

    /**
     * Brings another recent app to the front by starting its launcher intent,
     * which resumes its existing task the way a home-screen tap does. The
     * default is the most recent app other than the foreground one; [oldest]
     * picks the least recent instead, so repeating it cycles through them all.
     */
    private fun switchApp(ctx: Context, oldest: Boolean): Boolean {
        val recents = RootHelper.query("dumpsys activity recents") ?: return false
        val top = RootHelper.query("dumpsys activity activities | grep -m1 topResumedActivity").orEmpty()
        val topTask = Regex(""" t(\d+)\}""").find(top)?.groupValues?.get(1)
        val topPkg = Regex(""" u\d+ ([^/ ]+)/""").find(top)?.groupValues?.get(1)
        val pkgs = recentPackages(recents, skipTask = topTask)
            .filter { it != topPkg && it != ctx.packageName }
            .distinct()
        val pm = ctx.packageManager
        val launch = (if (oldest) pkgs.asReversed() else pkgs)
            .firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) } ?: return false
        return startActivity(ctx, launch.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    /**
     * Packages of this user's standard (non-home) tasks in `dumpsys activity
     * recents`, most recent first. Tasks of other users (e.g. cloned "dual"
     * apps) are skipped, since their launcher intent would open our user's copy.
     */
    private fun recentPackages(dump: String, skipTask: String?): List<String> {
        val myUser = (android.os.Process.myUid() / 100_000).toString()
        val out = mutableListOf<String>()
        var take = false
        dump.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("* Recent #") -> {
                    val task = Regex(""" #(\d+) """).find(line)?.groupValues?.get(1)
                    take = " type=standard " in line && task != skipTask
                }
                take && line.startsWith("userId=") ->
                    take = line.substringAfter("userId=").substringBefore(' ') == myUser
                // Older releases print realActivity=, newer ones mActivityComponent=.
                take && (line.startsWith("realActivity=") || line.startsWith("mActivityComponent=")) -> {
                    out += line.substringAfter('=').substringBefore('/').trim()
                    take = false
                }
            }
        }
        return out
    }

    // ---- Plain Android (no special privilege) ------------------------------
    private fun plain(ctx: Context, id: ActionId, arg: String?): Boolean = try {
        when (id) {
            ActionId.HOME -> startActivity(ctx, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            ActionId.SEARCH -> startActivity(ctx, Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH))
            ActionId.VOICE_SEARCH -> startActivity(ctx, Intent(RecognizerIntent.ACTION_WEB_SEARCH))
            ActionId.ASSISTANT -> startActivity(ctx, Intent(Intent.ACTION_VOICE_COMMAND))
            ActionId.WEBPAGE -> arg?.let { startActivity(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(it))) } ?: false
            ActionId.ACTIVITY -> arg?.let {
                val parts = it.split("/")
                startActivity(ctx, Intent().setClassName(parts[0], if (parts[1].startsWith(".")) parts[0] + parts[1] else parts[1]))
            } ?: false
            ActionId.OPEN_KEYBOARD -> {
                (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .toggleSoftInput(InputMethodManager.SHOW_FORCED, 0); true
            }
            ActionId.WIFI -> startActivity(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))
            ActionId.BLUETOOTH -> startActivity(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            ActionId.GPS -> startActivity(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            ActionId.TASKER -> arg?.let {
                // Requires Tasker -> Settings -> "Allow External Access".
                startActivity(ctx, Intent("net.dinglisch.android.tasker.ACTION_TASK").putExtra("task_name", it))
            } ?: false
            else -> false
        }
    } catch (e: Exception) { false }

    private fun startActivity(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    private fun toast(ctx: Context, msg: String) =
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}
