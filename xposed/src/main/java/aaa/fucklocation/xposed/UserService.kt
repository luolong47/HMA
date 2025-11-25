package aaa.fucklocation.xposed

import android.app.ActivityManagerHidden
import android.content.AttributionSource
import android.content.pm.IPackageManager
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import aaa.fucklocation.common.BuildConfig
import aaa.fucklocation.common.Constants
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.adapter.UidObserverAdapter

object UserService {

    private const val TAG = "HMA-UserService"

    private var appUid = 0

    private val uidObserver = object : UidObserverAdapter() {
        override fun onUidActive(uid: Int) {
            if (uid != appUid) return
            try {
                val provider = ActivityManagerApis.getContentProviderExternal(Constants.PROVIDER_AUTHORITY, 0, null, null)
                if (provider == null) {
                    logE(TAG, "获取提供者失败")
                    return
                }
                val extras = Bundle()
                extras.putBinder("binder", HMAService.instance)
                val reply = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val attr = AttributionSource.Builder(1000).setPackageName("android").build()
                    provider.call(attr, Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                    provider.call("android", null, Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    provider.call("android", Constants.PROVIDER_AUTHORITY, "", null, extras)
                } else {
                    provider.call("android", "", null, extras)
                }
                if (reply == null) {
                    logE(TAG, "发送绑定器到应用失败")
                    return
                }
                logI(TAG, "已发送绑定器到应用")
            } catch (e: Throwable) {
                logE(TAG, "UID激活时出错", e)
            }
        }
    }

    fun register(pms: IPackageManager) {
        logI(TAG, "初始化HMA服务 - 版本 ${BuildConfig.SERVICE_VERSION}")
        val service = HMAService(pms)
        appUid = Utils.getPackageUidCompat(service.pms, Constants.APP_PACKAGE_NAME, 0, 0)
        val appPackage = Utils.getPackageInfoCompat(service.pms, Constants.APP_PACKAGE_NAME, 0, 0)
        if (!Utils.verifyAppSignature(appPackage.applicationInfo?.sourceDir.toString())) {
            logE(TAG, "致命错误：应用签名不匹配")
            return
        }
        logD(TAG, "客户端UID: $appUid")
        logI(TAG, "注册观察者")

        waitSystemService("activity")
        ActivityManagerApis.registerUidObserver(
            uidObserver,
            ActivityManagerHidden.UID_OBSERVER_ACTIVE,
            ActivityManagerHidden.PROCESS_STATE_UNKNOWN,
            null
        )
    }

    private fun waitSystemService(name: String) {
        while (ServiceManager.getService(name) == null) {
            Thread.sleep(1000)
        }
    }
}
