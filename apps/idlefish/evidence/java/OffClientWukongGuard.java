package com.idlefish.flutterbridge.ccrc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import com.alibaba.security.ccrc.enums.Mode;
import com.alibaba.security.ccrc.service.CcrcService;
import com.alibaba.security.ccrc.service.enums.WukongResultCode;
import com.alibaba.security.ccrc.service.interfaces.AbsWuKongDetectListener;
import com.alibaba.security.ccrc.service.interfaces.OnWuKongActivateListener;
import com.alibaba.security.ccrc.service.model.WukongActivateRiskResult;
import com.alibaba.security.ccrc.service.model.WukongDetectFinishRiskResult;
import com.alibaba.security.ccrc.service.model.WukongRiskUploadResult;
import com.alibaba.security.wukong.behavior.sample.BehaviorRiskSample;
import com.taobao.flowcustoms.afc.manager.AfcDataManager;
import com.taobao.idlefish.protocol.remoteconfig.PRemoteConfigs;
import com.taobao.idlefish.protocol.tbs.PTBS;
import com.taobao.idlefish.router.urlfirewall.UrlFirewallActivity;
import com.taobao.idlefish.xmc.XModuleCenter;
import defpackage.NCErrorCode$$ExternalSyntheticOutline0;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/* JADX INFO: loaded from: classes4.dex */
public final class OffClientWukongGuard {
    private int mActivateSeq;
    private CcrcService mCcrcService;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final HashMap mPendingDetects = new HashMap();
    private int mState = 0;
    private static final AtomicLong SAMPLE_COUNTER = new AtomicLong();
    private static final OffClientWukongGuard INSTANCE = new OffClientWukongGuard();

    /* JADX INFO: renamed from: com.idlefish.flutterbridge.ccrc.OffClientWukongGuard$4, reason: invalid class name */
    class AnonymousClass4 implements Runnable {
        final /* synthetic */ DecisionCallback val$callback;

        AnonymousClass4(DecisionCallback decisionCallback) {
            this.val$callback = decisionCallback;
        }

        @Override // java.lang.Runnable
        public final void run() {
            this.val$callback.onAllow();
        }
    }

    public interface DecisionCallback {
        void onAllow();

        void onBlock();

        void onFailOpen(String str);
    }

    private static final class PendingDetect {
        final DecisionCallback callback;
        final String jumpUrl;
        final String pendingId;
        String sampleId;
        final long startMs = System.currentTimeMillis();
        Runnable timeoutRunnable;

        PendingDetect(String str, DecisionCallback decisionCallback, String str2) {
            this.jumpUrl = str;
            this.callback = decisionCallback;
            this.pendingId = str2;
        }

        final String currentKey() {
            return TextUtils.isEmpty(this.sampleId) ? this.pendingId : this.sampleId;
        }
    }

