package aaa.fucklocation.xposed.hook

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.util.ArrayMap
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import aaa.fucklocation.xposed.HMAService
import aaa.fucklocation.xposed.Utils
import aaa.fucklocation.xposed.logD
import aaa.fucklocation.xposed.logE
import aaa.fucklocation.xposed.logI
import aaa.fucklocation.xposed.logW
import aaa.fucklocation.common.Constants
import aaa.fucklocation.common.JsonConfig
import kotlin.random.Random
import java.lang.reflect.Method

/** 日志标签 */
private const val TAG = "LocationHookerAfterS"

/**
 * Android 12+位置Hook类
 * 
 * 该类实现了IFrameworkHook接口，负责Hook Android 12及以上版本的位置相关方法，
 * 包括位置报告、位置获取、GNSS回调注册等功能，以实现位置模拟
 */
@SuppressLint("PrivateApi")
class LocationHookerAfterS : IFrameworkHook {
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
     * 安装所有必要的位置相关Hook，包括：
     * - LocationProviderManager的onReportLocation方法
     * - LocationManagerService的getLastLocation方法
     * - LocationManagerService的getCurrentLocation方法
     * - GNSS状态和NMEA回调注册方法
     * - 地理围栏请求方法
     * 
     * @param lpparam 加载包参数
     */
    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook LocationProviderManager 的 onReportLocation 方法
            val providerManagerClass = lpparam.classLoader.loadClass("com.android.server.location.provider.LocationProviderManager")
            hooks.addAll(findAllMethods(providerManagerClass) {
                name == "onReportLocation"
            }.hookMethod {
                before { param ->
                    hookOnReportLocation(providerManagerClass, param)
                }
            })

            // Hook LocationManagerService 的 getLastLocation 方法
            val locationManagerServiceClass = lpparam.classLoader.loadClass("com.android.server.location.LocationManagerService")
            hooks.addAll(findAllMethods(locationManagerServiceClass) {
                name == "getLastLocation" && isPublic
            }.hookMethod {
                after {
                    try {
                        val targetParam: Any = if (it.args[0] is String) it.args[2] else it.args[1]
                        val packageName = getPackageNameFromIdentity(targetParam)
                        logI(TAG, "in getLastLocation! Caller package name: $packageName")

                        if (isInWhitelist(packageName)) {
                            logI(TAG, "in whitelist! Return custom location")
                            val fakeLocation = createFakeLocation(it.result as? Location, packageName)
                            it.result = fakeLocation
                        }
                    } catch (e: Exception) {
                        logE(TAG, "Error in getLastLocation hook", e)
                    }
                }
            })

            // Hook LocationManagerService 的 getCurrentLocation 方法
            hooks.addAll(findAllMethods(locationManagerServiceClass) {
                name == "getCurrentLocation" && isPublic
            }.hookMethod {
                after { param ->
                    try {
                        val targetParam: Any = if (param.args[0] is String) param.args[0] else param.args[1]
                        val packageName = getPackageNameFromIdentity(targetParam)
                        logI(TAG, "in getCurrentLocation! Caller package name: $packageName")

                        if (isInWhitelist(packageName)) {
                            logI(TAG, "in whiteList! Inject null...")
                            param.result = null
                        }
                    } catch (e: Exception) {
                        logE(TAG, "Error in getCurrentLocation hook", e)
                    }
                }
            })

            // Hook GNSS 回调注册
            hooks.addAll(findAllMethods(locationManagerServiceClass) {
                name == "registerGnssStatusCallback" && isPublic
            }.hookBefore { param ->
                val packageName = param.args[1] as String
                logI(TAG, "in registerGnssStatusCallback (S)! Caller package name: $packageName")

                if (isInWhitelist(packageName)) {
                    logI(TAG, "in whiteList! Dropping register request...")
                    param.result = null
                    return@hookBefore
                }
            })

            hooks.addAll(findAllMethods(locationManagerServiceClass) {
                name == "registerGnssNmeaCallback" && isPublic
            }.hookBefore { param ->
                val packageName = param.args[1] as String
                logI(TAG, "in registerGnssNmeaCallback (S)! Caller package name: $packageName")

                if (isInWhitelist(packageName)) {
                    logI(TAG, "in whiteList! Dropping register request...")
                    param.result = null
                    return@hookBefore
                }
            })

