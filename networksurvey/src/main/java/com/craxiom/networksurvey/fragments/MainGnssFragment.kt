package com.craxiom.networksurvey.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.ui.gnss.GnssOverviewScreen
import com.craxiom.networksurvey.ui.main.appbar.FragmentTopBarScroll
import com.craxiom.networksurvey.ui.main.appbar.fragmentTopBarScroll
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class MainGnssFragment : Fragment() {
    internal val topBarScroll = FragmentTopBarScroll()
    private val viewModel: SignalInfoViewModel by activityViewModels()
    private var hasPermission by mutableStateOf(false)
    private var locationEnabled by mutableStateOf(false)
    private var receivingUpdates = false
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshLocationStatus()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NsTheme {
                    GnssOverviewScreen(
                        viewModel, hasPermission, locationEnabled,
                        modifier = Modifier.fragmentTopBarScroll(topBarScroll),
                    ) {
                        if (hasPermission) {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } else {
                            requestLocation.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ))
                        }
                    }
                }
            }
        }

    override fun onResume() {
        super.onResume()
        refreshLocationStatus()
    }

    override fun onPause() {
        if (receivingUpdates) {
            viewModel.setStarted(requireContext(), false, Application.getPrefs())
            receivingUpdates = false
        }
        super.onPause()
    }

    private fun refreshLocationStatus() {
        val context = context ?: return
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(LocationManager::class.java)
        locationEnabled = manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        if (isResumed && hasPermission && locationEnabled && !receivingUpdates) {
            viewModel.reset()
            viewModel.setStarted(context, true, Application.getPrefs(), trackOrientation = false)
            receivingUpdates = true
        }
    }
}