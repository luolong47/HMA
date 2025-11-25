package aaa.fucklocation.xposed

import android.annotation.SuppressLint
import android.content.pm.IPackageManager
import android.os.Build
import android.os.ServiceManager
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import aaa.fucklocation.common.Constants
import aaa.fucklocation.xposed.hook.IFrameworkHook
import aaa.fucklocation.xposed.hook.LocationHookerAfterS
import aaa.fucklocation.xposed.hook.GnssManagerServiceHookerS
import aaa.fucklocation.xposed.hook.CellLocationHookerS
import kotlin.concurrent.thread

private const val TAG = "HMA-XposedEntry"

@Suppress("unused")
class XposedEntry : IXposedHookZygoteInit, IXposedHookLoadPackage {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXHelperInit.initZygote(startupParam)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == Constants.APP_PACKAGE_NAME) {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            hookAllConstructorAfter("aaa.fucklocation.MyApp") {
                getFieldByDesc("Laaa/fucklocation/MyApp;->isHooked:Z").setBoolean(it.thisObject, true)
            }
        } else if (lpparam.packageName == "android") {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            logI(TAG, "Hook entry")

            var serviceManagerHook: XC_MethodHook.Unhook? = null
            serviceManagerHook = findMethod("android.os.ServiceManager") {
                name == "addService"
            }.hookBefore { param ->
                if (param.args[0] == "package") {
                    serviceManagerHook?.unhook()
                    val pms = param.args[1] as IPackageManager
                    logD(TAG, "Got pms: $pms")
                    thread {
                        runCatching {
                            UserService.register(pms)
                            logI(TAG, "User service started")
                        }.onFailure {
                            logE(TAG, "System service crashed", it)
                        }
                    }
                }
            }

            // 初始化 Location Hook
            initLocationHooks(lpparam)
        } else if (lpparam.packageName == "com.android.phone") {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            logI(TAG, "Hook entry for phone")
            
            // 初始化基站定位Hook
            logI(TAG, "Initializing Cell Location Hooks for phone package")
            CellLocationHookerS().initHooks(lpparam)
        }
    }

    private fun initLocationHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        logI(TAG, "Initializing Location Hooks for Android ${Build.VERSION.SDK_INT}")

        // 由于最低API为31，只支持Android 12+
        logI(TAG, "Android 12+ detected, applying LocationHookerAfterS")
        LocationHookerAfterS().initHooks(lpparam)
        GnssManagerServiceHookerS().initHooks(lpparam)
    }
}