            hooks.addAll(findAllMethods(locationManagerServiceClass) {
                name == "requestGeofence" && isPublic
            }.hookBefore { param ->
                val packageName = param.args[2] as String
                logI(TAG, "in requestGeofence (S)! Caller package name: $packageName")

                if (isInWhitelist(packageName)) {
                    logI(TAG, "in whiteList! Dropping register request...")
                    param.result = null
                    return@hookBefore
                }
            })

            logI(TAG, "LocationHookerAfterS hooks initialized")
        } catch (e: Exception) {
            logE(TAG, "Failed to initialize LocationHookerAfterS", e)
        }
    }

    /**
     * Hook位置报告方法
     * 
     * 拦截位置报告，对白名单中的应用应用伪造位置
     * 
     * @param clazz LocationProviderManager类
     * @param param 方法Hook参数
     */
    private fun hookOnReportLocation(clazz: Class<*>, param: XC_MethodHook.MethodHookParam) {
        logI(TAG, "in onReportLocation!")

        try {
            val mRegistrations = findField(clazz, true) {
                name == "mRegistrations"
            }
            mRegistrations.isAccessible = true

            val registrations = mRegistrations.get(param.thisObject) as ArrayMap<*, *>
            val newRegistrations = ArrayMap<Any, Any>()

            registrations.forEach { registration ->
                val callerIdentity = findField(registration.value.javaClass, true) {
                    name == "mIdentity"
                }.get(registration.value)

                val packageName = getPackageNameFromIdentity(callerIdentity)

                if (!isInWhitelist(packageName)) {
                    newRegistrations[registration.key] = registration.value
                } else {
                    val value = registration.value
                    val locationResult = param.args[0]

                    val mLocationsField = findField(locationResult.javaClass, true) {
                        name == "mLocations" && isPrivate
                    }
                    mLocationsField.isAccessible = true
                    val mLocations = mLocationsField.get(locationResult) as ArrayList<*>

                    val originLocation = if (mLocations.isNotEmpty()) mLocations[0] as Location else Location(LocationManager.GPS_PROVIDER)
                    val fakeLocation = createFakeLocation(originLocation, packageName)

                    mLocationsField.set(locationResult, arrayListOf(fakeLocation))

                    val method = findMethod(value.javaClass, true) {
                        name == "acceptLocationChange"
                    }
                    val operation = method.invoke(value, locationResult)

                    findMethod(value.javaClass, true) {
                        name == "executeOperation"
                    }.invoke(value, operation)
                }
            }

            mRegistrations.set(param.thisObject, newRegistrations)
        } catch (e: Exception) {
            logE(TAG, "Error in hookOnReportLocation", e)
        }
    }

    /**
     * 从调用者身份获取包名
     * 
     * 根据不同类型的身份对象提取包名信息
     * 
     * @param identity 调用者身份对象
     * @return 包名字符串，如果无法获取则返回"unknown"
     */
    private fun getPackageNameFromIdentity(identity: Any?): String {
        // 这里应该实现从调用者身份获取包名的逻辑
        // 简化实现，实际应该根据不同 Android 版本使用不同的方法
        return try {
            when {
                identity is String -> identity
                identity != null -> {
                    val packageNameField = identity.javaClass.getDeclaredField("packageName")
                    packageNameField.isAccessible = true
                    packageNameField.get(identity) as String
                }
                else -> "unknown"
            }
        } catch (e: Exception) {
            logE(TAG, "Failed to get package name from identity", e)
            "unknown"
        }
    }

    /**
     * 检查包是否在白名单中
     * 
     * 判断指定包是否应该被Hook，包括检查系统保护包、应用自身包和用户配置
     * 
     * @param packageName 要检查的包名
     * @return 如果包在白名单中返回true，否则返回false
     */
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
            logD(TAG, "Package $packageName is not in whitelist")
        }
        
        return isHookEnabled
    }

    /**
     * 创建伪造位置
     * 
     * 根据配置的模板创建伪造位置，保留原始位置的其他属性，
     * 并添加随机偏移以增加真实性
     * 
     * @param originLocation 原始位置，可为null
     * @param packageName 请求位置的包名
     * @return 伪造的位置对象
     */
    private fun createFakeLocation(originLocation: Location?, packageName: String): Location {
        // 这里应该实现创建伪造位置的逻辑
        // 通过 AIDL 从配置中读取伪造位置
        val fakeLocation = Location(originLocation?.provider ?: LocationManager.FUSED_PROVIDER)
        
        // 通过 AIDL 获取配置的模板
        var latitude = 39.9042 // 默认北京纬度
        var longitude = 116.4074 // 默认北京经度
        
        try {
            // 获取 HMAService 实例
            val hmaService = HMAService.instance
            if (hmaService != null) {
                val templateJson = hmaService.getTemplate(packageName)
                if (templateJson.isNotEmpty()) {
                    val template = JsonConfig.parse("""{"templates":{"tmp":$templateJson}}""").templates["tmp"]
                    if (template != null) {
                        latitude = template.latitude?.toDoubleOrNull() ?: 39.9042
                        longitude = template.longitude?.toDoubleOrNull() ?: 116.4074
                        logI(TAG, "Got location from config for $packageName: lat=$latitude, lon=$longitude")
                    }
                } else {
                    logW(TAG, "Template is empty for $packageName, using default location")
                }
            } else {
                logW(TAG, "HMAService instance is null, using default location")
            }
        } catch (e: Exception) {
            logE(TAG, "Failed to get template from config", e)
        }
        
        // 设置伪造的经纬度，添加一些随机偏移
        fakeLocation.latitude = latitude + Random.nextDouble(-0.01, 0.01)
        fakeLocation.longitude = longitude + Random.nextDouble(-0.01, 0.01)
        
        // 保留原始位置的其他属性
        originLocation?.let { original ->
            fakeLocation.time = original.time
            fakeLocation.accuracy = original.accuracy
            fakeLocation.bearing = original.bearing
            fakeLocation.elapsedRealtimeNanos = original.elapsedRealtimeNanos
            
            // API 28+ 的属性
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    fakeLocation.bearingAccuracyDegrees = original.bearingAccuracyDegrees
                } catch (e: Exception) {
                    logE(TAG, "Failed to set bearingAccuracyDegrees", e)
                }
            }
            
            // API 29+ 的属性
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    fakeLocation.elapsedRealtimeUncertaintyNanos = original.elapsedRealtimeUncertaintyNanos
                } catch (e: Exception) {
                    logE(TAG, "Failed to set elapsedRealtimeUncertaintyNanos", e)
                }
                
                try {
                    fakeLocation.verticalAccuracyMeters = original.verticalAccuracyMeters
                } catch (e: Exception) {
                    logE(TAG, "Failed to set verticalAccuracyMeters", e)
                }
            }
        } ?: run {
            fakeLocation.time = System.currentTimeMillis() - Random.nextLong(100, 10000)
        }
        
        fakeLocation.altitude = 0.0
        
        // API 31+ 的属性
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                fakeLocation.isMock = false
            } catch (e: Exception) {
                logE(TAG, "Failed to set isMock", e)
            }
        }
        
        fakeLocation.speed = 0F
        
        // API 26+ 的属性
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                fakeLocation.speedAccuracyMetersPerSecond = 0F
            } catch (e: Exception) {
                logE(TAG, "Failed to set speedAccuracyMetersPerSecond", e)
            }
        }
        
        // 对于 Android 11 及以上版本，尝试设置 isFromMockProvider
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val setIsFromMockProviderMethod = fakeLocation.javaClass.getDeclaredMethod("setIsFromMockProvider", Boolean::class.javaPrimitiveType)
                setIsFromMockProviderMethod.isAccessible = true
                setIsFromMockProviderMethod.invoke(fakeLocation, false)
            } catch (e: Exception) {
                logE(TAG, "Not possible to set mock flag", e)
            }
        }
        
        return fakeLocation
    }
}