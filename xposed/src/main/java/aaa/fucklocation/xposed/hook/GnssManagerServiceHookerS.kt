package aaa.fucklocation.xposed.hook

import android.annotation.SuppressLint
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import aaa.fucklocation.xposed.HMAService
import aaa.fucklocation.xposed.logE
import aaa.fucklocation.xposed.logI
import aaa.fucklocation.common.Constants

private const val TAG = "GnssManagerServiceHookerS"

@SuppressLint("PrivateApi")
class GnssManagerServiceHookerS : IFrameworkHook {
    private var hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        // 在这里实现加载逻辑，但实际使用 initHooks
    }

    override fun unload() {
        hooks.forEach { it.unhook() }
        hooks.clear()
    }

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
                        logI(TAG, "in $methodName! Caller package name: $packageName")

                        if (isInWhitelist(packageName)) {
                            logI(TAG, "in whiteList! Dropping register request...")
                            param.result = null
                            return@hookBefore
                        }
                    } catch (e: Exception) {
                        logE(TAG, "Error in $methodName hook", e)
                    }
                })
            }

            logI(TAG, "GnssManagerServiceHookerS hooks initialized")
        } catch (e: Exception) {
            logE(TAG, "Failed to initialize GnssManagerServiceHookerS", e)
        }
    }

    private fun isInWhitelist(packageName: String): Boolean {
        // 首先检查是否是系统关键包，这些包不应该被 Hook
        if (packageName in Constants.packagesShouldNotHide) {
            logI(TAG, "Package $packageName is in system protected list, skipping")
            return false
        }
        
        // 检查是否是应用自身
        if (packageName == Constants.APP_PACKAGE_NAME) {
            logI(TAG, "Skipping self package: $packageName")
            return false
        }
        
        // 检查是否是 Google 服务相关包
        if (packageName == Constants.GMS_PACKAGE_NAME || packageName == Constants.GSF_PACKAGE_NAME) {
            logI(TAG, "Google service package: $packageName, checking config")
        }
        
        // 检查配置中是否启用了对该包的 Hook
        val isHookEnabled = HMAService.instance?.isHookEnabled(packageName) ?: false
        if (isHookEnabled) {
            logI(TAG, "Package $packageName is enabled for location hooking")
        } else {
            logI(TAG, "Package $packageName is not in whitelist")
        }
        
        return isHookEnabled
    }
}