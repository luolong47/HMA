package aaa.fucklocation.xposed.hook

import android.content.pm.ResolveInfo
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import aaa.fucklocation.common.Constants
import aaa.fucklocation.xposed.*
import java.util.concurrent.atomic.AtomicReference

class PmsHookTarget28(private val service: HMAService) : IFrameworkHook {

    companion object {
        private const val TAG = "PmsHookTarget28"
    }

    private val hooks = mutableListOf<XC_MethodHook.Unhook>()
    private var lastFilteredApp: AtomicReference<String?> = AtomicReference(null)

    @Suppress("UNCHECKED_CAST")
    override fun load() {
        logI(TAG, "加载Hook")
        hooks += findMethod(service.pms::class.java, findSuper = true) {
            name == "filterAppAccessLPr" && parameterCount == 5
        }.hookBefore { param ->
            runCatching {
                val callingUid = param.args[1] as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookBefore
                val callingApps = Utils.binderLocalScope {
                    service.pms.getPackagesForUid(callingUid)
                } ?: return@hookBefore
                val packageSettings = param.args[0] ?: return@hookBefore
                val targetApp = Utils.getPackageNameFromPackageSettings(packageSettings)
                for (caller in callingApps) {
                    if (service.shouldHide(caller, targetApp)) {
                        param.result = true
                        service.filterCount++
                        val last = lastFilteredApp.getAndSet(caller)
                        if (last != caller) logI(TAG, "@filterAppAccessLPr 查询来自 $caller")
                        logD(TAG, "@filterAppAccessLPr 调用者: $callingUid $caller, 目标: $targetApp")
                        return@hookBefore
                    }
                }
            }.onFailure {
                logE(TAG, "发生致命错误，禁用Hook", it)
                unload()
            }
        }
        hooks += findMethod(service.pms::class.java, findSuper = true) {
            name == "applyPostResolutionFilter"
        }.hookAfter { param ->
            runCatching {
                val callingUid = param.args[3] as Int
                if (callingUid == Constants.UID_SYSTEM) return@hookAfter
                val callingApps = Utils.binderLocalScope {
                    service.pms.getPackagesForUid(callingUid)
                } ?: return@hookAfter
                val list = param.result as MutableCollection<ResolveInfo>
                val listToRemove = mutableSetOf<ResolveInfo>()
                for (resolveInfo in list) {
                    val targetApp = with(resolveInfo) {
                        activityInfo?.packageName ?: serviceInfo?.packageName ?: providerInfo?.packageName ?: resolvePackageName
                    }
                    for (caller in callingApps) {
                        if (service.shouldHide(caller, targetApp)) {
                            val last = lastFilteredApp.getAndSet(caller)
                            if (last != caller) logI(TAG, "@applyPostResolutionFilter 查询来自 $caller")
                            logD(TAG, "@applyPostResolutionFilter 调用者: $callingUid $caller, 目标: $targetApp")
                            listToRemove.add(resolveInfo)
                            break
                        }
                    }
                }
                list.removeAll(listToRemove)
            }.onFailure {
                logE(TAG, "发生致命错误，禁用Hook", it)
                unload()
            }
        }
    }

    override fun unload() {
        hooks.forEach(XC_MethodHook.Unhook::unhook)
        hooks.clear()
    }
}
