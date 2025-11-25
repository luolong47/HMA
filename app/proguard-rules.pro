# Xposed
-keepclassmembers class aaa.fucklocation.MyApp {
    boolean isHooked;
}

# Enum class
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class aaa.fucklocation.data.UpdateData { *; }
-keep class aaa.fucklocation.data.UpdateData$* { *; }

-keep,allowoptimization class * extends androidx.preference.PreferenceFragmentCompat
-keepclassmembers class apk.fucklocation.databinding.**  {
    public <methods>;
}
