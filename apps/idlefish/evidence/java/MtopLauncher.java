package com.taobao.android.remoteobject.easy;

import android.app.Activity;
import android.app.Application;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import anet.channel.GlobalAppRuntimeInfo;
import anet.channel.SessionCenter;
import anet.channel.entity.ENV;
import anet.channel.request.Request;
import anetwork.channel.config.NetworkConfigCenter;
import anetwork.channel.interceptor.Callback;
import anetwork.channel.interceptor.Interceptor;
import anetwork.channel.interceptor.InterceptorManager;
import com.ali.user.mobile.model.TokenType;
import com.ali.user.open.mtop.UccRemoteLogin;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.Feature;
import com.alivc.live.biz.manager.c$$ExternalSyntheticOutline0;
import com.idlefish.blink.ExecNewInit;
import com.idlefish.blink.FishNewModule;
import com.idlefish.blink.ModuleNewInit;
import com.idlefish.blink.ProcPhase;
import com.taobao.android.dispatchqueue.QueueType;
import com.taobao.android.dispatchqueue.queue.CustomQueue;
import com.taobao.android.remoteobject.core.HttpResponseInterceptor;
import com.taobao.android.remoteobject.easy.JustEasy;
import com.taobao.android.remoteobject.easy.debug.FishMtopDebugFilter;
import com.taobao.android.remoteobject.easy.debug.MtopCheckFilter;
import com.taobao.android.remoteobject.easy.filter.BizTimeRecordFilter;
import com.taobao.android.remoteobject.easy.network.interceptor.MtopDecodeInterceptor;
import com.taobao.android.remoteobject.easy.priority.PriorityRequestExecutor;
import com.taobao.android.remoteobject.easy.priority.PriorityRequestMgr;
import com.taobao.android.remoteobject.mtop.MtopRemoteCallback;
import com.taobao.android.remoteobject.mtopsdk.MtopSDKInfo;
import com.taobao.android.remoteobject.mtopsdk.MtopSDKInitConfig;
import com.taobao.android.remoteobject.mtopsdk.ext.EasyMtopExtSDK;
import com.taobao.android.remoteobject.mtopsdk.ext.MtopExtSDKHandler;
import com.taobao.android.remoteobject.sync.Sync;
import com.taobao.fleamarket.business.user_growth.alive.window_channel.WindowChannelAliveApplication;
import com.taobao.idlefish.basecommon.utils.ProcessUtil;
import com.taobao.idlefish.fish_log.FishLog;
import com.taobao.idlefish.fish_log.IssueReporter;
import com.taobao.idlefish.preinstall.glueapi.weex.WeexGlueSingleton;
import com.taobao.idlefish.protocol.apibean.ApiEnv;
import com.taobao.idlefish.protocol.appinfo.Division;
import com.taobao.idlefish.protocol.appinfo.PApplicationUtil;
import com.taobao.idlefish.protocol.env.PEnv;
import com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor;
import com.taobao.idlefish.protocol.lifecycle.PActivityLifecycleContext;
import com.taobao.idlefish.protocol.localization.PLocalization;
import com.taobao.idlefish.protocol.net.ApiCallBack;
import com.taobao.idlefish.protocol.net.ApiConstant;
import com.taobao.idlefish.protocol.net.PApiContext;
import com.taobao.idlefish.protocol.net.PClientInfo;
import com.taobao.idlefish.protocol.net.ResponseParameter;
import com.taobao.idlefish.protocol.net.api.BaseApiProtocol;
import com.taobao.idlefish.protocol.net.api.Phase;
import com.taobao.idlefish.protocol.remoteconfig.OnValueFetched;
import com.taobao.idlefish.protocol.remoteconfig.PRemoteConfigs;
import com.taobao.idlefish.protocol.speedup.PIFSpeed;
import com.taobao.idlefish.protocol.tbs.PBehavirCollector;
import com.taobao.idlefish.protocol.tbs.PTBS;
import com.taobao.idlefish.protocol.tbs.PUtCollector;
import com.taobao.idlefish.protocol.utils.DpaJumpUrlUtil;
import com.taobao.idlefish.xframework.util.StringUtil;
import com.taobao.idlefish.xmc.XModuleCenter;
import com.taobao.orange.util.FileUtil;
import com.taobao.tao.messagekit.core.Contants.Constant;
import com.taobao.tao.remotebusiness.login.RemoteLogin;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import mtopsdk.common.log.LogAdapter;
import mtopsdk.common.log.TLogAdapterImpl;
import mtopsdk.common.util.TBSdkLog;
import mtopsdk.config.MtopConfigOrangeListenerImpl;
import mtopsdk.framework.domain.MtopContext;
import mtopsdk.framework.filter.IAfterFilter;
import mtopsdk.framework.manager.FilterManager;
import mtopsdk.framework.manager.impl.AbstractFilterManager;
import mtopsdk.mtop.domain.EnvModeEnum;
import mtopsdk.mtop.domain.MtopResponse;
import mtopsdk.mtop.global.MtopConfig;
import mtopsdk.mtop.intf.Mtop;
import mtopsdk.mtop.intf.MtopAccountSiteUtils;
import mtopsdk.mtop.intf.MtopSetting;
import mtopsdk.mtop.intf.MtopUnitStrategy;
import mtopsdk.xstate.XState;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes6.dex */
@FishNewModule(protocol = "com.taobao.idlefish.protocol.net.PApiContext")
public class MtopLauncher implements PApiContext {
    private static final String SP_GOOFISH_API_KEY = "apis";
    private static final String SP_GOOFISH_API_MODULE = "goofish_api_config";
    public static final String TAG = "MtopLauncher";
    private static FilterManager mMtopFilterManager;
    private Interceptor mOmegaInterceptor = null;
    private Interceptor mJumpUrlInterceptor = null;
    private Interceptor mHomeFeedsDpaInterceptor = null;
    private Interceptor mCoordinatesInterceptor = null;
    private Interceptor mLocationInterceptor = null;
    private final ClientHeaderInterceptor mClientHeaderInterceptor = new ClientHeaderInterceptor();

