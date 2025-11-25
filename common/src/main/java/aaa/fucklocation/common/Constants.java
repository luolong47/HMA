package aaa.fucklocation.common;

import java.util.Set;

/**
 * 常量类
 * 
 * 存储应用程序中使用的各种常量，包括包名、URL、系统属性等
 */
public class Constants {

    /** 应用程序包名 */
    public static final String APP_PACKAGE_NAME = "apk.fucklocation";

    /** 提供者授权 */
    public static final String PROVIDER_AUTHORITY = APP_PACKAGE_NAME + ".ServiceProvider";
    /** Google移动服务包名 */
    public static final String GMS_PACKAGE_NAME = "com.google.android.gms";
    /** Google服务框架包名 */
    public static final String GSF_PACKAGE_NAME = "com.google.android.gsf";
    /** 更新URL基础路径 */
    public static final String UPDATE_URL_BASE = "https://api.nullptr.icu/android/fuck-location/static/";
    /** 翻译项目URL */
    public static final String TRANSLATE_URL = "https://crowdin.com/project/fuck-location";

    /** Android应用数据隔离启用属性 */
    public static final String ANDROID_APP_DATA_ISOLATION_ENABLED_PROPERTY = "persist.zygote.app_data_isolation";
    /** Android VOLD应用数据隔离启用属性 */
    public static final String ANDROID_VOLD_APP_DATA_ISOLATION_ENABLED_PROPERTY = "persist.sys.vold_app_data_isolation_enabled";

    /** 包管理器接口描述符 */
    public static final String DESCRIPTOR = "android.content.pm.IPackageManager";
    /** 事务代码 */
    public static final int TRANSACTION = 'H' << 24 | 'M' << 16 | 'A' << 8 | 'D';
    /** 获取绑定器操作 */
    public static final int ACTION_GET_BINDER = 1;

    /** 系统用户ID */
    public static final int UID_SYSTEM = 1000;

    /** 不应被隐藏的系统包集合 */
    public static final Set<String> packagesShouldNotHide = Set.of(
            "android",
            "android.media",
            "android.uid.system",
            "android.uid.shell",
            "android.uid.systemui",
            "com.android.permissioncontroller",
            "com.android.providers.downloads",
            "com.android.providers.downloads.ui",
            "com.android.providers.media",
            "com.android.providers.media.module",
            "com.android.providers.settings",
            "com.google.android.webview",
            "com.google.android.providers.media.module"
    );
}
