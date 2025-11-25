package aaa.fucklocation.xposed.hook

import android.os.Build
import androidx.annotation.RequiresApi
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import aaa.fucklocation.common.Constants
import aaa.fucklocation.xposed.*
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(Build.VERSION_CODES.R)
class PmsHookTarget30(private val service: HMAService) : IFrameworkHook {

    companion object {
        private const val TAG = "PmsHookTarget30"
    }

    private var hook: XC_MethodHook.Unhook? = null
    private var lastFilteredApp: AtomicReference<String?> = AtomicReference(null)

    override fun load() {
        logI(TAG, "加载Hook")
        hook = findMethod("com.android.server.pm.AppsFilter") {
            name == "shouldFilterApplication"
        }.hookBefore { param ->
            runCatching {
                val callingUid = param.args[0] as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val callingApps = Utils.binderLocalScope {
                    service.pms.getPackagesForUid(callingUid)
                } ?: return@hookBefore
                val targetApp = Utils.getPackageNameFromPackageSettings(param.args[2])
                for (caller in callingApps) {
                    if (service.shouldHide(caller, targetApp)) {
                        param.result = true
                        service.filterCount++
                        val last = lastFilteredApp.getAndSet(caller)
                        if (last != caller) logI(TAG, "@shouldFilterApplication: 查询来自 $caller")
                        logD(TAG, "@shouldFilterApplication 调用者: $callingUid $caller, 目标: $targetApp")
                        return@hookBefore
                    }
                }
            }.onFailure {
                logE(TAG, "发生致命错误，禁用Hook", it)
                unload()
            }
        }
    }

    override fun unload() {
        hook?.unhook()
        hook = null
    }
}
