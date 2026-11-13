package com.craxiom.networksurvey.fragments;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.databinding.FragmentNetworkDetailsBinding;
import com.craxiom.networksurvey.fragments.model.CarrierAggregationViewState;
import com.craxiom.networksurvey.fragments.model.CellularViewModel;
import com.craxiom.networksurvey.fragments.model.GsmNeighbor;
import com.craxiom.networksurvey.fragments.model.LteNeighbor;
import com.craxiom.networksurvey.fragments.model.NrNeighbor;
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellSighting;
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellViewState;
import com.craxiom.networksurvey.fragments.model.UmtsNeighbor;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.model.NetworkTechnologyInfo;
import com.craxiom.networksurvey.model.NrRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.ui.cellular.CellularChartViewModel;
import com.craxiom.networksurvey.ui.cellular.CellularDetailsCompose;
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.util.CalculationUtils;
import com.craxiom.networksurvey.util.CellularBandwidthUtils;
import com.craxiom.networksurvey.util.CellularUtils;
import com.craxiom.networksurvey.util.ColorUtils;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.ParserUtils;
import com.craxiom.networksurvey.util.TelephonyStateUtils;
import com.mackhartley.roundedprogressbar.RoundedProgressBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import timber.log.Timber;

/**
 * A fragment for displaying the latest cellular network details to the user.
 *
 * @since 1.6.0 (It really came earlier, but was minimal until the 1.6.0 rewrite.
 */
public class NetworkDetailsFragment extends AServiceDataFragment implements ICellularSurveyRecordListener
{
    public static final String SUBSCRIPTION_ID_KEY = "subscription_id";

    // The next two values have been added because certain devices don't follow the Interger#MAX_VALUE approach defined
    // in the Android API. The phone is supposed to report Interger#MAX_VALUE to indicate "Unknown/Unset" values, but
    // Pixel devices seem to report -120 all the time for UMTS RSCP, and Samsung devices seem to report -24 for UMTS RSCP.
    // These values are technically valid and filtering them out is an incorrect thing to do, but it is all I can think
    // of right now to prevent invalid values from being reported.
    private static final int RSCP_UNSET_VALUE_120 = -120;
    private static final int RSCP_UNSET_VALUE_24 = -24;

    // Separator between a technology and its qualifier ("NR · Standalone") and between a pill's
    // label and value ("Voice · VoNR").
    private static final String TECH_SEPARATOR = " · ";

    /**
     * How long the NR Secondary Cell card keeps showing the last-seen cell in its idle state
     * after the NR leg detaches. Past this age the sighting is too stale to be useful and the
     * card hides as it did before the idle state existed.
     * <p>
     * The point of the idle state is to stop the card flickering in and out while the user reads
     * the screen, since on NSA the NR leg drops a few seconds after each data transfer ends. Two
     * minutes covers that comfortably. It is deliberately short because a held cell is a claim
     * about the recent past, and the longer it is held the less likely it still describes where
     * the user is. A change of serving cell drops the sighting regardless of its age.
     */
    private static final long NR_IDLE_MAX_AGE_MS = 2 * 60 * 1000L;

    /**
     * Alpha applied to the NR Secondary Cell card body while idle. Dimmed enough to read as "not
     * live" next to the Idle badge, but kept high enough that the values stay legible in both
     * themes.
     */
    private static final float NR_IDLE_BODY_ALPHA = 0.65f;

    private int subscriptionId;

    // The NR mode from the most recent onNetworkType callback. Written on the survey executor
    // (onNetworkType always runs just before the same scan's onCellularBatch) and volatile so
    // main-thread readers see the latest value.
    private volatile TelephonyStateUtils.NrMode currentNrMode = TelephonyStateUtils.NrMode.NONE;

    private FragmentNetworkDetailsBinding binding;
    private CellularViewModel viewModel;
    private CellularChartViewModel chartViewModel;
    private SharedViewModel sharedViewModel;
    private AirplaneModeReceiver airplaneModeReceiver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        //noinspection DataFlowIssue
        subscriptionId = args.getInt(SUBSCRIPTION_ID_KEY, -1);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        binding = FragmentNetworkDetailsBinding.inflate(inflater);

