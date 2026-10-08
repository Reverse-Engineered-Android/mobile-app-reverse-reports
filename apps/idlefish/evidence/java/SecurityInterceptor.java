package com.taobao.android.remoteobject.security;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import com.alibaba.fastjson.JSON;
import com.alibaba.poplayer.PopLayer;
import com.alibaba.poplayer.trigger.page.PageTriggerService;
import com.idlefish.blink.FishNewModule;
import com.idlefish.blink.ModuleNewInit;
import com.idlefish.blink.ProcPhase;
import com.taobao.android.remoteobject.security.FishMtopError;
import com.taobao.fleamarket.business.user_growth.alive.window_channel.WindowChannelAliveApplication;
import com.taobao.idlefish.event.ThreadBus;
import com.taobao.idlefish.home.power.event.subhandler.TapInterestCardEventHandler;
import com.taobao.idlefish.protocol.apibean.IApiBaseReturn;
import com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor;
import com.taobao.idlefish.protocol.lifecycle.PActivityLifecycleContext;
import com.taobao.idlefish.protocol.nav.PRouter;
import com.taobao.idlefish.protocol.remoteconfig.PRemoteConfigs;
import com.taobao.idlefish.protocol.tbs.PTBS;
import com.taobao.idlefish.protocol.webview.PPoplayer;
import com.taobao.idlefish.ui.alert.base.callback.AlertComponentClickData;
import com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback;
import com.taobao.idlefish.ui.alert.base.container.FishDialog;
import com.taobao.idlefish.ui.alert.util.DialogUtil;
import com.taobao.idlefish.ui.util.Toast;
import com.taobao.idlefish.xframework.archive.Constants;
import com.taobao.idlefish.xframework.archive.Variable;
import com.taobao.idlefish.xframework.util.StringUtil;
import com.taobao.idlefish.xmc.XModuleCenter;
import com.taobao.tao.messagekit.core.Contants.Constant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mtopsdk.mtop.domain.MtopResponse;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes6.dex */
@FishNewModule(protocol = "com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor")
public class SecurityInterceptor implements PSecurityInterceptor {
    private static final String USER_NEED_ALIPYA_BIND = "USER_NEED_ALIPYA_BIND";
    private static final String USER_NEED_REALNAME_VERIFY = "USER_NEED_REALNAME_VERIFY";
    private Handler mHandler;
    private static final Variable SECURITY_INTERCEPTOR = Variable.defineSwitch("securityInterceptorRetCode", "");
    private static final Variable WARNING_H5_URL_KEY = Variable.defineSwitch("warningH5UrlKey", "");
    public static Boolean isSecurityInterceptor = Boolean.FALSE;

    private boolean isPenalty(String str) {
        return StringUtil.isEqual(str, Constants.FAIL_BIZ_FORBIDDEN);
    }

    private boolean isRealVerify(IApiBaseReturn iApiBaseReturn) {
        if (iApiBaseReturn == null) {
            return false;
        }
        String desc = iApiBaseReturn.getDesc();
        return StringUtil.isEqual(desc, Constants.NEED_REAL_VERIFY) || StringUtil.isEqual(desc, Constants.RISK_USER_VERIFY);
    }

