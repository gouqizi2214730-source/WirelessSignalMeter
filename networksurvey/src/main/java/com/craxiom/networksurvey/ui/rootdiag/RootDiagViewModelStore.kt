package com.craxiom.networksurvey.ui.rootdiag

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Process-scoped store so DIAG collection can outlive the visible activity. */
internal object RootDiagViewModelStore : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
