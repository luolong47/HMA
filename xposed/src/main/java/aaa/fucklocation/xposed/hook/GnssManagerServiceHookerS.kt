package aaa.fucklocation.xposed.hook

import android.annotation.SuppressLint
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import aaa.fucklocation.xposed.HMAService
import aaa.fucklocation.xposed.logE
import aaa.fucklocation.xposed.logI
import aaa.fucklocation.common.Constants

/** 日志标签 */
private const val TAG = "GnssManagerServiceHookerS"

/** 日志消息常量 */
private const val MSG_IN_METHOD = "在%s中!调用者包名: %s"
private const val MSG_IN_WHITELIST = "在白名单中!丢弃注册请求..."

/**
 * GNSS管理服务Hook类
 * 
 * 该类实现了IFrameworkHook接口，负责Hook Android 12及以上版本的GNSS相关方法，
 * 包括GNSS状态回调、NMEA回调、测量监听器等，以防止应用获取真实的GNSS信息
 */
@SuppressLint("PrivateApi")
class GnssManagerServiceHookerS : IFrameworkHook {
    /** Hook列表，用于存储所有已安装的Hook */
    private var hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        // 在这里实现加载逻辑，但实际使用 initHooks
    }

    /**
     * 卸载所有Hook
     */
    override fun unload() {
        hooks.forEach { it.unhook() }
        hooks.clear()
    }

    /**
     * 初始化Hook
     * 
     * 安装所有必要的GNSS相关Hook，包括：
     * - GNSS状态回调注册方法
     * - GNSS NMEA回调注册方法
     * - GNSS测量监听器添加方法
     * - GNSS导航消息监听器添加方法
     * - GNSS天线信息监听器添加方法
     * 
     * @param lpparam 加载包参数
     */
    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val clazz = lpparam.classLoader.loadClass("com.android.server.location.gnss.GnssManagerService")

            // Hook 各种 GNSS 回调注册方法
            val methodsToHook = listOf(
                "registerGnssStatusCallback",
                "registerGnssNmeaCallback",
                "addGnssMeasurementsListener",
                "addGnssNavigationMessageListener",
                "addGnssAntennaInfoListener"
            )

            methodsToHook.forEach { methodName ->
                hooks.addAll(findAllMethods(clazz) {
                    name == methodName && isPublic
                }.hookBefore { param ->
                    try {
                        val packageName = when (methodName) {
                            "addGnssMeasurementsListener" -> param.args[2] as String
                            else -> param.args[1] as String
                        }
                        logI(TAG, String.format(MSG_IN_METHOD, methodName, packageName))

                        if (isInWhitelist(packageName)) {
                            logI(TAG, MSG_IN_WHITELIST)
                            param.result = null
                            return@hookBefore
                        }
                    } catch (e: Exception) {
                        logE(TAG, "$methodName Hook出错", e)
                    }
                })
            }

            logI(TAG, "GnssManagerServiceHookerS Hook已初始化")
        } catch (e: Exception) {
            logE(TAG, "初始化GnssManagerServiceHookerS失败", e)
        }
    }

    private fun isInWhitelist(packageName: String): Boolean {
        // 首先检查是否是系统关键包，这些包不应该被 Hook
        if (packageName in Constants.packagesShouldNotHide) {
            logI(TAG, "包 $packageName 在系统保护列表中，跳过")
            return false
        }
        
        // 检查是否是应用自身
        if (packageName == Constants.APP_PACKAGE_NAME) {
            logI(TAG, "跳过自身包: $packageName")
            return false
        }
        
        // 检查是否是 Google 服务相关包
        if (packageName == Constants.GMS_PACKAGE_NAME || packageName == Constants.GSF_PACKAGE_NAME) {
            logI(TAG, "Google服务包: $packageName，检查配置")
        }
        
        // 检查配置中是否启用了对该包的 Hook
        val isHookEnabled = HMAService.instance?.isHookEnabled(packageName) ?: false
        if (isHookEnabled) {
            logI(TAG, "包 $packageName 已启用位置Hook")
        } else {
            logI(TAG, "包 $packageName 不在白名单中")
        }
        
        return isHookEnabled
    }
}