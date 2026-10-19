package com.kj2431.pielauncher.action

import androidx.annotation.DrawableRes
import com.kj2431.pielauncher.R

/** Which underlying mechanism can satisfy an action. Tried in list order. */
enum class Mechanism { NONE, ACCESSIBILITY, ROOT }

/**
 * Catalog of every pie command. Original implementations of standard Android
 * operations — see [ActionRunner] for how each is carried out.
 *
 * [mechanisms] is in priority order: the runner uses the first one available.
 * [argHint] != null means the command takes a parameter (url, keycode, etc.).
 * [iconRes] is a Material-style vector glyph shown on the pie slice.
 */
enum class ActionId(
    val label: String,
    val description: String,
    val mechanisms: List<Mechanism>,
    @DrawableRes val iconRes: Int,
    val argHint: String? = null
) {
    // ---- Navigation keys ----
    HOME("Home", "Trigger home key", listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_home),
    HOME_LONG("Home (long)", "Longpress home key", listOf(Mechanism.ROOT), R.drawable.ic_home),
    MENU("Menu", "Trigger menu key", listOf(Mechanism.ROOT), R.drawable.ic_menu),
    MENU_LONG("Menu (long)", "Longpress menu key", listOf(Mechanism.ROOT), R.drawable.ic_menu),
    BACK("Back", "Trigger back key", listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_arrow_back),
    BACK_LONG("Back (long)", "Longpress back key", listOf(Mechanism.ROOT), R.drawable.ic_arrow_back),
    RECENTS("Recents", "Trigger recent apps", listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_recents),
    SEARCH("Search", "Activate search", listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_search),
    VOICE_SEARCH("Voice search", "Activate voice search", listOf(Mechanism.NONE), R.drawable.ic_mic),

    // ---- App switching ----
    NEXT_APP("Next app", "Switch to next active app (approx.)", listOf(Mechanism.ROOT), R.drawable.ic_arrow_forward),
    PREV_APP("Prev app", "Switch to previous active app (approx.)", listOf(Mechanism.ROOT), R.drawable.ic_skip_previous),
    LAST_APP("Last app", "Switch to last active app", listOf(Mechanism.ROOT), R.drawable.ic_history),

    // ---- System UI ----
    NOTIFICATIONS("Notifications", "Open the notification bar", listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_notifications),
    QUICK_SETTINGS("Quick settings", "Open the quick settings", listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_tune),
    OPEN_KEYBOARD("Keyboard", "Open the keyboard", listOf(Mechanism.NONE), R.drawable.ic_keyboard),
    POWER_MENU("Power menu", "Open the power menu", listOf(Mechanism.ACCESSIBILITY), R.drawable.ic_power),
    LOCK_SCREEN("Lock screen", "Lock the screen", listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_lock),

    // ---- Toggles ----
    WIFI("Wi-Fi", "Toggle Wi-Fi", listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_wifi),
    DATA("Mobile data", "Toggle mobile data", listOf(Mechanism.ROOT), R.drawable.ic_data),
    BLUETOOTH("Bluetooth", "Toggle Bluetooth", listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_bluetooth),
    GPS("Location", "Toggle location", listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_location),
    IMMERSIVE("Immersive", "Toggle immersive mode", listOf(Mechanism.ROOT), R.drawable.ic_fullscreen),

    // ---- Advanced ----
    KEY("Key", "Trigger an arbitrary keycode", listOf(Mechanism.ROOT), R.drawable.ic_key, argHint = "keycode (e.g. 26)"),
    ACTIVITY("Activity", "Start an activity component", listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_open_in_new, argHint = "pkg/.Activity"),
    WEBPAGE("Webpage", "Open a URL", listOf(Mechanism.NONE), R.drawable.ic_public, argHint = "https://…"),
    SCRIPT("Script", "Run a shell script", listOf(Mechanism.ROOT), R.drawable.ic_terminal, argHint = "shell command"),
    KILL_APP("Kill app", "Force-stop foreground app + home", listOf(Mechanism.ROOT), R.drawable.ic_close),
    KILL_ALL("Kill all", "Kill background apps", listOf(Mechanism.ROOT), R.drawable.ic_clear_all),
    TASKER("Tasker task", "Run a Tasker task", listOf(Mechanism.NONE), R.drawable.ic_bolt, argHint = "task name"),
    SCREENSHOT("Screenshot", "Take a screenshot", listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_image),
    UNPIN("Unpin", "Exit screen pinning (approx.)", listOf(Mechanism.ROOT), R.drawable.ic_pin),
    ASSISTANT("Assistant", "Activate the assistant", listOf(Mechanism.NONE), R.drawable.ic_assistant),
    SPLIT_SCREEN("Split screen", "Toggle split screen", listOf(Mechanism.ROOT, Mechanism.ACCESSIBILITY), R.drawable.ic_splitscreen),
    FLOAT_WINDOW("Floating window", "Float the foreground app, or maximise a floating window", listOf(Mechanism.ROOT), R.drawable.ic_float_window);

    val needsArg: Boolean get() = argHint != null
}
