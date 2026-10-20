package com.kj2431.pielauncher.action

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
    @StringRes val label: Int,
    @StringRes val description: Int,
    val mechanisms: List<Mechanism>,
    @DrawableRes val iconRes: Int,
    @StringRes val argHint: Int? = null
) {
    // ---- Navigation keys ----
    HOME(R.string.act_home, R.string.act_home_desc, listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_home),
    HOME_LONG(R.string.act_home_long, R.string.act_home_long_desc, listOf(Mechanism.ROOT), R.drawable.ic_home),
    MENU(R.string.act_menu, R.string.act_menu_desc, listOf(Mechanism.ROOT), R.drawable.ic_menu),
    MENU_LONG(R.string.act_menu_long, R.string.act_menu_long_desc, listOf(Mechanism.ROOT), R.drawable.ic_menu),
    BACK(R.string.act_back, R.string.act_back_desc, listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_arrow_back),
    BACK_LONG(R.string.act_back_long, R.string.act_back_long_desc, listOf(Mechanism.ROOT), R.drawable.ic_arrow_back),
    RECENTS(R.string.act_recents, R.string.act_recents_desc, listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_recents),
    SEARCH(R.string.act_search, R.string.act_search_desc, listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_search),
    VOICE_SEARCH(R.string.act_voice_search, R.string.act_voice_search_desc, listOf(Mechanism.NONE), R.drawable.ic_mic),

    // ---- App switching ----
    // Root reads the recent-task list; accessibility falls back to a double Recents press.
    NEXT_APP(R.string.act_next_app, R.string.act_next_app_desc, listOf(Mechanism.ROOT, Mechanism.ACCESSIBILITY), R.drawable.ic_arrow_forward),
    PREV_APP(R.string.act_prev_app, R.string.act_prev_app_desc, listOf(Mechanism.ROOT, Mechanism.ACCESSIBILITY), R.drawable.ic_skip_previous),
    LAST_APP(R.string.act_last_app, R.string.act_last_app_desc, listOf(Mechanism.ROOT, Mechanism.ACCESSIBILITY), R.drawable.ic_history),

    // ---- System UI ----
    NOTIFICATIONS(R.string.act_notifications, R.string.act_notifications_desc, listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_notifications),
    QUICK_SETTINGS(R.string.act_quick_settings, R.string.act_quick_settings_desc, listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_tune),
    OPEN_KEYBOARD(R.string.act_keyboard, R.string.act_keyboard_desc, listOf(Mechanism.NONE), R.drawable.ic_keyboard),
    POWER_MENU(R.string.act_power_menu, R.string.act_power_menu_desc, listOf(Mechanism.ACCESSIBILITY), R.drawable.ic_power),
    LOCK_SCREEN(R.string.act_lock_screen, R.string.act_lock_screen_desc, listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_lock),

    // ---- Toggles ----
    WIFI(R.string.act_wifi, R.string.act_wifi_desc, listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_wifi),
    DATA(R.string.act_data, R.string.act_data_desc, listOf(Mechanism.ROOT), R.drawable.ic_data),
    BLUETOOTH(R.string.act_bluetooth, R.string.act_bluetooth_desc, listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_bluetooth),
    GPS(R.string.act_location, R.string.act_location_desc, listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_location),
    IMMERSIVE(R.string.act_immersive, R.string.act_immersive_desc, listOf(Mechanism.ROOT), R.drawable.ic_fullscreen),

    // ---- Advanced ----
    KEY(R.string.act_key, R.string.act_key_desc, listOf(Mechanism.ROOT), R.drawable.ic_key, argHint = R.string.act_key_hint),
    ACTIVITY(R.string.act_activity, R.string.act_activity_desc, listOf(Mechanism.ROOT, Mechanism.NONE), R.drawable.ic_open_in_new, argHint = R.string.act_activity_hint),
    WEBPAGE(R.string.act_webpage, R.string.act_webpage_desc, listOf(Mechanism.NONE), R.drawable.ic_public, argHint = R.string.act_webpage_hint),
    SCRIPT(R.string.act_script, R.string.act_script_desc, listOf(Mechanism.ROOT), R.drawable.ic_terminal, argHint = R.string.act_script_hint),
    KILL_APP(R.string.act_kill_app, R.string.act_kill_app_desc, listOf(Mechanism.ROOT), R.drawable.ic_close),
    KILL_ALL(R.string.act_kill_all, R.string.act_kill_all_desc, listOf(Mechanism.ROOT), R.drawable.ic_clear_all),
    TASKER(R.string.act_tasker, R.string.act_tasker_desc, listOf(Mechanism.NONE), R.drawable.ic_bolt, argHint = R.string.act_tasker_hint),
    SCREENSHOT(R.string.act_screenshot, R.string.act_screenshot_desc, listOf(Mechanism.ACCESSIBILITY, Mechanism.ROOT), R.drawable.ic_image),
    UNPIN(R.string.act_unpin, R.string.act_unpin_desc, listOf(Mechanism.ROOT), R.drawable.ic_pin),
    ASSISTANT(R.string.act_assistant, R.string.act_assistant_desc, listOf(Mechanism.NONE), R.drawable.ic_assistant),
    SPLIT_SCREEN(R.string.act_split_screen, R.string.act_split_screen_desc, listOf(Mechanism.ROOT, Mechanism.ACCESSIBILITY), R.drawable.ic_splitscreen),
    FLOAT_WINDOW(R.string.act_float_window, R.string.act_float_window_desc, listOf(Mechanism.ROOT), R.drawable.ic_float_window);

    val needsArg: Boolean get() = argHint != null
}
