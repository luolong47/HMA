package aaa.fucklocation.ui.activity

import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.google.android.gms.ads.MobileAds
import com.google.android.material.color.DynamicColors
import apk.fucklocation.R
import apk.fucklocation.databinding.ActivityMainBinding
import aaa.fucklocation.ui.util.ThemeUtils
import rikka.material.app.MaterialActivity

/**
 * 主活动类
 * 
 * 应用程序的主界面，包含底部导航栏和多个Fragment
 * 负责初始化导航、主题和广告
 */
class MainActivity : MaterialActivity() {

    /**
     * 创建活动
     * 
     * 初始化界面、导航控制器、主题和广告
     * 
     * @param savedInstanceState 保存的实例状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ThemeUtils.isSystemAccent) DynamicColors.applyToActivityIfAvailable(this)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        setupWithNavController(binding.bottomNav, navController)

        MobileAds.initialize(this)
    }

    /**
     * 处理向上导航
     * 
     * @return 如果处理了导航返回true，否则返回false
     */
    override fun onSupportNavigateUp(): Boolean {
        val navController = this.findNavController(R.id.nav_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * 应用用户主题资源
     * 
     * @param theme 主题对象
     * @param isDecorView 是否为装饰视图
     */
    override fun onApplyUserThemeResource(theme: Resources.Theme, isDecorView: Boolean) {
        if (!ThemeUtils.isSystemAccent) theme.applyStyle(ThemeUtils.colorThemeStyleRes, true)
        theme.applyStyle(ThemeUtils.getNightThemeStyleRes(this), true)
    }

    /**
     * 计算用户主题键
     * 
     * @return 主题键字符串
     */
    override fun computeUserThemeKey() = ThemeUtils.colorTheme + ThemeUtils.getNightThemeStyleRes(this)

    /**
     * 应用透明系统栏
     */
    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }
}
