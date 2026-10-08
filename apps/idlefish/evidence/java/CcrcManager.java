package com.idlefish.flutterbridge.ccrc;

import android.app.Application;
import android.taobao.windvane.util.ImageTool$$ExternalSyntheticOutline0;
import android.text.TextUtils;
import com.alibaba.security.ccrc.enums.InitState;
import com.alibaba.security.ccrc.enums.Mode;
import com.alibaba.security.ccrc.interfaces.OnCcrcCallback;
import com.alibaba.security.ccrc.model.InitResult;
import com.alibaba.security.ccrc.service.CcrcContext;
import com.alibaba.security.ccrc.service.CcrcService;
import com.alibaba.security.wukong.bx.CcrcBHService;
import com.alibaba.security.wukong.model.TextRiskSample;
import com.alibaba.security.wukong.model.meta.Text;
import com.idlefish.blink.FishNewModule;
import com.idlefish.blink.ModuleNewInit;
import com.idlefish.blink.ProcPhase;
import com.meizu.cloud.pushsdk.handler.a.a.a$$ExternalSyntheticOutline0;
import com.taobao.idlefish.protocol.ccrc.PCcrc;
import com.taobao.idlefish.protocol.env.PEnv;
import com.taobao.idlefish.protocol.login.PLogin;
import com.taobao.idlefish.protocol.remoteconfig.PRemoteConfigs;
import com.taobao.idlefish.soloader.SoLoaderManager;
import com.taobao.idlefish.soloader.SoModuleLoadListener;
import com.taobao.idlefish.webview.BuildConfig;
import com.taobao.idlefish.xmc.XModuleCenter;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: classes4.dex */
@FishNewModule(protocol = "com.taobao.idlefish.protocol.ccrc.PCcrc")
public class CcrcManager implements PCcrc {
    private static CcrcService mCcrcService;
    private CcrcBHService mCcrcBHService;

    public static void activate() {
        CcrcService ccrcService = mCcrcService;
        if (ccrcService == null || ccrcService.isActivate()) {
            return;
        }
        mCcrcService.activate(new CcrcService.Config.Builder().setPid(ImageTool$$ExternalSyntheticOutline0.m(ImageTool$$ExternalSyntheticOutline0.m28m(((PLogin) XModuleCenter.moduleForProtocol(PLogin.class)).getLoginInfo().getUserId(), "_"))).setMode(Mode.DEFAULT).build(), new OnCcrcCallback() { // from class: com.idlefish.flutterbridge.ccrc.CcrcManager.2
            @Override // com.alibaba.security.ccrc.interfaces.OnCcrcCallback
            public final void onInit(InitState initState, InitResult initResult) {
                a$$ExternalSyntheticOutline0.m("activate success:", initState.equals(InitState.INIT_SUCCESS) || initState.equals(InitState.INITED), "CcrcService", "Application");
            }
        });
    }

    public static void deActivate() {
        CcrcService ccrcService = mCcrcService;
        if (ccrcService == null) {
            return;
        }
        ccrcService.deActivate();
    }

    public static String detectText(String str) {
        if (mCcrcService == null) {
            return "";
        }
        TextRiskSample textRiskSample = new TextRiskSample(String.valueOf(System.currentTimeMillis()), new Text(str));
        String riskID = textRiskSample.getRiskID();
        textRiskSample.detect(mCcrcService);
        return riskID;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void initRealCCRC(Application application) {
        String appKey = ((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getAppKey();
        String ttid = ((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getTtid();
        CcrcContext.setAppKey(appKey);
        CcrcContext.init(application, ttid);
        mCcrcService = CcrcService.getService("ccrc_idle_comment_post_mtee_sns_unify_check");
        this.mCcrcBHService = CcrcBHService.getBHService("ccrc_idlefish_swindle_risk");
        OffClientWukongGuard.warmUp();
    }

    @Override // com.taobao.idlefish.protocol.ccrc.PCcrc
    public final void bhActivate() {
        CcrcBHService ccrcBHService = this.mCcrcBHService;
        if (ccrcBHService == null) {
            return;
        }
        ccrcBHService.activate();
    }

    @Override // com.taobao.idlefish.protocol.ccrc.PCcrc
    public final void bhDeactivate() {
        CcrcBHService ccrcBHService = this.mCcrcBHService;
        if (ccrcBHService == null) {
            return;
        }
        ccrcBHService.deActivate();
    }

    @Override // com.taobao.idlefish.protocol.ccrc.PCcrc
    public final void bhDetect(Map<String, String> map) {
        CcrcBHService ccrcBHService = this.mCcrcBHService;
        if (ccrcBHService == null) {
            return;
        }
        if (map == null) {
            map = new HashMap<>();
        }
        ccrcBHService.detect(map);
    }

    @ModuleNewInit(procPhase = {@ProcPhase(phase = "idle", process = {"main"})})
    public final void initCCRC(final Application application) {
        boolean z;
        String value = ((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("android_switch_high", "ccrc_downgrade", "");
        if (TextUtils.isEmpty(value)) {
            z = false;
        } else {
            try {
                z = Boolean.parseBoolean(value);
            } catch (Exception e) {
                a$$ExternalSyntheticOutline0.m("isCcrcDowngrade parse error:", e, "CcrcService", "Application");
                z = false;
            }
        }
        a$$ExternalSyntheticOutline0.m("isCcrcDowngrade, ret = ", z, "CcrcService", "Application");
        if (z) {
            return;
        }
        if (BuildConfig.OPEN_PRE_INSTALL.booleanValue()) {
            SoLoaderManager.inst().addListener("Ccrc", new SoModuleLoadListener() { // from class: com.idlefish.flutterbridge.ccrc.CcrcManager.1
                @Override // com.taobao.idlefish.soloader.SoModuleLoadListener
                public final void onSuccess(String str) {
                    if ("Ccrc".equals(str)) {
                        CcrcManager.this.initRealCCRC(application);
                    }
                }

                @Override // com.taobao.idlefish.soloader.SoModuleLoadListener
                public final void onError(int i) {
                }
            });
        } else {
            initRealCCRC(application);
        }
    }
}
