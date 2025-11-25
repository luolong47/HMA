package aaa.fucklocation.xposed.hook

import android.annotation.SuppressLint
import android.os.Build
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import aaa.fucklocation.xposed.HMAService
import aaa.fucklocation.xposed.logE
import aaa.fucklocation.xposed.logI
import aaa.fucklocation.common.Constants
import aaa.fucklocation.common.JsonConfig

/** 日志标签 */
private const val TAG = "CellLocationHookerS"

/**
 * 基站位置Hook类
 * 
 * 该类实现了IFrameworkHook接口，负责Hook Android 12及以上版本的基站位置相关方法，
 * 包括获取基站位置、请求网络位置更新等，以实现基站位置模拟
 */
@SuppressLint("PrivateApi")
class CellLocationHookerS : IFrameworkHook {
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
     * 安装所有必要的基站位置相关Hook，包括：
     * - PhoneInterfaceManager的getCellLocation方法
     * - 其他基站位置相关方法
     * 
     * @param lpparam 加载包参数
     */
    fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val phoneInterfaceManagerClass = lpparam.classLoader.loadClass("com.android.phone.PhoneInterfaceManager")

            // Hook getCellLocation 方法
                hooks.addAll(findAllMethods(phoneInterfaceManagerClass) {
                    name == "getCellLocation" && isPublic
                }.hookMethod {
                    after { param ->
                        try {
                            // 获取调用者包名
                            val packageName = getPackageNameFromBinder()
                            logI(TAG, "在getCellLocation中!调用者包名: $packageName")

                            if (isInWhitelist(packageName)) {
                                logI(TAG, "在白名单中!返回自定义基站数据信息")
                                
                                // 获取模板配置
                                val hmaService = HMAService.instance
                                if (hmaService != null) {
                                    val templateJson = hmaService.getTemplate(packageName)
                                    if (templateJson.isNotEmpty()) {
                                        val template = JsonConfig.parse("""{"templates":{"tmp":$templateJson}}""").templates["tmp"]
                                        if (template != null) {
                                            // 检查返回的CellIdentity类型
                                            val result = param.result
                                            if (result != null) {
                                                val className = result.javaClass.simpleName
                                                when {
                                                    className.contains("Lte") -> {
                                                        logI(TAG, "使用LTE网络...")
                                                        param.result = createFakeLteCellIdentity(result, template)
                                                    }
                                                    className.contains("Nr") -> {
                                                        logI(TAG, "使用NR网络...")
                                                        param.result = createFakeNrCellIdentity(result, template)
                                                    }
                                                    else -> {
                                                        logI(TAG, "不支持的网络类型: $className")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logE(TAG, "getCellLocation Hook出错", e)
                        }
                    }
                })

            // Hook getAllCellInfo 方法
            hooks.addAll(findAllMethods(phoneInterfaceManagerClass) {
                name == "getAllCellInfo" && isPublic
            }.hookAfter { param ->
                try {
                    // 获取调用者包名
                    val packageName = getPackageNameFromBinder()
                    logI(TAG, "在getAllCellInfo中!调用者包名: $packageName")

                    if (isInWhitelist(packageName)) {
                        logI(TAG, "在白名单中!返回空基站信息列表")
                        param.result = emptyList<Any>()
                    }
                } catch (e: Exception) {
                    logE(TAG, "getAllCellInfo Hook出错", e)
                }
            })

            // Hook requestCellInfoUpdate 方法
            hooks.addAll(findAllMethods(phoneInterfaceManagerClass) {
                name == "requestCellInfoUpdate" && isPublic
            }.hookBefore { param ->
                try {
                    // 获取调用者包名
                    val packageName = getPackageNameFromBinder()
                    logI(TAG, "在requestCellInfoUpdate中!调用者包名: $packageName")

                    if (isInWhitelist(packageName)) {
                        logI(TAG, "在白名单中!丢弃基站信息更新请求")
                        param.result = null
                        return@hookBefore
                    }
                } catch (e: Exception) {
                    logE(TAG, "requestCellInfoUpdate Hook出错", e)
                }
            })

            // Hook getNeighboringCellInfo 方法 (已弃用但可能仍在使用)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                hooks.addAll(findAllMethods(phoneInterfaceManagerClass) {
                    name == "getNeighboringCellInfo" && isPublic
                }.hookAfter { param ->
                    try {
                        // 获取调用者包名
                        val packageName = getPackageNameFromBinder()
                        logI(TAG, "在getNeighboringCellInfo中!调用者包名: $packageName")

                        if (isInWhitelist(packageName)) {
                            logI(TAG, "在白名单中!返回空邻近基站信息列表")
                            param.result = emptyList<Any>()
                        }
                    } catch (e: Exception) {
                        logE(TAG, "getNeighboringCellInfo Hook出错", e)
                    }
                })
            }

            logI(TAG, "CellLocationHookerS Hook已初始化")
        } catch (e: Exception) {
            logE(TAG, "初始化CellLocationHookerS失败", e)
        }
    }

    private fun getPackageNameFromBinder(): String {
        return try {
            // 获取调用者的 UID
            val binder = android.os.Binder.getCallingUid()
            // 通过 UID 获取包名
            val packageManagerClass = Class.forName("android.app.ActivityManager")
            val activityManager = packageManagerClass.getMethod("getService").invoke(null)
            val packagesForUid = activityManager.javaClass.getMethod("getPackagesForUid", Int::class.java)
                .invoke(activityManager, binder) as Array<String>?
            
            packagesForUid?.firstOrNull() ?: "unknown"
        } catch (e: Exception) {
            logE(TAG, "从Binder获取包名失败", e)
            "unknown"
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
            logI(TAG, "包 $packageName 已启用基站位置Hook")
        } else {
            logI(TAG, "包 $packageName 不在白名单中")
        }
        
        return isHookEnabled
    }

    private fun createFakeLteCellIdentity(originalCell: Any, template: JsonConfig.Template): Any {
        try {
            // 获取模板中的基站参数
            val eci = template.eciNci?.toIntOrNull() ?: 0
            val pci = template.pci?.toIntOrNull() ?: 0
            val tac = template.tac?.toIntOrNull() ?: 0
            val earfcn = template.earfcnNrarfcn?.toIntOrNull() ?: 0
            val bandwidth = template.bandwidth?.toIntOrNull() ?: 0

            // 使用反射创建新的CellIdentityLte对象
            val cellClass = originalCell.javaClass
            
            // 获取原始对象的属性
            val mccString = cellClass.getDeclaredMethod("getMccString").invoke(originalCell) as String?
            val mncString = cellClass.getDeclaredMethod("getMncString").invoke(originalCell) as String?
            val operatorAlphaLong = cellClass.getDeclaredMethod("getOperatorAlphaLong").invoke(originalCell) as String?
            val operatorAlphaShort = cellClass.getDeclaredMethod("getOperatorAlphaShort").invoke(originalCell) as String?
            
            // 尝试获取bands属性
            val bands = try {
                cellClass.getDeclaredMethod("getBands").invoke(originalCell) as IntArray?
            } catch (e: Exception) {
                null
            } ?: intArrayOf()
            
            // 创建新的CellIdentityLte对象
            val constructor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cellClass.getDeclaredConstructor(
                    Int::class.java,    // ci
                    Int::class.java,    // pci
                    Int::class.java,    // tac
                    Int::class.java,    // earfcn
                    IntArray::class.java,  // bands
                    Int::class.java,    // bandwidth
                    String::class.java, // mccStr
                    String::class.java, // mncStr
                    String::class.java, // alphal
                    String::class.java, // alphas
                )
            } else {
                cellClass.getDeclaredConstructor(
                    Int::class.java,    // ci
                    Int::class.java,    // pci
                    Int::class.java,    // tac
                    Int::class.java,    // earfcn
                    Int::class.java,    // bandwidth
                    String::class.java, // mccStr
                    String::class.java, // mncStr
                    String::class.java, // alphal
                    String::class.java, // alphas
                )
            }
            
            constructor.isAccessible = true
            
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                constructor.newInstance(
                    eci,  // ECI
                    pci,  // PCI
                    tac,  // TAC
                    earfcn,  // EARFCN
                    bands,  // bands
                    bandwidth,  // bandwidth
                    mccString,
                    mncString,
                    operatorAlphaLong,
                    operatorAlphaShort
                )
            } else {
                constructor.newInstance(
                    eci,  // ECI
                    pci,  // PCI
                    tac,  // TAC
                    earfcn,  // EARFCN
                    bandwidth,  // bandwidth
                    mccString,
                    mncString,
                    operatorAlphaLong,
                    operatorAlphaShort
                )
            }
        } catch (e: Exception) {
            logE(TAG, "创建虚假LTE基站标识失败", e)
            return originalCell
        }
    }