    /* JADX INFO: renamed from: -$$Nest$mfinishDetect, reason: not valid java name */
    static void m530$$Nest$mfinishDetect(OffClientWukongGuard offClientWukongGuard, WukongDetectFinishRiskResult wukongDetectFinishRiskResult) {
        String str;
        if (wukongDetectFinishRiskResult != null) {
            offClientWukongGuard.getClass();
            str = wukongDetectFinishRiskResult.sampleID;
        } else {
            str = "";
        }
        synchronized (offClientWukongGuard) {
            PendingDetect pendingDetect = (PendingDetect) offClientWukongGuard.mPendingDetects.remove(str);
            if (pendingDetect == null) {
                return;
            }
            offClientWukongGuard.mMainHandler.removeCallbacks(pendingDetect.timeoutRunnable);
            WukongResultCode wukongResultCode = wukongDetectFinishRiskResult != null ? wukongDetectFinishRiskResult.wuKongResultCode : null;
            String strName = wukongResultCode != null ? wukongResultCode.name() : "";
            boolean z = wukongResultCode == WukongResultCode.DETECT_HIT_ACTION || wukongResultCode == WukongResultCode.DETECT_HIT_NO_ACTION || wukongResultCode == WukongResultCode.DETECT_NO_HIT;
            boolean z2 = wukongResultCode == WukongResultCode.DETECT_HIT_NO_ACTION;
            long jCurrentTimeMillis = System.currentTimeMillis() - pendingDetect.startMs;
            if (!z) {
                offClientWukongGuard.finishPending(pendingDetect, "detect_failed", strName);
                return;
            }
            if (z2) {
                track(jCurrentTimeMillis, "hit", pendingDetect.jumpUrl, strName);
                final DecisionCallback decisionCallback = pendingDetect.callback;
                if (decisionCallback == null) {
                    return;
                }
                INSTANCE.mMainHandler.post(new Runnable() { // from class: com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.5
                    @Override // java.lang.Runnable
                    public final void run() {
                        decisionCallback.onBlock();
                    }
                });
                return;
            }
            track(jCurrentTimeMillis, "pass", pendingDetect.jumpUrl, strName);
            DecisionCallback decisionCallback2 = pendingDetect.callback;
            if (decisionCallback2 == null) {
                return;
            }
            INSTANCE.mMainHandler.post(new AnonymousClass4(decisionCallback2));
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:7:0x000e  */
    /* JADX INFO: renamed from: -$$Nest$mhandleActivateFinish, reason: not valid java name */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    static void m531$$Nest$mhandleActivateFinish(com.idlefish.flutterbridge.ccrc.OffClientWukongGuard r2, com.alibaba.security.ccrc.service.model.WukongActivateRiskResult r3, int r4) {
        /*
            r0 = 0
            if (r3 == 0) goto Le
            r2.getClass()
            boolean r3 = r3.isSuccess()
            if (r3 == 0) goto Le
            r3 = 1
            goto Lf
        Le:
            r3 = 0
        Lf:
            monitor-enter(r2)
            int r1 = r2.mActivateSeq     // Catch: java.lang.Throwable -> L5e
            if (r4 == r1) goto L16
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L5e
            goto L5d
        L16:
            java.util.ArrayList r4 = new java.util.ArrayList     // Catch: java.lang.Throwable -> L5e
            java.util.HashMap r1 = r2.mPendingDetects     // Catch: java.lang.Throwable -> L5e
            java.util.Collection r1 = r1.values()     // Catch: java.lang.Throwable -> L5e
            r4.<init>(r1)     // Catch: java.lang.Throwable -> L5e
            if (r3 == 0) goto L27
            r0 = 2
            r2.mState = r0     // Catch: java.lang.Throwable -> L5e
            goto L2e
        L27:
            r2.mState = r0     // Catch: java.lang.Throwable -> L5e
            java.util.HashMap r0 = r2.mPendingDetects     // Catch: java.lang.Throwable -> L5e
            r0.clear()     // Catch: java.lang.Throwable -> L5e
        L2e:
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L5e
            if (r3 == 0) goto L45
            java.util.Iterator r3 = r4.iterator()
        L35:
            boolean r4 = r3.hasNext()
            if (r4 == 0) goto L5d
            java.lang.Object r4 = r3.next()
            com.idlefish.flutterbridge.ccrc.OffClientWukongGuard$PendingDetect r4 = (com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.PendingDetect) r4
            r2.detect(r4)
            goto L35
        L45:
            java.util.Iterator r3 = r4.iterator()
        L49:
            boolean r4 = r3.hasNext()
            if (r4 == 0) goto L5d
            java.lang.Object r4 = r3.next()
            com.idlefish.flutterbridge.ccrc.OffClientWukongGuard$PendingDetect r4 = (com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.PendingDetect) r4
            java.lang.String r0 = "activate_failed"
            java.lang.String r1 = ""
            r2.finishPending(r4, r0, r1)
            goto L49
        L5d:
            return
        L5e:
            r3 = move-exception
            monitor-exit(r2)     // Catch: java.lang.Throwable -> L5e
            throw r3
        */
        throw new UnsupportedOperationException("Method not decompiled: com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.m531$$Nest$mhandleActivateFinish(com.idlefish.flutterbridge.ccrc.OffClientWukongGuard, com.alibaba.security.ccrc.service.model.WukongActivateRiskResult, int):void");
    }

    private OffClientWukongGuard() {
    }

    private void activate(final int i) {
        CcrcService ccrcService;
        synchronized (this) {
            ccrcService = this.mCcrcService;
        }
        if (ccrcService == null) {
            handleActivateFail(i, "not_activated");
            return;
        }
        try {
            ccrcService.activate(new CcrcService.Config.Builder().setPid("PID_OC_" + UUID.randomUUID().toString()).setMode(Mode.DEFAULT).build(), new OnWuKongActivateListener() { // from class: com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.2
                @Override // com.alibaba.security.ccrc.service.interfaces.OnWuKongActivateListener
                public final void onActivateFinish(WukongActivateRiskResult wukongActivateRiskResult) {
                    OffClientWukongGuard.m531$$Nest$mhandleActivateFinish(OffClientWukongGuard.this, wukongActivateRiskResult, i);
                }
            });
        } catch (Throwable unused) {
            handleActivateFail(i, "activate_exception");
        }
    }

    private static String buildSampleId(CcrcService ccrcService) {
        String pid = ccrcService.getPid();
        if (TextUtils.isEmpty(pid)) {
            pid = "ccrc_idlefish_off_client_risk";
        }
        StringBuilder sbM4m = NCErrorCode$$ExternalSyntheticOutline0.m4m("SID_", pid, "_");
        sbM4m.append(System.currentTimeMillis());
        sbM4m.append("_");
        sbM4m.append(SAMPLE_COUNTER.incrementAndGet());
        return sbM4m.toString();
    }

    public static void check(String str, DecisionCallback decisionCallback) {
        boolean zEqualsIgnoreCase;
        String str2;
        final PendingDetect pendingDetect;
        boolean z;
        int i;
        OffClientWukongGuard offClientWukongGuard = INSTANCE;
        offClientWukongGuard.getClass();
        if (isFallbackEnabled()) {
            track(0L, "fallback", str, "");
            offClientWukongGuard.mMainHandler.post(new AnonymousClass4(decisionCallback));
            return;
        }
        boolean z2 = false;
        try {
            zEqualsIgnoreCase = "true".equalsIgnoreCase(((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("android_switch_high", "ccrc_off_client_risk_downgrade", ""));
        } catch (Throwable unused) {
            zEqualsIgnoreCase = false;
        }
        if (zEqualsIgnoreCase) {
            track(0L, "ccrc_downgrade", str, "");
            INSTANCE.mMainHandler.post(new AnonymousClass4(decisionCallback));
            return;
        }
        if (TextUtils.isEmpty(str)) {
            track(0L, "empty_url", str, "");
            notifyFailOpen(decisionCallback, "empty_url");
            return;
        }
        safeUrl(str);
        synchronized (offClientWukongGuard) {
            offClientWukongGuard.initServiceLocked();
            str2 = null;
            if (offClientWukongGuard.mCcrcService == null) {
                i = 0;
                z = false;
                str2 = "not_activated";
                pendingDetect = null;
            } else {
                pendingDetect = new PendingDetect(str, decisionCallback, "PENDING_" + System.currentTimeMillis() + "_" + SAMPLE_COUNTER.incrementAndGet());
                offClientWukongGuard.mPendingDetects.put(pendingDetect.currentKey(), pendingDetect);
                int i2 = offClientWukongGuard.mState;
                z = true;
                if (i2 == 2) {
                    i = 0;
                } else {
                    if (i2 != 1) {
                        offClientWukongGuard.mState = 1;
                        i = offClientWukongGuard.mActivateSeq + 1;
                        offClientWukongGuard.mActivateSeq = i;
                    } else {
                        i = 0;
                        z = false;
                    }
                    z2 = z;
                    z = false;
                }
            }
        }
        if (str2 != null) {
            track(0L, str2, str, "");
            notifyFailOpen(decisionCallback, str2);
            return;
        }
        pendingDetect.timeoutRunnable = new Runnable() { // from class: com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.3
            @Override // java.lang.Runnable
            public final void run() {
                boolean z3;
                synchronized (OffClientWukongGuard.this) {
                    z3 = OffClientWukongGuard.this.mPendingDetects.remove(pendingDetect.currentKey()) == pendingDetect;
                    if (z3 && OffClientWukongGuard.this.mState == 1 && OffClientWukongGuard.this.mPendingDetects.isEmpty()) {
                        OffClientWukongGuard.this.mState = 0;
                    }
                }
                if (z3) {
                    long jCurrentTimeMillis = System.currentTimeMillis();
                    PendingDetect pendingDetect2 = pendingDetect;
                    OffClientWukongGuard.track(jCurrentTimeMillis - pendingDetect2.startMs, "timeout", pendingDetect2.jumpUrl, "");
                    OffClientWukongGuard.notifyFailOpen(pendingDetect.callback, "timeout");
                }
            }
        };
        long j = 200;
        try {
            long j2 = Long.parseLong(((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("android_switch_high", "off_client_wukong_timeout_ms", String.valueOf(200L)));
            if (j2 > 0) {
                j = j2;
            }
        } catch (Throwable unused2) {
        }
        offClientWukongGuard.mMainHandler.postDelayed(pendingDetect.timeoutRunnable, j);
        if (z2) {
            offClientWukongGuard.activate(i);
        }
        if (z) {
            offClientWukongGuard.detect(pendingDetect);
        }
    }

    private void detect(PendingDetect pendingDetect) {
        if (pendingDetect == null) {
            return;
        }
        synchronized (this) {
            if (this.mPendingDetects.get(pendingDetect.currentKey()) != pendingDetect) {
                return;
            }
            CcrcService ccrcService = this.mCcrcService;
            if (ccrcService == null) {
                finishPending(pendingDetect, "not_activated", "");
                return;
            }
            try {
                safeUrl(pendingDetect.jumpUrl);
                String strBuildSampleId = buildSampleId(ccrcService);
                synchronized (this) {
                    if (this.mPendingDetects.remove(pendingDetect.currentKey()) != pendingDetect) {
                        return;
                    }
                    pendingDetect.sampleId = strBuildSampleId;
                    this.mPendingDetects.put(strBuildSampleId, pendingDetect);
                    String str = pendingDetect.sampleId;
                    String str2 = pendingDetect.jumpUrl;
                    HashMap map = new HashMap();
                    map.put(AfcDataManager.JUMP_URL, str2);
                    new BehaviorRiskSample(str, map).detect(ccrcService, false);
                }
            } catch (Throwable unused) {
                finishPending(pendingDetect, "detect_exception", "");
            }
        }
    }

    private void finishPending(PendingDetect pendingDetect, String str, String str2) {
        synchronized (this) {
            if (pendingDetect != null) {
                if (this.mPendingDetects.get(pendingDetect.currentKey()) == pendingDetect) {
                    this.mPendingDetects.remove(pendingDetect.currentKey());
                }
            }
        }
        this.mMainHandler.removeCallbacks(pendingDetect.timeoutRunnable);
        track(System.currentTimeMillis() - pendingDetect.startMs, str, pendingDetect.jumpUrl, str2);
        notifyFailOpen(pendingDetect.callback, str);
    }

    private void handleActivateFail(int i, String str) {
        synchronized (this) {
            if (i != this.mActivateSeq) {
                return;
            }
            this.mState = 0;
            ArrayList arrayList = new ArrayList(this.mPendingDetects.values());
            this.mPendingDetects.clear();
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                finishPending((PendingDetect) it.next(), str, "");
            }
        }
    }

    private synchronized void initServiceLocked() {
        if (this.mCcrcService != null) {
            return;
        }
        try {
            CcrcService service = CcrcService.getService("ccrc_idlefish_off_client_risk");
            this.mCcrcService = service;
            if (service == null) {
                return;
            }
            try {
                service.prepare();
            } catch (Throwable unused) {
            }
            this.mCcrcService.setWuKongDetectListener(new AbsWuKongDetectListener() { // from class: com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.1
                @Override // com.alibaba.security.ccrc.service.interfaces.OnWuKongDetectListener
                public final void onDetectFinish(WukongDetectFinishRiskResult wukongDetectFinishRiskResult) {
                    OffClientWukongGuard.m530$$Nest$mfinishDetect(OffClientWukongGuard.this, wukongDetectFinishRiskResult);
                }

                @Override // com.alibaba.security.ccrc.service.interfaces.OnWuKongDetectListener
                public final void onRiskUpload(WukongRiskUploadResult wukongRiskUploadResult) {
                }
            });
        } catch (Throwable unused2) {
            this.mCcrcService = null;
        }
    }

    public static boolean isFallbackEnabled() {
        try {
            return ((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("android_switch_high", "off_client_wukong_fallback", false);
        } catch (Throwable unused) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void notifyFailOpen(final DecisionCallback decisionCallback, final String str) {
        if (decisionCallback == null) {
            return;
        }
        INSTANCE.mMainHandler.post(new Runnable() { // from class: com.idlefish.flutterbridge.ccrc.OffClientWukongGuard.6
            @Override // java.lang.Runnable
            public final void run() {
                decisionCallback.onFailOpen(str);
            }
        });
    }

    public static void openBlockPage(Context context, String str) {
        String currentPageName;
        Context applicationContext = XModuleCenter.getApplication() != null ? XModuleCenter.getApplication().getApplicationContext() : null;
        if (context == null) {
            context = applicationContext;
        }
        if (context == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setClass(context, UrlFirewallActivity.class);
        intent.putExtra("url", str);
        try {
            currentPageName = ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).getCurrentPageName();
        } catch (Throwable unused) {
            currentPageName = "Page_xyUnknown";
        }
        intent.putExtra("prevPageName", currentPageName);
        if (!(context instanceof Activity)) {
            intent.addFlags(268435456);
        }
        context.startActivity(intent);
    }

    private static void safeUrl(String str) {
        try {
            Uri uri = Uri.parse(str);
            uri.getScheme();
            uri.getHost();
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void track(long j, String str, String str2, String str3) {
        try {
            HashMap map = new HashMap();
            map.put("result", str);
            if (str2 == null) {
                str2 = "";
            }
            map.put(AfcDataManager.JUMP_URL, str2);
            if (!TextUtils.isEmpty(str3)) {
                if (str3 == null) {
                    str3 = "";
                }
                map.put("code", str3);
            }
            map.put("costMs", String.valueOf(j));
            ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).commitEvent(19999, "ccrc_off_client_risk", "ccrc_result", "", map);
        } catch (Throwable unused) {
        }
    }

    public static void warmUp() {
        boolean zEqualsIgnoreCase;
        int i;
        OffClientWukongGuard offClientWukongGuard = INSTANCE;
        offClientWukongGuard.getClass();
        if (isFallbackEnabled()) {
            return;
        }
        try {
            zEqualsIgnoreCase = "true".equalsIgnoreCase(((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("android_switch_high", "ccrc_off_client_risk_downgrade", ""));
        } catch (Throwable unused) {
            zEqualsIgnoreCase = false;
        }
        if (zEqualsIgnoreCase) {
            return;
        }
        synchronized (offClientWukongGuard) {
            offClientWukongGuard.initServiceLocked();
            if (offClientWukongGuard.mCcrcService != null && (i = offClientWukongGuard.mState) != 2 && i != 1) {
                offClientWukongGuard.mState = 1;
                int i2 = offClientWukongGuard.mActivateSeq + 1;
                offClientWukongGuard.mActivateSeq = i2;
                offClientWukongGuard.activate(i2);
            }
        }
    }
}
