package com.craxiom.networksurvey.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.SimChangeReceiver;
import com.craxiom.networksurvey.databinding.FragmentCellularSimpleBinding;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.controller.CellularController;

import java.util.List;


/**
 * 蜂窝页面容器 Fragment。
 *
 * 不再使用 TabLayout + ViewPager2 切换 SIM 卡，改为只持有一个 {@link NetworkDetailsFragment}，
 * SIM 切换通过信号页顶部的运营商按钮完成（{@link #switchToNextSim()}），
 * 由 Compose 层调用。
 */
public class MainCellularFragment extends AServiceDataFragment
{
    private List<SubscriptionInfo> activeSubscriptionInfoList;
    private BroadcastReceiver simBroadcastReceiver;
    private FragmentCellularSimpleBinding binding;

    // 当前选中的 SIM 索引（静态，跨 Fragment 重建保持）
    private static int currentSimIndex = 0;

    /** SIM 切换回调，由 Compose 层注册 */
    public interface OnSimSwitchCallback {
        void onSimSwitched(int simIndex, int simCount, String label);
    }

    private OnSimSwitchCallback simSwitchCallback;

    /** 暴露已绑定的 Service 引用，供子 Fragment 直接复用（跳过重复 bindService） */
    public NetworkSurveyService getService() {
        return service;
    }

    public void setSimSwitchCallback(OnSimSwitchCallback callback) {
        this.simSwitchCallback = callback;
    }

    /** 获取当前 SIM 数量 */
    public int getSimCount() {
        return activeSubscriptionInfoList == null ? 0 : activeSubscriptionInfoList.size();
    }

    /** 获取当前 SIM 索引 */
    public int getCurrentSimIndex() {
        return currentSimIndex;
    }

    /** 获取当前 SIM 标签 */
    public String getCurrentSimLabel() {
        if (activeSubscriptionInfoList == null || activeSubscriptionInfoList.isEmpty()) return "No SIM";
        SubscriptionInfo subscription = activeSubscriptionInfoList.get(currentSimIndex);
        CharSequence carrier = subscription.getCarrierName();
        String slotLabel = "SIM " + (currentSimIndex + 1);
        if (carrier == null || carrier.length() == 0) return slotLabel;
        return activeSubscriptionInfoList.size() > 1 ? carrier + " · " + slotLabel : carrier.toString();
    }

    /** 切换到下一个 SIM（在 SIM1 / SIM2 之间循环切换） */
    public void switchToNextSim() {
        if (activeSubscriptionInfoList == null || activeSubscriptionInfoList.size() <= 1) return;
        currentSimIndex = (currentSimIndex + 1) % activeSubscriptionInfoList.size();
        showNetworkDetailsFragment();
        if (simSwitchCallback != null) {
            simSwitchCallback.onSimSwitched(currentSimIndex, activeSubscriptionInfoList.size(), getCurrentSimLabel());
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        simBroadcastReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (intent == null) return;

                if (service != null && isAdded()) updateSubscriptions(service);
            }
        };

        Context context = getContext();
        if (context != null)
        {
            LocalBroadcastManager.getInstance(context).registerReceiver(simBroadcastReceiver,
                    new IntentFilter(SimChangeReceiver.SIM_CHANGED_INTENT));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        binding = FragmentCellularSimpleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        showNetworkDetailsFragment();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        View view = getView();
        if (view != null)
        {
            view.setKeepScreenOn(false);
        }

        startAndBindToService();
    }

    @Override
    public void onDestroy()
    {
        Context context = getContext();
        if (context != null)
        {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(simBroadcastReceiver);
        }

        super.onDestroy();
    }

    @Override
    protected void onSurveyServiceConnected(NetworkSurveyService service)
    {
        updateSubscriptions(service);
    }

    private void updateSubscriptions(NetworkSurveyService service)
    {
        activeSubscriptionInfoList = service.getActiveSubscriptionInfoList();

        // 确保索引不越界
        if (currentSimIndex >= activeSubscriptionInfoList.size()) {
            currentSimIndex = 0;
        }

        showNetworkDetailsFragment();

        // 通知 Compose 层当前 SIM 状态
        if (simSwitchCallback != null) {
            simSwitchCallback.onSimSwitched(currentSimIndex, activeSubscriptionInfoList.size(), getCurrentSimLabel());
        }
    }

    /** 根据当前 SIM 索引创建/替换 NetworkDetailsFragment */
    private void showNetworkDetailsFragment()
    {
        if (getView() == null) return;

        int subscriptionId;
        if (activeSubscriptionInfoList == null || activeSubscriptionInfoList.isEmpty()) {
            subscriptionId = CellularController.DEFAULT_SUBSCRIPTION_ID;
        } else if (activeSubscriptionInfoList.size() == 1) {
            subscriptionId = CellularController.DEFAULT_SUBSCRIPTION_ID;
        } else {
            subscriptionId = activeSubscriptionInfoList.get(currentSimIndex).getSubscriptionId();
        }

        Fragment current = getChildFragmentManager().findFragmentById(R.id.cellular_detail_container);
        if (current instanceof NetworkDetailsFragment && current.getArguments() != null &&
                current.getArguments().getInt(NetworkDetailsFragment.SUBSCRIPTION_ID_KEY) == subscriptionId) return;

        NetworkDetailsFragment fragment = new NetworkDetailsFragment();
        Bundle args = new Bundle();
        args.putInt(NetworkDetailsFragment.SUBSCRIPTION_ID_KEY, subscriptionId);
        fragment.setArguments(args);

        getChildFragmentManager().beginTransaction()
                .replace(R.id.cellular_detail_container, fragment)
                .commitAllowingStateLoss();
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        super.onSurveyServiceDisconnecting(service);
    }

    /**
     * Called by child fragments when neighbor data is updated.
     */
    public void onNeighborDataUpdated()
    {
        // Compose 自身接管列表滚动，此处无需原老版嵌套滚动补偿
    }
}
