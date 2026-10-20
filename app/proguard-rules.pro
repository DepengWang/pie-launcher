# Custom preferences are only referenced from res/xml/root_preferences.xml and
# are inflated reflectively by the preference framework.
-keep class com.kj2431.pielauncher.prefs.** extends androidx.preference.Preference {
    <init>(...);
}
