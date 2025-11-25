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

/** 日志标签 */
private const val TAG = "HMA-XposedEntry"

/**
 * Xposed入口类
 * 
 * 该类实现了IXposedHookZygoteInit和IXposedHookLoadPackage接口，
 * 负责在系统启动时初始化Hook，并根据不同的包名应用不同的Hook策略
 */
@Suppress("unused")
class XposedEntry : IXposedHookZygoteInit, IXposedHookLoadPackage {

    /**
     * 初始化Zygote进程
     * 
     * @param startupParam 启动参数
     */
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXHelperInit.initZygote(startupParam)
    }

    /**
     * 处理包加载事件
     * 
     * 根据不同的包名应用不同的Hook策略：
     * - 主应用包：标记为已Hook
     * - 系统包：注册用户服务并初始化位置Hook
     * - 电话包：初始化基站定位Hook
     * 
     * @param lpparam 加载包参数
     */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == Constants.APP_PACKAGE_NAME) {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            hookAllConstructorAfter("aaa.fucklocation.MyApp") {
                getFieldByDesc("Laaa/fucklocation/MyApp;->isHooked:Z").setBoolean(it.thisObject, true)
            }
        } else if (lpparam.packageName == "android") {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            logI(TAG, "Hook入口")

            var serviceManagerHook: XC_MethodHook.Unhook? = null
            serviceManagerHook = findMethod("android.os.ServiceManager") {
                name == "addService"
            }.hookBefore { param ->
                if (param.args[0] == "package") {
                    serviceManagerHook?.unhook()
                    val pms = param.args[1] as IPackageManager
                    logD(TAG, "获取到包管理服务: $pms")
                    thread {
                        runCatching {
                            UserService.register(pms)
                            logI(TAG, "用户服务已启动")
                        }.onFailure {
                            logE(TAG, "系统服务崩溃", it)
                        }
                    }
                }
            }

            // 初始化 Location Hook
            initLocationHooks(lpparam)
        } else if (lpparam.packageName == "com.android.phone") {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            logI(TAG, "电话包Hook入口")
            
            // 初始化基站定位Hook
            logI(TAG, "为电话包初始化基站定位Hook")
            CellLocationHookerS().initHooks(lpparam)
        }
    }

    /**
     * 初始化位置相关的Hook
     * 
     * 根据Android版本初始化相应的位置Hook，包括：
     * - LocationHookerAfterS：Android 12+的位置Hook
     * - GnssManagerServiceHookerS：GNSS管理服务Hook
     * 
     * @param lpparam 加载包参数
     */
    private fun initLocationHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        logI(TAG, "为Android ${Build.VERSION.SDK_INT}初始化位置Hook")

        // 由于最低API为31，只支持Android 12+
        logI(TAG, "检测到Android 12+，应用LocationHookerAfterS")
        LocationHookerAfterS().initHooks(lpparam)
        GnssManagerServiceHookerS().initHooks(lpparam)
    }
}