    private fun createFakeNrCellIdentity(originalCell: Any, template: JsonConfig.Template): Any {
        try {
            // 获取模板中的基站参数
            val nci = template.eciNci?.toLongOrNull() ?: 0L
            val pci = template.pci?.toIntOrNull() ?: 0
            val tac = template.tac?.toIntOrNull() ?: 0
            val nrarfcn = template.earfcnNrarfcn?.toIntOrNull() ?: 0

            // 使用反射创建新的CellIdentityNr对象
            val cellClass = originalCell.javaClass
            
            // 获取原始对象的属性
            val mccString = cellClass.getDeclaredMethod("getMccString").invoke(originalCell) as String?
            val mncString = cellClass.getDeclaredMethod("getMncString").invoke(originalCell) as String?
            val operatorAlphaLong = cellClass.getDeclaredMethod("getOperatorAlphaLong").invoke(originalCell) as String?
            val operatorAlphaShort = cellClass.getDeclaredMethod("getOperatorAlphaShort").invoke(originalCell) as String?
            
            // 尝试获取bands属性
            val bands = try {
                cellClass.getDeclaredMethod("getBands").invoke(originalCell) as IntArray?
            } catch (e: Exception) {
                null
            } ?: intArrayOf()
            
            // 创建新的CellIdentityNr对象
            val constructor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cellClass.getDeclaredConstructor(
                    Int::class.java,    // pci
                    Int::class.java,    // tac
                    Int::class.java,    // nrArfcn
                    IntArray::class.java,  // bands
                    String::class.java, // mccStr
                    String::class.java, // mncStr
                    Long::class.java,   // nci
                    String::class.java, // alphal
                    String::class.java, // alphas
                )
            } else {
                cellClass.getDeclaredConstructor(
                    Int::class.java,    // pci
                    Int::class.java,    // tac
                    Int::class.java,    // nrArfcn
                    String::class.java, // mccStr
                    String::class.java, // mncStr
                    Long::class.java,   // nci
                    String::class.java, // alphal
                    String::class.java, // alphas
                )
            }
            
            constructor.isAccessible = true
            
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                constructor.newInstance(
                    pci,  // PCI
                    tac,  // TAC
                    nrarfcn,  // NRARFCN
                    bands,  // bands
                    mccString,
                    mncString,
                    nci,  // NCI
                    operatorAlphaLong,
                    operatorAlphaShort
                )
            } else {
                constructor.newInstance(
                    pci,  // PCI
                    tac,  // TAC
                    nrarfcn,  // NRARFCN
                    mccString,
                    mncString,
                    nci,  // NCI
                    operatorAlphaLong,
                    operatorAlphaShort
                )
            }
        } catch (e: Exception) {
            logE(TAG, "创建虚假NR基站标识失败", e)
            return originalCell
        }
    }
}