    private static class CoordinatesInterceptor implements Interceptor {
        private CoordinatesInterceptor() {
        }

        /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:20:0x0071 -> B:24:0x0084). Please report as a decompilation issue!!! */
        @Override // anetwork.channel.interceptor.Interceptor
        public Future intercept(Interceptor.Chain chain) {
            boolean zBooleanValue;
            Double d;
            Request request = chain.request();
            Callback callback = chain.callback();
            if (request != null && ("acs.m.goofish.com".equals(request.getHost()) || "acs.wapa.goofish.com".equals(request.getHost()))) {
                try {
                    Division cacheDivision = ((PApplicationUtil) XModuleCenter.moduleForProtocol(PApplicationUtil.class)).getFishApplicationInfo().getCacheDivision();
                    Mtop mtopInstance = Mtop.instance(Mtop.Id.INNER, MtopExtSDKHandler.getMtopExtSDKDefault().getAndroidContext());
                    if (cacheDivision == null || cacheDivision.lat == null || (d = cacheDivision.lon) == null) {
                        mtopInstance.setCoordinates("0", "0");
                    } else {
                        mtopInstance.setCoordinates(d.toString(), cacheDivision.lat.toString());
                    }
                } finally {
                    if (!zBooleanValue) {
                    }
                }
            }
            return chain.proceed(request, callback);
        }
    }

    private static class HomeFeedsDpaInterceptor implements Interceptor {
        private HomeFeedsDpaInterceptor() {
        }

        @Override // anetwork.channel.interceptor.Interceptor
        public Future intercept(Interceptor.Chain chain) {
            Request request = chain.request();
            Callback callback = chain.callback();
            String urlString = request != null ? request.getUrlString() : null;
            String homeFeedsFakeJumpUrl = DpaJumpUrlUtil.getHomeFeedsFakeJumpUrl();
            if (homeFeedsFakeJumpUrl != null && urlString != null && urlString.contains("mtop.taobao.idlehome.home.nextfresh")) {
                try {
                    if (URLDecoder.decode(new String(request.getBodyBytes())).contains("\"pageNumber\":1,")) {
                        DpaJumpUrlUtil.disableHomeFeedsFakeJumpUrl();
                        String strEncode = Uri.encode(homeFeedsFakeJumpUrl);
                        if (strEncode.length() < 2048) {
                            Request.Builder builderNewBuilder = request.newBuilder();
                            builderNewBuilder.addHeader(DpaJumpUrlUtil.CUSTOMS_HEADER_URL, strEncode);
                            request = builderNewBuilder.build();
                        } else {
                            FishLog.e("Dpa", "HomeFeedsDpaInterceptor", "DpaJumpUrl length greater than 2048. url=".concat(urlString));
                        }
                    }
                } catch (Throwable th) {
                    if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
                        throw th;
                    }
                    ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).errorLog("omega_intercept_mtop", Log.getStackTraceString(th));
                }
            }
            return chain.proceed(request, callback);
        }
    }

    private static class JumpUrlInterceptor implements Interceptor {
        private JumpUrlInterceptor() {
        }

        /* JADX WARN: Removed duplicated region for block: B:23:0x007f  */
        /* JADX WARN: Removed duplicated region for block: B:39:0x0082 A[EXC_TOP_SPLITTER, SYNTHETIC] */
        @Override // anetwork.channel.interceptor.Interceptor
        /*
            Code decompiled incorrectly, please refer to instructions dump.
            To view partially-correct add '--show-bad-code' argument
        */
        public java.util.concurrent.Future intercept(anetwork.channel.interceptor.Interceptor.Chain r7) {
            /*
                Method dump skipped, instruction units count: 235
                To view this dump add '--comments-level debug' option
            */
            throw new UnsupportedOperationException("Method not decompiled: com.taobao.android.remoteobject.easy.MtopLauncher.JumpUrlInterceptor.intercept(anetwork.channel.interceptor.Interceptor$Chain):java.util.concurrent.Future");
        }
    }

    private static class LocationInterceptor implements Interceptor {
        private LocationInterceptor() {
        }

        @Override // anetwork.channel.interceptor.Interceptor
        public Future intercept(Interceptor.Chain chain) {
            PLocalization pLocalization;
            Map<String, String> headers;
            Request request = chain.request();
            Callback callback = chain.callback();
            if (request != null && ("g-acs.m.goofish.com".equals(request.getHost()) || "g-acs.wapa.goofish.com".equals(request.getHost()) || "acs.m.goofish.com".equals(request.getHost()) || "acs.wapa.goofish.com".equals(request.getHost()) || "acs.m.taobao.com".equals(request.getHost()) || "acs.wapa.taobao.com".equals(request.getHost()))) {
                try {
                    if (XModuleCenter.moduleReady(PLocalization.class) && (pLocalization = (PLocalization) XModuleCenter.moduleForProtocol(PLocalization.class)) != null && pLocalization.isSupported().booleanValue() && (headers = pLocalization.getHeaders()) != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            if (!TextUtils.isEmpty(entry.getValue()) && !request.getHeaders().containsKey(entry.getKey())) {
                                Request.Builder builderNewBuilder = request.newBuilder();
                                builderNewBuilder.addHeader(entry.getKey(), entry.getValue());
                                request = builderNewBuilder.build();
                            }
                        }
                    }
                } catch (Throwable th) {
                    if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
                        throw th;
                    }
                    ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).errorLog("location_intercept_mtop", Log.getStackTraceString(th));
                }
            }
            return chain.proceed(request, callback);
        }
    }

    static class LogAdapterWrapper implements LogAdapter {
        private LogAdapter mReal;

        public LogAdapterWrapper(LogAdapter logAdapter) {
            this.mReal = logAdapter;
            if (logAdapter == null) {
                this.mReal = new TLogAdapterImpl();
            }
        }

        @Override // mtopsdk.common.log.LogAdapter
        public String getLogLevel() {
            return this.mReal.getLogLevel();
        }

        @Override // mtopsdk.common.log.LogAdapter
        public void printLog(int i, String str, String str2, Throwable th) {
            this.mReal.printLog(i, str, str2, th);
            if (str2 == null || !str2.contains("[login]call login")) {
                return;
            }
            FishLog.e("login", "remote login", "[login]call login happened. ", new Exception());
            IssueReporter issueReporterReason = FishLog.newIssue(TokenType.LOGIN).reason("remote_login");
            issueReporterReason.args("hit", "true");
            issueReporterReason.report();
        }

        @Override // mtopsdk.common.log.LogAdapter
        public void traceLog(String str, String str2) {
            this.mReal.traceLog(str, str2);
        }
    }

    private static class OmegaAfterFiler implements IAfterFilter {
        private OmegaAfterFiler() {
        }

        @Override // mtopsdk.framework.filter.IAfterFilter
        public String doAfter(MtopContext mtopContext) {
            if (mtopContext == null || mtopContext.mtopRequest == null || mtopContext.mtopResponse == null) {
                return "CONTINUE";
            }
            try {
                HashMap map = new HashMap();
                String apiName = mtopContext.mtopRequest.getApiName();
                String version = mtopContext.mtopRequest.getVersion();
                map.put("request", mtopContext.mtopRequest.getData());
                map.put("response", mtopContext.mtopResponse.getBytedata() != null ? new String(mtopContext.mtopResponse.getBytedata()) : "{}");
                if (XModuleCenter.moduleReady(PBehavirCollector.class)) {
                    ((PBehavirCollector) XModuleCenter.moduleForProtocol(PBehavirCollector.class)).commitRequest(apiName, version, (String) map.get("request"), (String) map.get("response"));
                }
                if (!XModuleCenter.moduleReady(PUtCollector.class)) {
                    return "CONTINUE";
                }
                ((PUtCollector) XModuleCenter.moduleForProtocol(PUtCollector.class)).sendOmegaEvent("omega_mtop_result", apiName, version, map);
                return "CONTINUE";
            } catch (Throwable th) {
                if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
                    throw th;
                }
                ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).errorLog("omega_mtop_result", Log.getStackTraceString(th));
                return "CONTINUE";
            }
        }

        @Override // mtopsdk.framework.filter.IMtopFilter
        @NonNull
        public String getName() {
            return "fish.OmegaAfterFilter";
        }
    }

    private static class OmegaInterceptor implements Interceptor {
        private OmegaInterceptor() {
        }

        @Override // anetwork.channel.interceptor.Interceptor
        public Future intercept(Interceptor.Chain chain) {
            Request request = chain.request();
            Callback callback = chain.callback();
            if (request != null && ("acs.m.taobao.com".equals(request.getHost()) || "acs.wapa.taobao.com".equals(request.getHost()))) {
                try {
                    Map<String, String> clientHeader = ((PClientInfo) XModuleCenter.moduleForProtocol(PClientInfo.class)).getClientHeader();
                    if (clientHeader != null && clientHeader.containsKey("EagleEye-UserData") && !request.getHeaders().containsKey("EagleEye-UserData")) {
                        Request.Builder builderNewBuilder = request.newBuilder();
                        builderNewBuilder.addHeader("EagleEye-UserData", clientHeader.get("EagleEye-UserData"));
                        request = builderNewBuilder.build();
                    }
                } catch (Throwable th) {
                    if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
                        throw th;
                    }
                    ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).errorLog("omega_intercept_mtop", Log.getStackTraceString(th));
                }
            }
            return chain.proceed(request, callback);
        }
    }

    private static void addMtopFilter() {
        try {
            Mtop mtopInstance = Mtop.instance(MtopExtSDKHandler.getMtopExtSDKDefault().getAndroidContext());
            if (mtopInstance == null || mtopInstance.getMtopConfig() == null || mtopInstance.getMtopConfig().filterManager == null || mMtopFilterManager == mtopInstance.getMtopConfig().filterManager) {
                return;
            }
            AbstractFilterManager abstractFilterManager = mtopInstance.getMtopConfig().filterManager;
            MtopFishLog mtopFishLog = new MtopFishLog();
            abstractFilterManager.addBefore(mtopFishLog.newBeforeFilter());
            abstractFilterManager.addAfter(mtopFishLog.newAfterFilter());
            abstractFilterManager.addBefore(MtopLuxuryFilter.newBeforeFilter());
            abstractFilterManager.addAfter(MtopLuxuryFilter.newAfterFilter());
            abstractFilterManager.addBefore(BizTimeRecordFilter.newBeforeFilter());
            abstractFilterManager.addAfter(BizTimeRecordFilter.newAfterFilter());
            if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue() || ((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).isSwitchable()) {
                abstractFilterManager.addBefore(MtopCheckFilter.newBeforeFilter());
                abstractFilterManager.addAfter(MtopCheckFilter.newAfterFilter());
                FishMtopDebugFilter.insertBeforeExecuteCall(mtopInstance.getMtopConfig().filterManager);
            }
            mtopInstance.getMtopConfig().filterManager.addAfter(new OmegaAfterFiler());
            mMtopFilterManager = mtopInstance.getMtopConfig().filterManager;
        } catch (Throwable th) {
            if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
                throw th;
            }
            ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).errorLog("check_omega_intercept", Log.getStackTraceString(th));
        }
    }

    private void checkTopActivity() {
        if (XModuleCenter.moduleReady(PActivityLifecycleContext.class)) {
            Activity currentActivityQ = ((PActivityLifecycleContext) XModuleCenter.moduleForProtocol(PActivityLifecycleContext.class)).getCurrentActivityQ();
            String simpleName = currentActivityQ != null ? currentActivityQ.getClass().getSimpleName() : "null";
            if (currentActivityQ == null || TextUtils.equals("InitActivity", simpleName) || TextUtils.equals("MainActivity", simpleName)) {
                return;
            }
            FishLog.w(MtopSend.TAG__, TAG, "top activity=" + simpleName + ", release request queue");
            PriorityRequestExecutor.inst().releaseQueue();
        }
    }

    public static void firstMtopRequest(Application application) {
        new Thread() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.3
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                Sync.getInstance().start(MtopExtSDKHandler.getMtopExtSDKDefault());
            }
        }.start();
    }

    private static void initMtopLog() {
        MtopConfig.logAdapterImpl = new LogAdapterWrapper(MtopConfig.logAdapterImpl);
    }

    private static void initMtopSdk(Application application) {
        ENV env;
        initMtopLog();
        String ttid = ((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getTtid();
        MtopSetting.setMtopDomain("acs.m.goofish.com", "acs.wapa.goofish.com", "acs.wapatest.goofish.com");
        SystemClock.uptimeMillis();
        XState.init(application);
        SystemClock.uptimeMillis();
        Mtop mtopInstance = Mtop.instance(application);
        MtopAccountSiteUtils.bindInstanceId("xianyu");
        mtopInstance.registerTtid(ttid);
        MtopSetting.setMtopDomain(Mtop.MTOP_ID_TAOBAO, MtopUnitStrategy.GUIDE_ONLINE_DOMAIN, MtopUnitStrategy.GUIDE_PRE_DOMAIN, MtopUnitStrategy.GUIDE_DAILY_DOMAIN);
        Mtop mtopInstance2 = Mtop.instance(Mtop.MTOP_ID_TAOBAO, application);
        RemoteLogin.setLoginImpl(mtopInstance2, UccRemoteLogin.getUccLoginImplWithSite("taobao"));
        mtopInstance2.registerTtid(ttid);
        MtopAccountSiteUtils.bindInstanceId(Mtop.MTOP_ID_TAOBAO, "taobao");
        WeexGlueSingleton.getOrFake().wxMtopGlobalConfigSetDefaultAccountSite("taobao");
        GlobalAppRuntimeInfo.setTtid(((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getTtid());
        ApiEnv apiEnv = (ApiEnv) ((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getTypeBasedEnv(ApiEnv.class);
        if (apiEnv == ApiEnv.Daily) {
            env = ENV.TEST;
            EnvModeEnum envModeEnum = EnvModeEnum.TEST;
            mtopInstance.switchEnvMode(envModeEnum);
            mtopInstance2.switchEnvMode(envModeEnum);
        } else if (apiEnv == ApiEnv.PreRelease) {
            env = ENV.PREPARE;
            EnvModeEnum envModeEnum2 = EnvModeEnum.PREPARE;
            mtopInstance.switchEnvMode(envModeEnum2);
            mtopInstance2.switchEnvMode(envModeEnum2);
        } else {
            env = ENV.ONLINE;
        }
        SessionCenter.init(application, ((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getAppKey(), env);
        BaseApiProtocol.app = application;
        MtopSetting.setMtopConfigListener(new MtopConfigOrangeListenerImpl());
        MtopSetting.setAppVersion(((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getVersion());
        MtopSDKInitConfig.Builder builderNewBuilder = MtopSDKInitConfig.newBuilder();
        builderNewBuilder.androidContext(application).onlineIdx(0).dailyIdx(2).env((ApiEnv) ((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getTypeBasedEnv(ApiEnv.class)).needLog(((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()).needSPDY(((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).needSPDY()).needPost(true);
        MtopSDKInitConfig mtopSDKInitConfigBuild = builderNewBuilder.build();
        MtopExtSDKHandler<MtopSDKInfo, Object, MtopRemoteCallback> mtopExtSDKDefault = MtopExtSDKHandler.getMtopExtSDKDefault();
        mtopExtSDKDefault.initConfig(mtopSDKInitConfigBuild);
        if (((PIFSpeed) XModuleCenter.moduleForProtocol(PIFSpeed.class)).optimizeMtopPreload()) {
            mtopExtSDKDefault.setCallbackQueue(new CustomQueue("mtop-callback-queue", QueueType.SERIAL));
        }
        JustEasy.setContextFactory(new JustEasy.EasyContextFactory() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.2
            @Override // com.taobao.android.remoteobject.easy.JustEasy.EasyContextFactory
            public EasyContext create() {
                return EasyMtopExtSDK.get();
            }
        });
        addMtopFilter();
        if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
            TBSdkLog.setTLogEnabled(false);
            TBSdkLog.setLogEnable(TBSdkLog.LogEnable.DebugEnable);
        } else {
            TBSdkLog.setTLogEnabled(true);
            TBSdkLog.setLogEnable(TBSdkLog.LogEnable.ErrorEnable);
        }
    }

    @ExecNewInit(initDepends = {"com.taobao.idlefish.protocol.net.PApiContext", "com.taobao.idlefish.protocol.remoteconfig.PRemoteConfigs", "com.taobao.idlefish.protocol.tbs.PTBS", "com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor", "com.taobao.idlefish.protocol.net.PClientInfo", "com.taobao.idlefish.protocol.traffic.PTrafficStat", "com.taobao.idlefish.protocol.imei.PImei"}, procPhase = {@ProcPhase(phase = "common", process = {"main", WindowChannelAliveApplication.SIMPLE_PROCESS_NAME, "channel", "recoveryModel", Constant.Monitor.PULL_RATE, "widgetProvider"})})
    public static void mtopPropertiesInit(Application application) {
        if (WindowChannelAliveApplication.SIMPLE_PROCESS_NAME.equals(XModuleCenter.getSimpleProcessName())) {
            return;
        }
        mtopPropertiesInitSDK(application);
        firstMtopRequest(application);
    }

    public static void mtopPropertiesInitSDK(Application application) {
        if (((PIFSpeed) XModuleCenter.moduleForProtocol(PIFSpeed.class)).openANETBindingOpt()) {
            NetworkConfigCenter.setBindServiceOptimize(true);
        }
        MtopExtSDKHandler.getMtopExtSDKDefault().setHttpResponseInterceptor(new HttpResponseInterceptor() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.4
            @Override // com.taobao.android.remoteobject.core.HttpResponseInterceptor
            public void onMtopResponse(MtopResponse mtopResponse, JSONObject jSONObject) {
                if (mtopResponse != null) {
                    ((PSecurityInterceptor) XModuleCenter.moduleForProtocol(PSecurityInterceptor.class)).interceptor(jSONObject, mtopResponse.getRetCode());
                }
                MtopDecodeInterceptor.instance().interceptor(mtopResponse);
            }

            @Override // com.taobao.android.remoteobject.core.HttpResponseInterceptor
            public void onHeaderReturn(Map<String, List<String>> map) {
            }
        });
        PRemoteConfigs pRemoteConfigs = (PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class);
        pRemoteConfigs.fetchValue("wuaApis", ApiConstant.WUA_APIS_DEF, new OnValueFetched() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.5
            @Override // com.taobao.idlefish.protocol.remoteconfig.OnValueFetched
            public void onFetchFailed(Object obj) {
                MtopLauncher.updateWuaApis(String.valueOf(obj));
            }

            @Override // com.taobao.idlefish.protocol.remoteconfig.OnValueFetched
            public void onFetched(String str) {
                MtopLauncher.updateWuaApis(str);
            }
        });
        pRemoteConfigs.fetchValue("loginApis", null, new OnValueFetched() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.6
            @Override // com.taobao.idlefish.protocol.remoteconfig.OnValueFetched
            public void onFetched(String str) {
                MtopLauncher.updateLoginApis(str);
            }

            @Override // com.taobao.idlefish.protocol.remoteconfig.OnValueFetched
            public void onFetchFailed(Object obj) {
            }
        });
        updateHostDomainApis(XModuleCenter.getApplication().getSharedPreferences(SP_GOOFISH_API_MODULE, 0).getString(SP_GOOFISH_API_KEY, null), "init_config");
        pRemoteConfigs.fetchValue("android_switch_high", "goofishapi", null, new OnValueFetched() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.7
            @Override // com.taobao.idlefish.protocol.remoteconfig.OnValueFetched
            public void onFetched(String str) {
                MtopLauncher.updateHostDomainApis(str, FileUtil.ORANGE_DIR);
                XModuleCenter.getApplication().getSharedPreferences(MtopLauncher.SP_GOOFISH_API_MODULE, 0).edit().putString(MtopLauncher.SP_GOOFISH_API_KEY, str).apply();
            }

            @Override // com.taobao.idlefish.protocol.remoteconfig.OnValueFetched
            public void onFetchFailed(Object obj) {
            }
        });
        if (c$$ExternalSyntheticOutline0.m("auto_start_mtop_debug", 0, "auto_start_mtop_debug", false)) {
            try {
                Class<?> cls = Class.forName("com.taobao.idlefish.debug.FishNetInterceptor");
                cls.getMethod("register", new Class[0]).invoke(cls.getMethod("ins", new Class[0]).invoke(null, null), null);
            } catch (Throwable th) {
                if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
                    th.printStackTrace();
                }
            }
        }
        MtopLauncher mtopLauncher = (MtopLauncher) XModuleCenter.moduleForProtocol(PApiContext.class);
        if (mtopLauncher.mOmegaInterceptor == null) {
            OmegaInterceptor omegaInterceptor = new OmegaInterceptor();
            mtopLauncher.mOmegaInterceptor = omegaInterceptor;
            InterceptorManager.addInterceptor(omegaInterceptor);
        }
        if (mtopLauncher.mJumpUrlInterceptor == null) {
            JumpUrlInterceptor jumpUrlInterceptor = new JumpUrlInterceptor();
            mtopLauncher.mJumpUrlInterceptor = jumpUrlInterceptor;
            InterceptorManager.addInterceptor(jumpUrlInterceptor);
        }
        if (mtopLauncher.mHomeFeedsDpaInterceptor == null) {
            HomeFeedsDpaInterceptor homeFeedsDpaInterceptor = new HomeFeedsDpaInterceptor();
            mtopLauncher.mHomeFeedsDpaInterceptor = homeFeedsDpaInterceptor;
            InterceptorManager.addInterceptor(homeFeedsDpaInterceptor);
        }
        if (mtopLauncher.mCoordinatesInterceptor == null) {
            CoordinatesInterceptor coordinatesInterceptor = new CoordinatesInterceptor();
            mtopLauncher.mCoordinatesInterceptor = coordinatesInterceptor;
            InterceptorManager.addInterceptor(coordinatesInterceptor);
        }
        if (mtopLauncher.mLocationInterceptor == null) {
            LocationInterceptor locationInterceptor = new LocationInterceptor();
            mtopLauncher.mLocationInterceptor = locationInterceptor;
            InterceptorManager.addInterceptor(locationInterceptor);
        }
    }

    @ExecNewInit(initDepends = {"com.taobao.idlefish.protocol.env.PEnv", "com.taobao.idlefish.protocol.localization.PLocalization"}, procPhase = {@ProcPhase(phase = "common", process = {"main", WindowChannelAliveApplication.SIMPLE_PROCESS_NAME, "channel", "recoveryModel", Constant.Monitor.PULL_RATE, "widgetProvider"})})
    public static void preInit(Application application) {
        initMtopSdk(application);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void updateHostDomainApis(String str, String str2) {
        Map<String, String> map;
        if (TextUtils.isEmpty(str)) {
            return;
        }
        Map<String, String> map2 = null;
        try {
            map = (Map) JSON.parseObject(str, new TypeReference<Map<String, String>>() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.8
            }, new Feature[0]);
        } catch (Exception e) {
            e = e;
        }
        try {
            FishLog.w("MtopCfgDomain", "updateHostDomainApis ret = ", JSON.toJSONString(map) + ", type = " + str2 + ", process = " + ProcessUtil.getCurrentProcessName(XModuleCenter.getApplication()));
        } catch (Exception e2) {
            e = e2;
            map2 = map;
            e.printStackTrace();
            map = map2;
        }
        if (map == null || map.isEmpty()) {
            return;
        }
        MtopExtSDKHandler.getMtopExtSDKDefault().setHostDomains(map);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void updateLoginApis(String str) {
        String[] strArrSplit = StringUtil.split(str);
        if (strArrSplit == null || strArrSplit.length <= 0) {
            return;
        }
        MtopExtSDKHandler.getMtopExtSDKDefault().setLoginApis(Arrays.asList(strArrSplit));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void updateWuaApis(String str) {
        if (TextUtils.isEmpty(str)) {
            str = ApiConstant.WUA_APIS_DEF;
        }
        String[] strArrSplit = StringUtil.split(str);
        if (strArrSplit == null || strArrSplit.length <= 0) {
            return;
        }
        MtopExtSDKHandler.getMtopExtSDKDefault().setWuaApis(Arrays.asList(strArrSplit));
    }

    private boolean useRequestQueue() {
        if (XModuleCenter.moduleReady(PRemoteConfigs.class)) {
            return ((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("launch_use_mtop_priority_queue", true);
        }
        return true;
    }

    @Override // com.taobao.idlefish.protocol.net.PApiContext
    public long getDate() {
        return Sync.getInstance().getDate();
    }

    @Override // com.taobao.idlefish.protocol.net.PApiContext
    public void logout() {
        MtopExtSDKHandler.getMtopExtSDKDefault().logout();
    }

    @Override // com.taobao.idlefish.protocol.net.PApiContext
    public <T, A extends ResponseParameter> void send(BaseApiProtocol<T, A> baseApiProtocol, ApiCallBack<A> apiCallBack) {
        if (useRequestQueue() && PriorityRequestMgr.inst().enqueueRequestIfNeed(baseApiProtocol, apiCallBack)) {
            checkTopActivity();
        } else {
            sendInternal(baseApiProtocol, apiCallBack);
        }
    }

    public <T, A extends ResponseParameter> void sendInternal(final BaseApiProtocol<T, A> baseApiProtocol, final ApiCallBack<A> apiCallBack) {
        if (apiCallBack != null) {
            apiCallBack.onProcess(Phase.pendExec);
        }
        this.mClientHeaderInterceptor.asyncIntercept(new Runnable() { // from class: com.taobao.android.remoteobject.easy.MtopLauncher.1
            @Override // java.lang.Runnable
            public void run() {
                MtopSend.get().send(baseApiProtocol, apiCallBack);
            }
        });
    }

    @ModuleNewInit(initDepends = {"com.taobao.idlefish.init.SecurityGuardInitConfig.initSecurity", "com.taobao.android.remoteobject.easy.MtopLauncher.preInit"}, prefer = 99, procPhase = {@ProcPhase(phase = "common", process = {"main", WindowChannelAliveApplication.SIMPLE_PROCESS_NAME, "channel", "recoveryModel", Constant.Monitor.PULL_RATE, "widgetProvider"})})
    public void init(Application application) {
    }
}
