package aaa.fucklocation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import aaa.fucklocation.service.ConfigManager
import aaa.fucklocation.ui.fragment.TemplateSettingsFragmentArgs
import kotlinx.coroutines.flow.MutableStateFlow

class TemplateSettingsViewModel(
    val originalName: String?,
    val isWhiteList: Boolean,
    var name: String?
) : ViewModel() {

    class Factory(private val args: TemplateSettingsFragmentArgs) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TemplateSettingsViewModel::class.java)) {
                val viewModel = TemplateSettingsViewModel(args.name, args.isWhiteList, args.name)
                args.name?.let {
                    viewModel.appliedAppList.value = ConfigManager.getTemplateAppliedAppList(it)
                    viewModel.targetAppList.value = ConfigManager.getTemplateTargetAppList(it)
                    // Load new fields
                    viewModel.longitude = ConfigManager.getTemplateLongitude(it)
                    viewModel.latitude = ConfigManager.getTemplateLatitude(it)
                    viewModel.eciNci = ConfigManager.getTemplateEciNci(it)
                    viewModel.pci = ConfigManager.getTemplatePci(it)
                    viewModel.tac = ConfigManager.getTemplateTac(it)
                    viewModel.earfcnNrarfcn = ConfigManager.getTemplateEarfcnNrarfcn(it)
                    viewModel.bandwidth = ConfigManager.getTemplateBandwidth(it)
                }
                @Suppress("UNCHECKED_CAST")
                return viewModel as T
            } else throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    val appliedAppList = MutableStateFlow<ArrayList<String>>(ArrayList())
    val targetAppList = MutableStateFlow<ArrayList<String>>(ArrayList())
    
    // New fields for location and network parameters
    var longitude: String? = null
    var latitude: String? = null
    var eciNci: String? = null
    var pci: String? = null
    var tac: String? = null
    var earfcnNrarfcn: String? = null
    var bandwidth: String? = null
}
