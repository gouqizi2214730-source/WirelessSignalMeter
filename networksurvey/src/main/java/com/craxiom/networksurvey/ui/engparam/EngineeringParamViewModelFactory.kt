package com.craxiom.networksurvey.ui.engparam

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.craxiom.networksurvey.domain.EngineeringParamRepository

/**
 * [EngineeringParamViewModel] 的工厂，注入 Application Context 与 Repository。
 * 用于 Compose 的 [androidx.lifecycle.viewmodel.compose.viewModel] 手动创建。
 */
class EngineeringParamViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val repository = EngineeringParamRepository(appContext)
        return EngineeringParamViewModel(appContext, repository) as T
    }
}
