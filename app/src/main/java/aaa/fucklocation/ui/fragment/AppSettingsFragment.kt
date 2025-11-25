package aaa.fucklocation.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.clearFragmentResultListener
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.preference.*
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.fucklocation.R
import apk.fucklocation.databinding.FragmentSettingsBinding
import aaa.fucklocation.common.JsonConfig
import aaa.fucklocation.service.ConfigManager
import aaa.fucklocation.ui.util.navController
import aaa.fucklocation.ui.util.setupToolbar
import aaa.fucklocation.ui.viewmodel.AppSettingsViewModel
import aaa.fucklocation.util.PackageHelper

class AppSettingsFragment : Fragment(R.layout.fragment_settings) {

    private val binding by viewBinding<FragmentSettingsBinding>()
    private val viewModel by viewModels<AppSettingsViewModel>() {
        val args by navArgs<AppSettingsFragmentArgs>()
        val cfg = ConfigManager.getAppConfig(args.packageName)
        val pack = if (cfg != null) AppSettingsViewModel.Pack(args.packageName, true, cfg)
        else AppSettingsViewModel.Pack(args.packageName, false, JsonConfig.AppConfig())
        AppSettingsViewModel.Factory(pack)
    }

    private fun saveConfig() {
        if (!viewModel.pack.enabled) ConfigManager.setAppConfig(viewModel.pack.app, null)
        else ConfigManager.setAppConfig(viewModel.pack.app, viewModel.pack.config)
    }

    private fun onBack() {
        saveConfig()
        navController.navigateUp()
    }

    override fun onPause() {
        super.onPause()
        saveConfig()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveConfig()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBack() }
        setupToolbar(
            toolbar = binding.toolbar,
            title = getString(R.string.title_app_settings),
            navigationIcon = R.drawable.baseline_arrow_back_24,
            navigationOnClick = { onBack() }
        )

        if (childFragmentManager.findFragmentById(R.id.settings_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_container, AppPreferenceFragment())
                .commit()
        }
    }

    class AppPreferenceDataStore(private val pack: AppSettingsViewModel.Pack) : PreferenceDataStore() {

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return when (key) {
                "enableHide" -> pack.enabled
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }

        override fun putBoolean(key: String, value: Boolean) {
            when (key) {
                "enableHide" -> pack.enabled = value
                else -> throw IllegalArgumentException("Invalid key: $key")
            }
        }
    }

    class AppPreferenceFragment : PreferenceFragmentCompat() {

        private val parent get() = requireParentFragment() as AppSettingsFragment
        private val pack get() = parent.viewModel.pack

        private fun updateApplyTemplates() {
            findPreference<Preference>("applyTemplates")?.title =
                getString(R.string.app_template_using, pack.config.applyTemplates.size)
        }



        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = AppPreferenceDataStore(pack)
            setPreferencesFromResource(R.xml.app_settings, rootKey)
            findPreference<Preference>("appInfo")?.let {
                it.icon = PackageHelper.loadAppIcon(pack.app).toDrawable(resources)
                it.title = PackageHelper.loadAppLabel(pack.app)
                it.summary = PackageHelper.loadPackageInfo(pack.app).packageName
            }

            findPreference<Preference>("applyTemplates")?.setOnPreferenceClickListener {
                val templates = ConfigManager.getTemplateList().mapNotNull {
                    it.name
                }.toTypedArray()
                val checked = templates.map {
                    pack.config.applyTemplates.contains(it)
                }.toBooleanArray()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.app_choose_template)
                    .setMultiChoiceItems(templates, checked) { _, i, value -> checked[i] = value }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        pack.config.applyTemplates = templates.mapIndexedNotNullTo(mutableSetOf()) { i, name ->
                            if (checked[i]) name else null
                        }
                        updateApplyTemplates()
                    }
                    .show()
                true
            }

            updateApplyTemplates()
        }
    }
}