        viewModel = new ViewModelProvider(requireActivity()).get(getClass().getName() + subscriptionId, CellularViewModel.class);
        chartViewModel = new ViewModelProvider(requireActivity()).get(getClass().getName() + "cellular_chart" + subscriptionId, CellularChartViewModel.class);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // 全 Compose 渲染：将蜂窝详情 UI 装入 ComposeView
        CellularDetailsCompose.setContent(binding.cellularDetailsComposeView, viewModel, chartViewModel,
                this::showCellularInfoDialog, this::showNetworkTechnologyInfoDialog, this::showNrDetailsInfoDialog);

        return binding.getRoot();
    }
    @Override
    public void onPause()
    {

        // Unregister airplane mode receiver
        if (airplaneModeReceiver != null)
        {
            try
            {
                requireContext().unregisterReceiver(airplaneModeReceiver);
                Timber.d("Unregistered airplane mode receiver");
            } catch (IllegalArgumentException e)
            {
                Timber.w(e, "Airplane mode receiver was not registered");
            }
            airplaneModeReceiver = null;
        }

        super.onPause();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // Register airplane mode receiver
        airplaneModeReceiver = new AirplaneModeReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        requireContext().registerReceiver(airplaneModeReceiver, filter);

        // Check initial airplane mode state
        boolean isAirplaneModeOn = isAirplaneModeOn(requireContext());
        viewModel.setAirplaneModeActive(isAirplaneModeOn);
        if (isAirplaneModeOn)
        {
            clearCellularUi();
        }

        // 直接复用父 Fragment 已绑定的 Service，跳过第二次 bindService 的异步等待。
        // 若父 Service 尚未就绪则回退到自行绑定。
        final Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof MainCellularFragment)
        {
            NetworkSurveyService parentService = ((MainCellularFragment) parentFragment).getService();
            if (parentService != null)
            {
                // 父 Fragment 已绑定 Service，直接复用引用，跳过异步 bindService
                service = parentService;
                onSurveyServiceConnected(service);
            }
            else
            {
                // 父 Fragment 的 Service 尚未绑定完成，回退到自行绑定
                startAndBindToService();
            }
        }
        else
        {
            startAndBindToService();
        }

    }

    @Override
    public void onDestroyView()
    {
        viewModel.getServingCellProtocol().removeObservers(getViewLifecycleOwner());
        viewModel.getCellId().removeObservers(getViewLifecycleOwner());

        super.onDestroyView();
    }

    @Override
    protected void onSurveyServiceConnected(NetworkSurveyService service)
    {
        service.registerCellularSurveyRecordListener(this);

        service.runSingleCellularScan();
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        service.unregisterCellularSurveyRecordListener(this);

        super.onSurveyServiceDisconnecting(service);
    }

    @Override
    public void onCellularBatch(List<CellularRecordWrapper> cellularGroup, int subscriptionId)
    {
        // The records are for a different SIM, so ignore them because another
        // NetworkDetailsFragment instance will handle them.
        if (this.subscriptionId != subscriptionId) return;

        processCellularGroup(cellularGroup);
    }

    @Override
    public void onNetworkType(NetworkTechnologyInfo technologyInfo, int subscriptionId)
    {
        // The records are for a different SIM, so ignore them because another
        // NetworkDetailsFragment instance will handle them.
        if (this.subscriptionId != subscriptionId) return;

        currentNrMode = technologyInfo.nrMode();

        final TelephonyStateUtils.HeroResult hero = TelephonyStateUtils.deriveHero(
                technologyInfo.nrMode(), technologyInfo.baseDataRat(), technologyInfo.registrationRows());
        final String heroText = heroDisplay(hero);

        viewModel.setHeroText(heroText);
        viewModel.setHeroColorId(heroColorId(hero.state()));

        // The pill row supports a live technology hero. On the degraded No Service and Unknown
        // states the pills usually carry nothing but "None"/"Unknown" noise the hero already
        // conveys, so the row hides (a null voice value hides it) unless a pill still has a real
        // value to show (e.g. on API 26-30 during Wi-Fi calling the hero is Unknown while the
        // legacy data network type is IWLAN). On every live state show all three pills with
        // explicit values so nothing appears or disappears during normal use.
        if (!TelephonyStateUtils.shouldShowPillRow(hero.state(), technologyInfo.voiceDisplay(),
                technologyInfo.dataDisplay(),
                Set.of(NetworkSurveyConstants.UNKNOWN, getString(R.string.voice_bearer_none))))
        {
            viewModel.setVoicePillValue(null);
            viewModel.setDataPillValue(null);
            viewModel.setBrandingPillValue(null);
        } else
        {
            viewModel.setVoicePillValue(technologyInfo.voiceDisplay());
            viewModel.setDataPillValue(technologyInfo.dataDisplay());
            // Branding shows the carrier's marketing override label, or a uniform "None" when there
            // is no branding override (or it is not yet known). Gating on the raw override int keeps
            // the value consistent across API levels and scan paths; overrideDisplay itself is "N/A"
            // or "Unknown" on some paths, which would not match the info dialog copy.
            viewModel.setBrandingPillValue(
                    TelephonyStateUtils.isBrandingOverride(technologyInfo.overrideNetworkType())
                            ? technologyInfo.overrideDisplay()
                            : getString(R.string.branding_none));
        }

        viewModel.setCarrierAggregation(carrierAggregationViewState(technologyInfo.cellBandwidthsKhz()));
    }

    /**
     * @return The hero technology line for the top card (e.g. "NR · Standalone").
     */
    private String heroDisplay(TelephonyStateUtils.HeroResult hero)
    {
        return switch (hero.state())
        {
            case NR_STANDALONE -> NetworkSurveyConstants.NR + TECH_SEPARATOR
                    + getString(R.string.nr_mode_standalone);
            case NR_NON_STANDALONE -> NetworkSurveyConstants.LTE + " + " + NetworkSurveyConstants.NR
                    + TECH_SEPARATOR + NetworkSurveyConstants.NSA;
            case DATA_RAT -> CalculationUtils.getNetworkType(hero.rat());
            case NO_SERVICE -> getString(R.string.hero_no_service);
            case UNKNOWN -> getString(R.string.unknown);
        };
    }

    /**
     * @return The hero text color resource: accent for a live technology, orange for no service,
     * and faded for unknown, so a bad state never renders in the "all good" accent color.
     */
    private int heroColorId(TelephonyStateUtils.HeroState state)
    {
        return switch (state)
        {
            case NO_SERVICE -> R.color.rssi_orange;
            case UNKNOWN -> R.color.fadedText;
            default -> R.color.colorAccent;
        };
    }

    /**
     * @return The carrier aggregation view state (per-carrier chip labels plus the summary line),
     * or null when fewer than two valid component carriers are reported (so the section is hidden).
     */
    private CarrierAggregationViewState carrierAggregationViewState(int[] cellBandwidthsKhz)
    {
        final int[] validKhz = CellularBandwidthUtils.validBandwidthsKhz(cellBandwidthsKhz);
        if (validKhz.length <= 1) return null;

        final List<String> chipLabels = new ArrayList<>(validKhz.length);
        for (int khz : validKhz)
        {
            chipLabels.add(getString(R.string.mhz_value_label, CellularBandwidthUtils.formatBandwidthMhz(khz)));
        }
        final String summary = getResources().getQuantityString(R.plurals.carrier_aggregation_summary,
                validKhz.length, validKhz.length,
                CellularBandwidthUtils.formatBandwidthMhz(CellularBandwidthUtils.aggregateBandwidthKhz(validKhz)));
        return new CarrierAggregationViewState(chipLabels, summary);
    }
    /**
     * Clears out the UI, which is needed if the phone stops seeing towers or something else happens (e.g. airplane mode).
     */
    private void clearCellularUi()
    {
        viewModel.setServingCellProtocol(CellularProtocol.NONE);

        viewModel.setMcc("");
        viewModel.setMnc("");
        viewModel.setAreaCode("");
        viewModel.setCellId(null);
        viewModel.setChannelNumber("");
        viewModel.setFrequency("");
        viewModel.setBand("");
        viewModel.setLteBand("");

        viewModel.setPci("");
        viewModel.setBandwidth("");
        viewModel.setTa("");
        viewModel.setCqi("");

        viewModel.setSignalOne(null);
        viewModel.setSignalTwo(null);
        viewModel.setSignalThree(null);

        viewModel.setNrNeighbors(Collections.emptySortedSet());
        viewModel.setLteNeighbors(Collections.emptySortedSet());
        viewModel.setUmtsNeighbors(Collections.emptySortedSet());
        viewModel.setGsmNeighbors(Collections.emptySortedSet());

        // Carrier aggregation is a property of the serving cell, so clear it when the serving cell
        // goes away. The top-card fields (hero and pills) are intentionally left alone here:
        // onNetworkType refreshes them on every scan before this runs.
        viewModel.setCarrierAggregation(null);

        viewModel.setNrSecondaryCell(null);
        viewModel.setNrLastSeenText("");
        viewModel.clearLastNrSecondaryCell();
    }

    /**
     * The method responsible for handling a new batch of cellular records.
     *
     * @param cellularGroup The new batch of cellular records.
     */
    private void processCellularGroup(List<CellularRecordWrapper> cellularGroup)
    {
        final List<GsmRecordData> gsmNeighbors = new ArrayList<>();
        final List<UmtsRecordData> umtsNeighbors = new ArrayList<>();
        final List<LteRecordData> lteNeighbors = new ArrayList<>();
        final List<NrRecordWrapper> nrNonServing = new ArrayList<>();
        CellularRecordWrapper servingCellRecord = null;
        for (CellularRecordWrapper record : cellularGroup) {
            if (isRegisteredCell(record) &&
                    (servingCellRecord == null || record.cellularProtocol == CellularProtocol.NR)) {
                servingCellRecord = record;
            }
        }
        for (CellularRecordWrapper cellularRecord : cellularGroup)
        {
            switch (cellularRecord.cellularProtocol)
            {
                case NONE:
                    continue;

                case GSM:
                    final GsmRecordData gsmData = ((GsmRecord) cellularRecord.cellularRecord).getData();
                    if (cellularRecord == servingCellRecord)
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processGsmServingCell(gsmData);
                    } else
                    {
                        gsmNeighbors.add(gsmData);
                    }
                    break;

                case CDMA:
                    // We don't support CDMA since it is pretty much gone
                    break;

                case UMTS:
                    final UmtsRecordData umtsData = ((UmtsRecord) cellularRecord.cellularRecord).getData();
                    if (cellularRecord == servingCellRecord)
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processUmtsServingCell(umtsData);
                    } else
                    {
                        umtsNeighbors.add(umtsData);
                    }
                    break;

                case LTE:
                    final LteRecordData lteData = ((LteRecord) cellularRecord.cellularRecord).getData();
                    if (cellularRecord == servingCellRecord)
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processLteServingCell(lteData);
                    } else
                    {
                        lteNeighbors.add(lteData);
                    }
                    break;

                case NR:
                    final NrRecordData nrData = ((NrRecord) cellularRecord.cellularRecord).getData();
                    if (cellularRecord == servingCellRecord)
                    {
                        viewModel.setServingCellProtocol(cellularRecord.cellularProtocol);
                        processNrServingCell(nrData, ((NrRecordWrapper) cellularRecord).bands);
                    } else
                    {
                        nrNonServing.add((NrRecordWrapper) cellularRecord);
                    }
                    break;
            }
        }

        if (servingCellRecord == null) {
            clearCellularUi();
            chartViewModel.recordSignal(CellularProtocol.NONE, null);
        } else {
            sharedViewModel.updateLatestServingCellInfo(
                    new ServingCellInfo(servingCellRecord, subscriptionId, System.currentTimeMillis()));
        }

        // A cell the device reports as SECONDARY_SERVING is one the phone is actively using: on
        // 5G NSA it is the NR cell carrying the 5G data (the phone is registered on the LTE
        // anchor, so it shows up as a non-serving record), and on 5G SA with NR carrier
        // aggregation it is an NR SCell. Promote it to the NR details card and keep it out of the
        // neighbors table. Devices that report no connection status simply do not show the card.
        final NrRecordWrapper secondaryNrCell = CellularUtils.selectSecondaryServingNrCell(nrNonServing);
        if (secondaryNrCell != null) nrNonServing.remove(secondaryNrCell);
        viewModel.setNrSecondaryCell(resolveNrSecondaryCellViewState(secondaryNrCell,
                CellularUtils.getTowerId(servingCellRecord)));

        processGsmNeighbors(gsmNeighbors);
        processUmtsNeighbors(umtsNeighbors);
        processLteNeighbors(lteNeighbors);
        processNrNeighbors(nrNonServing.stream()
                .map(wrapper -> ((NrRecord) wrapper.cellularRecord).getData())
                .collect(Collectors.toList()));
    }

    private static boolean isRegisteredCell(CellularRecordWrapper wrapper)
    {
        return switch (wrapper.cellularProtocol) {
            case GSM -> ((GsmRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
            case UMTS -> ((UmtsRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
            case LTE -> ((LteRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
            case NR -> ((NrRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
            default -> false;
        };
    }

    /**
     * Takes in the GSM serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the GSM serving cell record.
     */
    private void processGsmServingCell(GsmRecordData data)
    {
        chartViewModel.recordSignal(CellularProtocol.GSM,
                data.hasSignalStrength() ? (int) data.getSignalStrength().getValue() : null);

        viewModel.setCarrier(data.getProvider());
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
        viewModel.setAreaCode(data.hasLac() ? String.valueOf(data.getLac().getValue()) : "");
        viewModel.setCellId(data.hasCi() ? (long) data.getCi().getValue() : null);
        viewModel.setChannelNumber(data.hasArfcn() ? String.valueOf(data.getArfcn().getValue()) : "");
        viewModel.setPci(data.hasBsic() ? ParserUtils.bsicToString(data.getBsic().getValue()) : "");

        viewModel.setSignalOne(data.hasSignalStrength() ? (int) data.getSignalStrength().getValue() : null);
    }

    /**
     * Takes in the UMTS serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the UMTS serving cell record.
     */
    private void processUmtsServingCell(UmtsRecordData data)
    {
        chartViewModel.recordSignal(CellularProtocol.UMTS,
                data.hasRscp() ? (int) data.getRscp().getValue() : null);

        viewModel.setCarrier(data.getProvider());
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
        viewModel.setAreaCode(data.hasLac() ? String.valueOf(data.getLac().getValue()) : "");
        viewModel.setCellId(data.hasCid() ? (long) data.getCid().getValue() : null);
        viewModel.setChannelNumber(data.hasUarfcn() ? String.valueOf(data.getUarfcn().getValue()) : "");
        viewModel.setPci(data.hasPsc() ? String.valueOf(data.getPsc().getValue()) : "");

        viewModel.setSignalOne(data.hasSignalStrength() ? (int) data.getSignalStrength().getValue() : null);
        viewModel.setSignalTwo(data.hasRscp() ? (int) data.getRscp().getValue() : null);
    }

    /**
     * Takes in the LTE serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the LTE serving cell record.
     */
    private void processLteServingCell(LteRecordData data)
    {
        chartViewModel.recordSignal(CellularProtocol.LTE,
                data.hasRsrp() ? (int) data.getRsrp().getValue() : null);

        viewModel.setCarrier(data.getProvider());
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
        viewModel.setAreaCode(data.hasTac() ? String.valueOf(data.getTac().getValue()) : "");
        viewModel.setCellId(data.hasEci() ? (long) data.getEci().getValue() : null);

        // Set EARFCN without band information
        viewModel.setChannelNumber(data.hasEarfcn() ? String.valueOf(data.getEarfcn().getValue()) : "");

        // Set LTE band field with band number and name
        if (data.hasEarfcn())
        {
            int earfcn = data.getEarfcn().getValue();
            int bandNumber = CellularUtils.downlinkEarfcnToBand(earfcn);
            if (bandNumber != -1)
            {
                String bandName = CellularUtils.getLteBandName(bandNumber);
                if (bandName != null)
                {
                    viewModel.setLteBand(bandNumber + " (" + bandName + ")");
                } else
                {
                    viewModel.setLteBand(String.valueOf(bandNumber));
                }
            } else
            {
                viewModel.setLteBand("");
            }
        } else
        {
            viewModel.setLteBand("");
        }

        viewModel.setPci(data.hasPci() ? formatPci(data.getPci().getValue()) : "");
        viewModel.setBandwidth(LteMessageConstants.getLteBandwidth(data.getLteBandwidth()));
        viewModel.setTa(data.hasTa() ? String.valueOf(data.getTa().getValue()) : "");
        viewModel.setCqi(data.hasCqi() ? String.valueOf(data.getCqi().getValue()) : "");

        viewModel.setSignalOne(data.hasRsrp() ? (int) data.getRsrp().getValue() : null);
        viewModel.setSignalTwo(data.hasRsrq() ? (int) data.getRsrq().getValue() : null);
        viewModel.setSignalThree(data.hasSnr() ? (int) data.getSnr().getValue() : null);
    }

    /**
     * Takes in the NR serving cell details and sets it in the view model so that it can be
     * displayed in the UI.
     *
     * @param data The details for the NR serving cell record.
     */
    private void processNrServingCell(NrRecordData data, int[] bands)
    {
        chartViewModel.recordSignal(CellularProtocol.NR,
                data.hasSsRsrp() ? (int) data.getSsRsrp().getValue() : null);

        viewModel.setCarrier(data.getProvider());
        setMccMncOnViewModel(data.hasPlmn() ? data.getPlmn().getValue() : null,
                data.hasMcc() ? data.getMcc().getValue() : null,
                data.hasMnc() ? data.getMnc().getValue() : null);
        viewModel.setAreaCode(data.hasTac() ? String.valueOf(data.getTac().getValue()) : "");
        viewModel.setCellId(data.hasNci() ? data.getNci().getValue() : null);

        // Set NARFCN without band information
        viewModel.setChannelNumber(data.hasNarfcn() ? String.valueOf(data.getNarfcn().getValue()) : "");

        viewModel.setBand(CellularUtils.formatNrBands(bands,
                data.hasNarfcn() ? data.getNarfcn().getValue() : -1));

        viewModel.setPci(data.hasPci() ? formatPci(data.getPci().getValue()) : "");

        viewModel.setFrequency(data.hasNarfcn() ? formatNrFrequency(data.getNarfcn().getValue()) : "");

        viewModel.setTa(data.hasTa() ? String.valueOf(data.getTa().getValue()) : "");

        viewModel.setSignalOne(data.hasSsRsrp() ? (int) data.getSsRsrp().getValue() : null);
        viewModel.setSignalTwo(data.hasSsRsrq() ? (int) data.getSsRsrq().getValue() : null);
        viewModel.setSignalThree(data.hasSsSinr() ? (int) data.getSsSinr().getValue() : null);
    }

    /**
     * @return The PCI display value including the Primary and Secondary Sync Sequence breakdown,
     * e.g. "146 (2/48)".
     */
    private String formatPci(int pci)
    {
        final int primarySyncSequence = CalculationUtils.getPrimarySyncSequence(pci);
        final int secondarySyncSequence = CalculationUtils.getSecondarySyncSequence(pci);
        return pci + " (" + primarySyncSequence + "/" + secondarySyncSequence + ")";
    }

    /**
     * @return The NR frequency display value in MHz for the provided NARFCN (e.g. "3709.920 MHz"),
     * or an empty string when the NARFCN is not in a valid range.
     */
    private String formatNrFrequency(int narfcn)
    {
        final double frequencyMhz = CellularUtils.narfcnToFrequencyMhz(narfcn);
        if (frequencyMhz <= 0) return "";

        return getString(R.string.mhz_value_label, String.format(Locale.US, "%.3f", frequencyMhz));
    }

    /**
     * Builds the NR Secondary Cell card's view state for this scan: the live cell when the scan
     * contains one, the dimmed last-seen idle state while the phone remains on NSA with the NR
     * leg detached (on NSA the leg only attaches during active data), or null to hide the card.
     * <p>
     * Also publishes the idle card's "Last seen ..." line, which is kept out of the returned view
     * state so that its per-scan ticking does not rebuild the rest of the card.
     *
     * @param secondaryNrCell The SECONDARY_SERVING NR cell from this scan, or null when absent.
     * @param servingCellId   The serving cell the phone is attached to for this scan. A sighting
     *                        recorded under a different serving cell describes a place the user
     *                        has since left, so it is never held as idle.
     */
    private NrSecondaryCellViewState resolveNrSecondaryCellViewState(NrRecordWrapper secondaryNrCell,
                                                                     String servingCellId)
    {
        final long nowMs = SystemClock.elapsedRealtime();
        final NrSecondaryCellSighting sighting = viewModel.getLastNrSecondaryCellSighting();
        final boolean sightingUsable = sighting.isUsableFor(servingCellId);
        final long lastSeenAgeMs = sightingUsable ? nowMs - sighting.realtimeMs() : -1;

        final TelephonyStateUtils.NrCardState cardState = TelephonyStateUtils.resolveNrCardState(
                secondaryNrCell != null, currentNrMode, lastSeenAgeMs, NR_IDLE_MAX_AGE_MS);

        switch (cardState)
        {
            case ACTIVE:
                final NrSecondaryCellViewState state = buildNrSecondaryCellViewState(secondaryNrCell);
                viewModel.setLastNrSecondaryCellSighting(
                        new NrSecondaryCellSighting(state, nowMs, servingCellId));
                viewModel.setNrLastSeenText("");
                return state;

            case IDLE:
                viewModel.setNrLastSeenText(
                        getString(R.string.nr_last_seen_format, formatAge(lastSeenAgeMs)));
                return sighting.state().asIdle();

            default:
                viewModel.setNrLastSeenText("");
                return null;
        }
    }

    /**
     * @return A short age display for the idle card's last-seen line, e.g. "42s" or "3m".
     */
    private String formatAge(long ageMs)
    {
        final long seconds = ageMs / 1000;
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m";
    }

    /**
     * Builds the view state for the NR Secondary Cell details card from the NR record the device
     * reported as SECONDARY_SERVING (the 5G NSA data leg, or an NR CA SCell under SA).
     */
    private NrSecondaryCellViewState buildNrSecondaryCellViewState(NrRecordWrapper wrapper)
    {
        final NrRecordData data = ((NrRecord) wrapper.cellularRecord).getData();

        return new NrSecondaryCellViewState(
                CellularUtils.formatNrBands(wrapper.bands,
                        data.hasNarfcn() ? data.getNarfcn().getValue() : -1),
                data.hasNarfcn() ? formatNrFrequency(data.getNarfcn().getValue()) : "",
                data.hasPci() ? formatPci(data.getPci().getValue()) : "",
                data.hasNarfcn() ? String.valueOf(data.getNarfcn().getValue()) : "",
                data.hasSsRsrp() ? (int) data.getSsRsrp().getValue() : null,
                data.hasSsRsrq() ? (int) data.getSsRsrq().getValue() : null,
                data.hasSsSinr() ? (int) data.getSsSinr().getValue() : null,
                false);
    }
    /**
     * Takes in the current group of UMTS neighbors, converts them to a {@link UmtsNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processGsmNeighbors(List<GsmRecordData> neighbors)
    {
        final TreeSet<GsmNeighbor> gsmNeighbors = neighbors.stream().map(data -> {
            GsmNeighbor.Builder builder = new GsmNeighbor.Builder();
            if (data.hasArfcn()) builder.arfcn(data.getArfcn().getValue());
            if (data.hasBsic()) builder.bsic(data.getBsic().getValue());
            if (data.hasSignalStrength()) builder.rssi((int) data.getSignalStrength().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setGsmNeighbors(gsmNeighbors);
    }

    /**
     * Takes in the current group of UMTS neighbors, converts them to a {@link UmtsNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processUmtsNeighbors(List<UmtsRecordData> neighbors)
    {
        final TreeSet<UmtsNeighbor> umtsNeighbors = neighbors.stream().map(data -> {
            UmtsNeighbor.Builder builder = new UmtsNeighbor.Builder();
            if (data.hasUarfcn()) builder.uarfcn(data.getUarfcn().getValue());
            if (data.hasPsc()) builder.psc(data.getPsc().getValue());
            if (data.hasRscp()) builder.rscp((int) data.getRscp().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setUmtsNeighbors(umtsNeighbors);
    }

    /**
     * Takes in the current group of LTE neighbors, converts them to an {@link LteNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processLteNeighbors(List<LteRecordData> neighbors)
    {
        final TreeSet<LteNeighbor> lteNeighbors = neighbors.stream().map(data -> {
            LteNeighbor.Builder builder = new LteNeighbor.Builder();
            if (data.hasEarfcn()) builder.earfcn(data.getEarfcn().getValue());
            if (data.hasPci()) builder.pci(data.getPci().getValue());
            if (data.hasRsrp()) builder.rsrp((int) data.getRsrp().getValue());
            if (data.hasRsrq()) builder.rsrq((int) data.getRsrq().getValue());
            if (data.hasTa()) builder.ta(data.getTa().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setLteNeighbors(lteNeighbors);
    }

    /**
     * Takes in the current group of LTE neighbors, converts them to an {@link NrNeighbor}, and then
     * updates the view model.
     *
     * @param neighbors The current group of Lte Neighbors.
     */
    private void processNrNeighbors(List<NrRecordData> neighbors)
    {
        final TreeSet<NrNeighbor> nrNeighbors = neighbors.stream().map(data -> {
            NrNeighbor.Builder builder = new NrNeighbor.Builder();
            if (data.hasNarfcn()) builder.narfcn(data.getNarfcn().getValue());
            if (data.hasPci()) builder.pci(data.getPci().getValue());
            if (data.hasSsRsrp()) builder.ssRsrp((int) data.getSsRsrp().getValue());
            if (data.hasSsRsrq()) builder.ssRsrq((int) data.getSsRsrq().getValue());
            return builder.build();
        }).sorted().collect(Collectors.toCollection(TreeSet::new));

        viewModel.setNrNeighbors(nrNeighbors);
    }
    /**
     * Builds the two-tone pill text: the label in the faded label color, the separator and value in
     * the pill's normal text color, matching the app's faded-label/bright-value convention.
     */
    private CharSequence buildPillText(@StringRes int labelRes, String value)
    {
        final String label = getString(labelRes);
        final SpannableStringBuilder builder = new SpannableStringBuilder(label);
        builder.append(TECH_SEPARATOR).append(value);

        final Context context = getContext();
        if (context != null)
        {
            builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.fadedText)),
                    0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }
    /**
     * Shows the Network Technology info dialog: an explanation of the hero line and the
     * Voice / Data / Branding pills.
     */
    private void showNetworkTechnologyInfoDialog()
    {
        if (getContext() == null) return;
        FragmentDialogs.showCellularInfo(getParentFragmentManager(),
                getString(R.string.network_technology_title),
                getString(R.string.network_technology_explanation));
    }
    /**
     * Displays a dialog with some information about cellular terms.
     */
    private void showCellularInfoDialog()
    {
        final Context context = getContext();
        if (context == null) return;

        CellularProtocol protocol = viewModel.getServingCellProtocol().getValue();

        // Default to LTE as a fallback
        String cellularInfoTitle = getString(R.string.lte_info_description);
        CharSequence cellularInfoBody = getString(R.string.lte_cellular_terms_explanation);

        if (protocol == null) protocol = CellularProtocol.LTE;
        switch (protocol)
        {
            case NONE, LTE ->
            {
                cellularInfoTitle = getString(R.string.lte_info_description);
                cellularInfoBody = getText(R.string.lte_cellular_terms_explanation);
            }
            case GSM ->
            {
                cellularInfoTitle = getString(R.string.gsm_info_description);
                cellularInfoBody = getText(R.string.gsm_cellular_terms_explanation);
            }
            case CDMA ->
            {
                cellularInfoTitle = "How did you find CDMA?";
                cellularInfoBody = "CDMA is no longer supported. I am impressed you were able to find a CDMA network! Honestly, send me an email at craxiomdev@gmail.com and let me know where you found it.";
            }
            case UMTS ->
            {
                cellularInfoTitle = getString(R.string.umts_info_description);
                cellularInfoBody = getText(R.string.umts_cellular_terms_explanation);
            }
            case NR ->
            {
                cellularInfoTitle = getString(R.string.nr_info_description);
                cellularInfoBody = getText(R.string.nr_cellular_terms_explanation);
            }
        }

        FragmentDialogs.showCellularInfo(getParentFragmentManager(), cellularInfoTitle, cellularInfoBody);
    }

    /**
     * Displays a dialog explaining what a 5G NR secondary cell is, plus the terms shown on the
     * card. Deliberately not the generic NR terms dialog: that one documents fields (MCC/MNC, TAC,
     * CID, TA) this card does not display.
     */
    private void showNrDetailsInfoDialog()
    {
        if (getContext() == null) return;
        FragmentDialogs.showCellularInfo(getParentFragmentManager(),
                getString(R.string.nr_secondary_info_description),
                getText(R.string.nr_secondary_cell_explanation));
    }

    /**
     * BroadcastReceiver to detect airplane mode changes.
     * This is independent of the PhoneStateListener and provides immediate UI updates.
     */
    private class AirplaneModeReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction()))
            {
                boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                Timber.d("Airplane mode broadcast received: %s, subscriptionId: %d",
                        isAirplaneModeOn ? "ON" : "OFF", subscriptionId);

                // Update view model
                viewModel.setAirplaneModeActive(isAirplaneModeOn);

                // Clear cellular UI if airplane mode is on
                if (isAirplaneModeOn)
                {
                    Timber.d("Clearing cellular UI due to airplane mode");
                    clearCellularUi();
                }
            }
        }
    }

    /**
     * Helper method to check if airplane mode is currently enabled.
     *
     * @param context The context to use for checking the setting.
     * @return True if airplane mode is on, false otherwise.
     */
    private static boolean isAirplaneModeOn(Context context)
    {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * Sets the MCC and MNC on the view model, preferring the PLMN string (which preserves
     * leading zeros) when available.
     */
    private void setMccMncOnViewModel(String plmn, Integer mcc, Integer mnc)
    {
        if (plmn != null)
        {
            viewModel.setMcc(NsUtils.extractMccFromPlmn(plmn));
            viewModel.setMnc(NsUtils.extractMncFromPlmn(plmn));
        } else
        {
            viewModel.setMcc(mcc != null ? String.valueOf(mcc) : "");
            viewModel.setMnc(mnc != null ? String.valueOf(mnc) : "");
        }
    }
}
