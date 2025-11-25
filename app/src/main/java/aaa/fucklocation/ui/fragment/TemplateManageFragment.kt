package aaa.fucklocation.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.clearFragmentResultListener
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.transition.MaterialContainerTransform
import apk.fucklocation.R
import apk.fucklocation.databinding.FragmentTemplateManageBinding
import aaa.fucklocation.common.JsonConfig
import aaa.fucklocation.service.ConfigManager
import aaa.fucklocation.ui.adapter.TemplateAdapter
import aaa.fucklocation.ui.util.navController
import aaa.fucklocation.ui.util.setupToolbar

class TemplateManageFragment : Fragment(R.layout.fragment_template_manage) {

    private val binding by viewBinding<FragmentTemplateManageBinding>()
    private val adapter = TemplateAdapter(this::navigateToSettings)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment
            scrimColor = Color.TRANSPARENT
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar(
            toolbar = binding.toolbar,
            title = getString(R.string.title_template_manage),
            navigationIcon = R.drawable.baseline_arrow_back_24,
            navigationOnClick = { navController.navigateUp() }
        )
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        binding.newTemplate.setOnClickListener {
            navigateToSettings(ConfigManager.TemplateInfo(null, false))
        }
        binding.templateList.layoutManager = LinearLayoutManager(context)
        binding.templateList.adapter = adapter
    }

    private fun navigateToSettings(info: ConfigManager.TemplateInfo) {
        setFragmentResultListener("template_settings") { _, bundle ->
            fun deal() {
                var name = bundle.getString("name")
                val appliedList = bundle.getStringArrayList("appliedList")!!
                // Get new fields
                val longitude = bundle.getString("longitude")
                val latitude = bundle.getString("latitude")
                val eciNci = bundle.getString("eciNci")
                val pci = bundle.getString("pci")
                val tac = bundle.getString("tac")
                val earfcnNrarfcn = bundle.getString("earfcnNrarfcn")
                val bandwidth = bundle.getString("bandwidth")
                
                if (info.name == null) { // New template
                    if (name.isNullOrEmpty()) return
                    ConfigManager.updateTemplate(name, JsonConfig.Template(
                        isWhitelist = info.isWhiteList,
                        appList = emptySet(), // No target apps anymore
                        longitude = longitude,
                        latitude = latitude,
                        eciNci = eciNci,
                        pci = pci,
                        tac = tac,
                        earfcnNrarfcn = earfcnNrarfcn,
                        bandwidth = bandwidth
                    ))
                    ConfigManager.updateTemplateAppliedApps(name, appliedList)
                } else {                 // Existing template
                    if (name == null) {
                        ConfigManager.deleteTemplate(info.name)
                    } else {
                        if (name.isEmpty()) name = info.name
                        if (name != info.name) ConfigManager.renameTemplate(info.name, name)
                        // Get existing template to preserve appList
                        val existingTemplate = ConfigManager.getTemplateTargetAppList(info.name)
                        ConfigManager.updateTemplate(name, JsonConfig.Template(
                            isWhitelist = info.isWhiteList,
                            appList = existingTemplate.toSet(), // Preserve existing appList
                            longitude = longitude,
                            latitude = latitude,
                            eciNci = eciNci,
                            pci = pci,
                            tac = tac,
                            earfcnNrarfcn = earfcnNrarfcn,
                            bandwidth = bandwidth
                        ))
                        ConfigManager.updateTemplateAppliedApps(name, appliedList)
                    }
                }
            }
            deal()
            adapter.updateList()
            clearFragmentResultListener("template_settings")
        }

        val args = TemplateSettingsFragmentArgs(info.name, info.isWhiteList)
        navController.navigate(R.id.nav_template_settings, args.toBundle())
    }
}