    private boolean needNormalInterceptor(String str) {
        FishMtopError.FishMtopErrorToast fishMtopErrorToast;
        if (StringUtil.isEmpty(str)) {
            return false;
        }
        final Activity currentActivity = ((PActivityLifecycleContext) XModuleCenter.moduleForProtocol(PActivityLifecycleContext.class)).getCurrentActivity();
        final FishMtopError fishMtopError = (FishMtopError) JSON.parseObject(str, FishMtopError.class);
        if (fishMtopError.type.equals(TapInterestCardEventHandler.CONFIRM) && fishMtopError.confirm != null) {
            ThreadBus.post(1, new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.6
                @Override // java.lang.Runnable
                public void run() {
                    Activity activity = currentActivity;
                    if (activity == null || activity.isFinishing() || fishMtopError.confirm.buttonList.size() != 2) {
                        return;
                    }
                    OnClickDataFormatCallback onClickDataFormatCallback = new OnClickDataFormatCallback() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.6.1
                        @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                        public void getFormatDataByClickOne(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                            if (fishDialog != null) {
                                fishDialog.dismiss();
                            }
                        }

                        @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                        public void getFormatDataByClickTwo(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                            if (!StringUtil.isEmptyOrNullStr(fishMtopError.confirm.buttonList.get(1).url)) {
                                ((PRouter) XModuleCenter.moduleForProtocol(PRouter.class)).build(fishMtopError.confirm.buttonList.get(1).url).open(currentActivity);
                            }
                            if (fishDialog != null) {
                                fishDialog.dismiss();
                            }
                        }

                        @Override // android.content.DialogInterface.OnDismissListener
                        public void onDismiss(DialogInterface dialogInterface) {
                        }
                    };
                    if (StringUtil.isEmpty(fishMtopError.confirm.title)) {
                        FishMtopError.FishMtopErrorConfirm fishMtopErrorConfirm = fishMtopError.confirm;
                        DialogUtil.buildContentBtn(fishMtopErrorConfirm.content, fishMtopErrorConfirm.buttonList.get(0).text, fishMtopError.confirm.buttonList.get(1).text, false, currentActivity, onClickDataFormatCallback);
                    } else {
                        FishMtopError.FishMtopErrorConfirm fishMtopErrorConfirm2 = fishMtopError.confirm;
                        DialogUtil.buildTitleContentBtn(fishMtopErrorConfirm2.title, fishMtopErrorConfirm2.content, fishMtopErrorConfirm2.buttonList.get(0).text, fishMtopError.confirm.buttonList.get(1).text, false, currentActivity, onClickDataFormatCallback);
                    }
                }
            });
            return true;
        }
        if (fishMtopError.type.equals("toast") && (fishMtopErrorToast = fishMtopError.toast) != null) {
            Toast.showText(currentActivity, fishMtopErrorToast.content, Integer.valueOf(((int) fishMtopErrorToast.staySeconds) * 1000));
            return true;
        }
        if (fishMtopError.type.equals("link") && fishMtopError.link != null) {
            if (currentActivity != null && !currentActivity.isFinishing()) {
                ((PRouter) XModuleCenter.moduleForProtocol(PRouter.class)).build(fishMtopError.link.url).open(currentActivity);
            }
            return true;
        }
        if (!fishMtopError.type.equals(PopLayer.SCHEMA) || fishMtopError.poplayer == null) {
            return false;
        }
        ((PPoplayer) XModuleCenter.moduleForProtocol(PPoplayer.class)).sendBroadcastToPoplayer(currentActivity, PageTriggerService.PAGE_SCHEME + fishMtopError.poplayer.name, fishMtopError.poplayer.param);
        return true;
    }

    private void startSecurityInterceptorWebHybridActivity(Context context, String str) {
        ((PRouter) XModuleCenter.moduleForProtocol(PRouter.class)).build("fleamarket://SecurityInterceptorWebHybrid").putExtra("url", str).addFlags(268435456).open(context);
    }

    private void warning(JSONObject jSONObject) {
        String strValueOf;
        if (jSONObject != null) {
            try {
                strValueOf = String.valueOf(jSONObject.get(WARNING_H5_URL_KEY.getValue()));
            } catch (Exception unused) {
                strValueOf = null;
            }
            if (StringUtil.isEmpty(strValueOf)) {
                return;
            }
            synchronized (isSecurityInterceptor) {
                if (isSecurityInterceptor.booleanValue()) {
                    return;
                }
                isSecurityInterceptor = Boolean.TRUE;
                try {
                    startSecurityInterceptorWebHybridActivity(XModuleCenter.getApplication(), strValueOf);
                } catch (Exception unused2) {
                    isSecurityInterceptor = Boolean.FALSE;
                }
            }
        }
    }

    @Override // com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor
    public boolean go2PenaltyActivityIfNeeded(String str, final String str2, final String str3, String str4, String str5) {
        List<String> list;
        String strOptString;
        String strOptString2;
        final String str6;
        final String str7;
        final String str8;
        JSONObject jSONObject;
        String strOptString3;
        String strOptString4;
        JSONObject jSONObject2;
        final String str9;
        final String str10;
        final String str11;
        if (str4 != null && str4.startsWith("{") && str4.endsWith("}")) {
            return needNormalInterceptor(str4);
        }
        String strOptString5 = null;
        try {
            list = (List) JSON.parseObject(((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("real_verify_white_list", ""), List.class);
        } catch (Exception unused) {
            list = null;
        }
        if (list != null && str != null) {
            for (String str12 : list) {
                if (str12 != null && str.equals(str12)) {
                    return false;
                }
            }
        }
        final Activity currentActivity = ((PActivityLifecycleContext) XModuleCenter.moduleForProtocol(PActivityLifecycleContext.class)).getCurrentActivity();
        if (currentActivity != null && !currentActivity.isFinishing()) {
            try {
            } catch (JSONException e) {
                e = e;
                strOptString = null;
                strOptString2 = null;
            }
            if (isRealVerify(str4)) {
                try {
                    jSONObject = new JSONObject(str3);
                } catch (Throwable unused2) {
                    jSONObject = new JSONObject(str2);
                }
                strOptString = jSONObject.optString("riskDesc");
                try {
                    strOptString2 = jSONObject.optString("confirmText");
                } catch (JSONException e2) {
                    e = e2;
                    strOptString2 = null;
                }
                try {
                    strOptString5 = jSONObject.optString("confirmUrl");
                } catch (JSONException e3) {
                    e = e3;
                    e.printStackTrace();
                }
                str6 = strOptString;
                str7 = strOptString2;
                str8 = strOptString5;
                if (!StringUtil.isEmptyOrNullStr(str6) && !StringUtil.isEmptyOrNullStr(str7)) {
                    ThreadBus.post(1, new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.2
                        @Override // java.lang.Runnable
                        public void run() {
                            DialogUtil.buildContentBtn(str6, "取消", str7, false, currentActivity, new OnClickDataFormatCallback() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.2.1
                                @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                                public void getFormatDataByClickOne(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                    if (fishDialog != null) {
                                        fishDialog.dismiss();
                                    }
                                }

                                @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                                public void getFormatDataByClickTwo(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                    ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).deprecatedCtrlClicked(currentActivity, "Button-Vertify");
                                    if (!StringUtil.isEmptyOrNullStr(str8)) {
                                        ((PRouter) XModuleCenter.moduleForProtocol(PRouter.class)).build(str8).open(currentActivity);
                                    }
                                    if (fishDialog != null) {
                                        fishDialog.dismiss();
                                    }
                                }

                                @Override // android.content.DialogInterface.OnDismissListener
                                public void onDismiss(DialogInterface dialogInterface) {
                                }
                            });
                        }
                    });
                }
                return true;
            }
            if (isPenalty(str5)) {
                try {
                    try {
                        jSONObject2 = new JSONObject(str3);
                    } catch (Throwable unused3) {
                        jSONObject2 = new JSONObject(str2);
                    }
                    strOptString3 = jSONObject2.optString("riskDesc");
                    try {
                        strOptString4 = jSONObject2.optString("confirmText");
                        try {
                            strOptString5 = jSONObject2.optString("confirmUrl");
                        } catch (JSONException e4) {
                            e = e4;
                            e.printStackTrace();
                        }
                    } catch (JSONException e5) {
                        e = e5;
                        strOptString4 = null;
                    }
                } catch (JSONException e6) {
                    e = e6;
                    strOptString3 = null;
                    strOptString4 = null;
                    e.printStackTrace();
                    str9 = strOptString3;
                    str10 = strOptString4;
                    str11 = strOptString5;
                    if (!StringUtil.isEmptyOrNullStr(str9)) {
                        ThreadBus.post(1, new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.3
                            @Override // java.lang.Runnable
                            public void run() {
                                DialogUtil.buildContentBtn(str9, "取消", str10, false, currentActivity, new OnClickDataFormatCallback() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.3.1
                                    @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                                    public void getFormatDataByClickOne(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                        if (fishDialog != null) {
                                            fishDialog.dismiss();
                                        }
                                    }

                                    @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                                    public void getFormatDataByClickTwo(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                        if (!StringUtil.isEmptyOrNullStr(str11)) {
                                            ((PRouter) XModuleCenter.moduleForProtocol(PRouter.class)).build(str11).open(currentActivity);
                                        }
                                        if (fishDialog != null) {
                                            fishDialog.dismiss();
                                        }
                                    }

                                    @Override // android.content.DialogInterface.OnDismissListener
                                    public void onDismiss(DialogInterface dialogInterface) {
                                    }
                                });
                            }
                        });
                    }
                    return true;
                }
                str9 = strOptString3;
                str10 = strOptString4;
                str11 = strOptString5;
                if (!StringUtil.isEmptyOrNullStr(str9) && !StringUtil.isEmptyOrNullStr(str10)) {
                    ThreadBus.post(1, new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.3
                        @Override // java.lang.Runnable
                        public void run() {
                            DialogUtil.buildContentBtn(str9, "取消", str10, false, currentActivity, new OnClickDataFormatCallback() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.3.1
                                @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                                public void getFormatDataByClickOne(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                    if (fishDialog != null) {
                                        fishDialog.dismiss();
                                    }
                                }

                                @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                                public void getFormatDataByClickTwo(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                    if (!StringUtil.isEmptyOrNullStr(str11)) {
                                        ((PRouter) XModuleCenter.moduleForProtocol(PRouter.class)).build(str11).open(currentActivity);
                                    }
                                    if (fishDialog != null) {
                                        fishDialog.dismiss();
                                    }
                                }

                                @Override // android.content.DialogInterface.OnDismissListener
                                public void onDismiss(DialogInterface dialogInterface) {
                                }
                            });
                        }
                    });
                }
                return true;
            }
            if (StringUtil.isEqual(str4, USER_NEED_ALIPYA_BIND)) {
                ThreadBus.post(1, new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.4
                    @Override // java.lang.Runnable
                    public void run() {
                        try {
                            AlipayAndIdentityVerifyUtils.showBindAlipay(currentActivity, str3);
                        } catch (Exception e7) {
                            e7.printStackTrace();
                        }
                    }
                });
                return true;
            }
            if (StringUtil.isEqual(str4, USER_NEED_REALNAME_VERIFY)) {
                ThreadBus.post(1, new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.5
                    @Override // java.lang.Runnable
                    public void run() {
                        JSONObject jSONObject3;
                        try {
                            try {
                                jSONObject3 = new JSONObject(str3);
                            } catch (Throwable unused4) {
                                jSONObject3 = new JSONObject(str2);
                            }
                            AlipayAndIdentityVerifyUtils.showGuideIdentityVerify(currentActivity, jSONObject3.optString("title"), jSONObject3.optString("desc"), jSONObject3.optString("confirmText"), jSONObject3.optString("confirmUrl"));
                        } catch (Exception e7) {
                            e7.printStackTrace();
                        }
                    }
                });
                return true;
            }
            e.printStackTrace();
            str6 = strOptString;
            str7 = strOptString2;
            str8 = strOptString5;
            if (!StringUtil.isEmptyOrNullStr(str6)) {
                ThreadBus.post(1, new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.2
                    @Override // java.lang.Runnable
                    public void run() {
                        DialogUtil.buildContentBtn(str6, "取消", str7, false, currentActivity, new OnClickDataFormatCallback() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.2.1
                            @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                            public void getFormatDataByClickOne(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                if (fishDialog != null) {
                                    fishDialog.dismiss();
                                }
                            }

                            @Override // com.taobao.idlefish.ui.alert.base.callback.OnClickDataFormatCallback
                            public void getFormatDataByClickTwo(FishDialog fishDialog, ArrayList<AlertComponentClickData> arrayList) {
                                ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).deprecatedCtrlClicked(currentActivity, "Button-Vertify");
                                if (!StringUtil.isEmptyOrNullStr(str8)) {
                                    ((PRouter) XModuleCenter.moduleForProtocol(PRouter.class)).build(str8).open(currentActivity);
                                }
                                if (fishDialog != null) {
                                    fishDialog.dismiss();
                                }
                            }

                            @Override // android.content.DialogInterface.OnDismissListener
                            public void onDismiss(DialogInterface dialogInterface) {
                            }
                        });
                    }
                });
            }
            return true;
        }
        return false;
    }

    @ModuleNewInit(initDepends = {"com.taobao.idlefish.protocol.remoteconfig.PRemoteConfigs"}, procPhase = {@ProcPhase(phase = "common", process = {"main", WindowChannelAliveApplication.SIMPLE_PROCESS_NAME, "channel", "recoveryModel", Constant.Monitor.PULL_RATE, "widgetProvider"})})
    public void init(Application application) {
        initSDK(application);
    }

    public void initSDK(Application application) {
        this.mHandler = new Handler(Looper.getMainLooper());
    }

    @Override // com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor
    public void interceptor(final JSONObject jSONObject, final String str) {
        if (isSecurityInterceptor.booleanValue() || StringUtil.isEmpty(SECURITY_INTERCEPTOR.getValue())) {
            return;
        }
        this.mHandler.post(new Runnable() { // from class: com.taobao.android.remoteobject.security.SecurityInterceptor.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    SecurityInterceptor.this.work(jSONObject, str);
                } catch (Throwable th) {
                    ((PTBS) XModuleCenter.moduleForProtocol(PTBS.class)).errorLog("SecurityInterceptor.interceptor", th.getMessage());
                }
            }
        });
    }

    @Override // com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor
    public void normalInterceptorIfNeed(Map<String, Object> map) {
        JSONObject dataJsonObject;
        if (map == null || !map.containsKey("mtopsdk.mtop.domain.MtopResponse") || (dataJsonObject = ((MtopResponse) map.get("mtopsdk.mtop.domain.MtopResponse")).getDataJsonObject()) == null) {
            return;
        }
        try {
            needNormalInterceptor(dataJsonObject.getString("extMsg"));
        } catch (Throwable unused) {
        }
    }

    @Override // com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor
    public void setIsSecurityInterceptor(boolean z) {
        isSecurityInterceptor = Boolean.valueOf(z);
    }

    @Override // com.taobao.idlefish.protocol.interceptor.PSecurityInterceptor
    public void work(JSONObject jSONObject, String str) {
        if (jSONObject == null) {
            return;
        }
        String value = SECURITY_INTERCEPTOR.getValue();
        if (StringUtil.isEmpty(value) || StringUtil.isEmpty(str) || value.indexOf("|".concat(str).concat("|")) < 0) {
            return;
        }
        warning(jSONObject);
    }

    private boolean isPenalty(IApiBaseReturn iApiBaseReturn) {
        return StringUtil.isEqual(iApiBaseReturn.getRetCodeAtIndex(0), Constants.FAIL_BIZ_FORBIDDEN);
    }

    private boolean isRealVerify(String str) {
        return StringUtil.isEqual(str, Constants.NEED_REAL_VERIFY) || StringUtil.isEqual(str, Constants.RISK_USER_VERIFY);
    }
}
