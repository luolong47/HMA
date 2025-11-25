package aaa.fucklocation

import android.annotation.SuppressLint
import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import apk.fucklocation.R
import aaa.fucklocation.service.ConfigManager
import aaa.fucklocation.service.PrefManager
import aaa.fucklocation.ui.receiver.AppChangeReceiver
import aaa.fucklocation.ui.util.makeToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.zhanghai.android.appiconloader.AppIconLoader
import rikka.material.app.LocaleDelegate
import java.util.*
import kotlin.system.exitProcess

/** 全局应用实例 */
lateinit var hmaApp: MyApp

/**
 * 应用程序主类
 * 
 * 负责初始化应用程序全局状态、配置管理、主题设置和语言环境
 */
class MyApp : Application() {

    /** 是否已Hook */
    @JvmField
    var isHooked = false

    /** 全局协程作用域 */
    val globalScope = CoroutineScope(Dispatchers.Default)
    /** 应用图标加载器 */
    val appIconLoader by lazy {
        val iconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size)
        AppIconLoader(iconSize, false, this)
    }

    /**
     * 创建应用
     * 
     * 初始化全局状态、配置管理器、主题和语言环境
     */
    @Suppress("DEPRECATION")
    @SuppressLint("SdCardPath")
    override fun onCreate() {
        super.onCreate()
        hmaApp = this
        if (!filesDir.absolutePath.startsWith("/data/user/0/")) {
            makeToast(R.string.do_not_dual)
            exitProcess(0)
        }
        AppChangeReceiver.register(this)
        ConfigManager.init()

        AppCompatDelegate.setDefaultNightMode(PrefManager.darkTheme)
        LocaleDelegate.defaultLocale = getLocale(PrefManager.locale)
        val config = resources.configuration
        config.setLocale(LocaleDelegate.defaultLocale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /**
     * 根据标签获取语言环境
     * 
     * @param tag 语言标签，如"zh-CN"、"en"等，或"SYSTEM"表示系统默认
     * @return 对应的语言环境对象
     */
    fun getLocale(tag: String): Locale {
        return if (tag == "SYSTEM") LocaleDelegate.systemLocale
        else Locale.forLanguageTag(tag)
    }
}
