package aaa.fucklocation.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialContainerTransform
import apk.fucklocation.R
import apk.fucklocation.databinding.FragmentTemplateSettingsBinding
import aaa.fucklocation.service.ConfigManager
import aaa.fucklocation.ui.util.navController
import aaa.fucklocation.ui.util.setupToolbar
import aaa.fucklocation.ui.viewmodel.TemplateSettingsViewModel
import kotlinx.coroutines.launch

class TemplateSettingsFragment : Fragment(R.layout.fragment_template_settings) {

    private val binding by viewBinding<FragmentTemplateSettingsBinding>()
    private val viewModel by viewModels<TemplateSettingsViewModel> {
        val args by navArgs<TemplateSettingsFragmentArgs>()
        TemplateSettingsViewModel.Factory(args)
    }

    private fun onBack(delete: Boolean) {
        viewModel.name = viewModel.name?.trim()
        if (viewModel.name != viewModel.originalName && (ConfigManager.hasTemplate(viewModel.name) || viewModel.name == null) || delete) {
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (delete) R.string.template_delete_title else R.string.template_name_invalid)
                .setMessage(if (delete) R.string.template_delete else R.string.template_name_already_exist)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    saveResult(delete)
                }
            if (delete) builder.setNegativeButton(android.R.string.cancel, null)
            builder.show()
        } else {
            saveResult(false)
        }
    }

    private fun saveResult(delete: Boolean) {
        setFragmentResult("template_settings", Bundle().apply {
            putString("name",if (delete) null else viewModel.name)
            putStringArrayList("appliedList", viewModel.appliedAppList.value)
            // Add new fields
            putString("longitude", viewModel.longitude)
            putString("latitude", viewModel.latitude)
            putString("eciNci", viewModel.eciNci)
            putString("pci", viewModel.pci)
            putString("tac", viewModel.tac)
            putString("earfcnNrarfcn", viewModel.earfcnNrarfcn)
            putString("bandwidth", viewModel.bandwidth)
        })
        navController.navigateUp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment
            scrimColor = Color.TRANSPARENT
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBack(false) }
        setupToolbar(
            toolbar = binding.toolbar,
            title = getString(R.string.title_template_settings),
            navigationIcon = R.drawable.baseline_arrow_back_24,
            navigationOnClick = { onBack(false) },
            menuRes = R.menu.menu_delete,
            onMenuOptionSelected = {
                onBack(true)
            }
        )

        binding.templateName.setText(viewModel.name)
        binding.templateName.addTextChangedListener { viewModel.name = it.toString() }
        
        // Setup new input fields
        binding.templateLongitude.setText(viewModel.longitude)
        binding.templateLongitude.addTextChangedListener { viewModel.longitude = it.toString() }
        
        binding.templateLatitude.setText(viewModel.latitude)
        binding.templateLatitude.addTextChangedListener { viewModel.latitude = it.toString() }
        
        binding.templateEciNci.setText(viewModel.eciNci)
        binding.templateEciNci.addTextChangedListener { viewModel.eciNci = it.toString() }
        
        binding.templatePci.setText(viewModel.pci)
        binding.templatePci.addTextChangedListener { viewModel.pci = it.toString() }
        
        binding.templateTac.setText(viewModel.tac)
        binding.templateTac.addTextChangedListener { viewModel.tac = it.toString() }
        
        binding.templateEarfcnNrarfcn.setText(viewModel.earfcnNrarfcn)
        binding.templateEarfcnNrarfcn.addTextChangedListener { viewModel.earfcnNrarfcn = it.toString() }
        
        binding.templateBandwidth.setText(viewModel.bandwidth)
        binding.templateBandwidth.addTextChangedListener { viewModel.bandwidth = it.toString() }
        
        binding.appliedApps.setOnClickListener {
            setFragmentResultListener("app_select") { _, bundle ->
                viewModel.appliedAppList.value = bundle.getStringArrayList("checked")!!
                clearFragmentResultListener("app_select")
            }
            val args = ScopeFragmentArgs(
                filterOnlyEnabled = true,
                isWhiteList = viewModel.isWhiteList,
                checked = viewModel.appliedAppList.value.toTypedArray()
            )
            navController.navigate(R.id.nav_scope, args.toBundle())
        }

        lifecycleScope.launch {
            viewModel.appliedAppList.collect {
                binding.appliedApps.text = String.format(getString(R.string.template_applied_count), it.size)
            }
        }
    }
}
