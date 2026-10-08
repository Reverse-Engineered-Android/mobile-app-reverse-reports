package com.taobao.android.remoteobject.easy;

import android.app.Application;
import android.text.TextUtils;
import com.alimm.xadsdk.request.builder.IRequestConst;
import com.taobao.android.remoteobject.BuildConfig;
import com.taobao.android.remoteobject.device.FishOaid;
import com.taobao.android.remoteobject.magicscreen.IMagicScreen;
import com.taobao.android.remoteobject.util.LoginUtil;
import com.taobao.idlefish.chain.ChainBlock;
import com.taobao.idlefish.minors.MinorsManager;
import com.taobao.idlefish.preinstall.ChannelManager;
import com.taobao.idlefish.protocol.dhh.IGetOaidCallback;
import com.taobao.idlefish.protocol.net.PClientInfo;
import com.taobao.idlefish.ui.util.ThreadUtils;
import com.taobao.idlefish.xframework.util.FishUmidHelper;
import com.taobao.idlefish.xmc.XModuleCenter;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: classes6.dex */
public class ClientHeaderInterceptor {
    /* JADX INFO: Access modifiers changed from: private */
    public void intercept(final Runnable runnable) {
        if (!FishIdSwitch.useNewId()) {
            if (runnable != null) {
                runnable.run();
                return;
            }
            return;
        }
        final Application application = XModuleCenter.getApplication();
        final Map<String, String> clientHeader = ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).getClientHeader();
        if (clientHeader == null) {
            clientHeader = new HashMap<>();
        }
        if (BuildConfig.OPEN_PRE_INSTALL.booleanValue()) {
            clientHeader.put("fish_preinstall", "1");
            clientHeader.put("fish_preinstall_channel", ChannelManager.getInstance().getChannelName());
        }
        clientHeader.put("channel_2", ChannelManager.getInstance().getChannelId());
        String string = application.getSharedPreferences("fish_imei", 0).getString("fish_imei", "");
        if (!TextUtils.isEmpty(string)) {
            clientHeader.put("imei", string);
            ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
        }
        String securityToken = FishUmidHelper.getSecurityToken(XModuleCenter.getApplication());
        if (!TextUtils.isEmpty(securityToken)) {
            clientHeader.put("umid", securityToken);
            ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
        }
        clientHeader.put("x-magic_device", ((IMagicScreen) ChainBlock.instance().obtainChain("MagicScreenImpl", IMagicScreen.class, true)).isMagicDevices() ? "1" : "0");
        clientHeader.put("x-screen-level", ((IMagicScreen) ChainBlock.instance().obtainChain("MagicScreenImpl", IMagicScreen.class, true)).stringForServerScreenLevel());
        clientHeader.put("first_open", DeviceActivateUtils.getFlag() ? "1" : "0");
        ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
        clientHeader.put("x-custom-cache-uid", LoginUtil.getUserId());
        ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
        if ("false".equals(application.getSharedPreferences("fish_privicy", 0).getString("LOCAL_PRIVACY_RECOMMAND_STATUS", "true"))) {
            clientHeader.put("x-custom-privacy-device-recommend-closed", "true");
            ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
        } else {
            clientHeader.remove("x-custom-privacy-device-recommend-closed");
            ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
        }
        if (MinorsManager.getInstance().supportMinors(application)) {
            clientHeader.put("minors_mode_enabled", MinorsManager.getInstance().isMinorsEnableFromCache(application) + "");
            ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
        }
        FishOaid.inst().getOaid(application, new IGetOaidCallback() { // from class: com.taobao.android.remoteobject.easy.ClientHeaderInterceptor.3
            @Override // com.taobao.idlefish.protocol.dhh.IGetOaidCallback
            public void onOaidCallback(String str, long j) {
                if (!TextUtils.isEmpty(str)) {
                    clientHeader.put(IRequestConst.OAID, str);
                    clientHeader.put("aliOaid", FishOaid.inst().getAliOaidFromCache(application));
                    ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).setClientHeader(clientHeader);
                }
                Runnable runnable2 = runnable;
                if (runnable2 != null) {
                    runnable2.run();
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Runnable wrapBackgroundRunnable(final Runnable runnable) {
        return new Runnable() { // from class: com.taobao.android.remoteobject.easy.ClientHeaderInterceptor.2
            @Override // java.lang.Runnable
            public void run() {
                ThreadUtils.post(runnable, true);
            }
        };
    }

    public void asyncIntercept(final Runnable runnable) {
        ThreadUtils.post(new Runnable() { // from class: com.taobao.android.remoteobject.easy.ClientHeaderInterceptor.1
            @Override // java.lang.Runnable
            public void run() {
                ClientHeaderInterceptor clientHeaderInterceptor = ClientHeaderInterceptor.this;
                clientHeaderInterceptor.intercept(clientHeaderInterceptor.wrapBackgroundRunnable(runnable));
            }
        }, true);
    }
}
