package mtopsdk.security;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.collection.ArraySet$$ExternalSyntheticOutline0;
import com.alibaba.wireless.security.open.SecException;
import com.alibaba.wireless.security.open.SecurityGuardManager;
import com.alibaba.wireless.security.open.SecurityGuardParamContext;
import com.alibaba.wireless.security.open.avmp.IAVMPGenericComponent;
import com.alibaba.wireless.security.open.middletier.IMiddleTierGenericComponent;
import com.alibaba.wireless.security.open.middletier.IUnifiedSecurityComponent;
import com.alibaba.wireless.security.open.middletier.fc.IFCComponent;
import com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent;
import com.alibaba.wireless.security.open.securitybody.ISecurityBodyComponent;
import com.alibaba.wireless.security.open.umid.IUMIDComponent;
import com.alibaba.wireless.security.open.umid.IUMIDInitListenerEx;
import com.taobao.tao.remotebusiness.login.RemoteLogin;
import com.taobao.weex.el.parse.Operators;
import defpackage.NCErrorCode$$ExternalSyntheticOutline0;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import mtopsdk.common.util.StringUtils;
import mtopsdk.common.util.TBSdkLog;
import mtopsdk.mtop.domain.EnvModeEnum;
import mtopsdk.mtop.global.MtopConfig;
import mtopsdk.mtop.global.SwitchConfig;
import mtopsdk.mtop.util.MtopSDKThreadPoolExecutorFactory;
import mtopsdk.security.ISign;
import mtopsdk.security.util.SecurityUtils;
import mtopsdk.security.util.SignConstants;
import mtopsdk.security.util.SignStatistics;
import mtopsdk.xstate.XState;
import mtopsdk.xstate.util.XStateConstants;

/* JADX INFO: loaded from: classes9.dex */
public class InnerSignImpl extends AbstractSignImpl {
    private volatile IAVMPGenericComponent.IAVMPGenericInstance mAVMPInstance;
    private SecurityGuardManager sgMgr = null;
    private IMiddleTierGenericComponent mMiddleTier = null;
    private IUnifiedSecurityComponent mUnifiedSign = null;

    public static HashMap convertInnerBaseStrMap(String str, Map map, boolean z) {
        if (map == null || map.size() < 1) {
            return null;
        }
        String str2 = (String) map.get("utdid");
        String str3 = (String) map.get("uid");
        String str4 = (String) map.get(XStateConstants.KEY_REQBIZ_EXT);
        String str5 = (String) map.get("data");
        String str6 = (String) map.get("t");
        String str7 = (String) map.get("api");
        String str8 = (String) map.get("v");
        String str9 = (String) map.get("sid");
        String str10 = (String) map.get("ttid");
        String str11 = (String) map.get("deviceId");
        String str12 = (String) map.get("lat");
        String str13 = (String) map.get("lng");
        String str14 = (String) map.get("extdata");
        String str15 = (String) map.get("x-features");
        String str16 = (String) map.get(XStateConstants.KEY_ROUTER_ID);
        String str17 = (String) map.get(XStateConstants.KEY_PLACE_ID);
        String str18 = (String) map.get(XStateConstants.KEY_OPEN_BIZ);
        String str19 = (String) map.get(XStateConstants.KEY_MINI_APPKEY);
        String str20 = (String) map.get(XStateConstants.KEY_REQ_APPKEY);
        String str21 = (String) map.get(XStateConstants.KEY_ACCESS_TOKEN);
        String str22 = (String) map.get(XStateConstants.KEY_OPEN_BIZ_DATA);
        StringBuilder sb = new StringBuilder(64);
        if (str2 == null) {
            str2 = "";
        }
        sb.append(str2);
        sb.append("&");
        if (str3 == null) {
            str3 = "";
        }
        sb.append(str3);
        sb.append("&");
        if (str4 == null) {
            str4 = "";
        }
        NCErrorCode$$ExternalSyntheticOutline0.m(sb, str4, "&", str, "&");
        sb.append(SecurityUtils.getMd5(str5));
        sb.append("&");
        sb.append(str6);
        sb.append("&");
        sb.append(str7);
        ArraySet$$ExternalSyntheticOutline0.m(sb, "&", str8, "&");
        if (str9 == null) {
            str9 = "";
        }
        sb.append(str9);
        sb.append("&");
        if (str10 == null) {
            str10 = "";
        }
        sb.append(str10);
        sb.append("&");
        if (str11 == null) {
            str11 = "";
        }
        sb.append(str11);
        sb.append("&");
        if (str12 == null) {
            str12 = "";
        }
        sb.append(str12);
        sb.append("&");
        if (str13 == null) {
            str13 = "";
        }
        sb.append(str13);
        sb.append("&");
        if (z) {
            if (str14 == null) {
                str14 = "";
            }
            sb.append(str14);
            sb.append("&");
        } else if (StringUtils.isNotBlank(str14)) {
            sb.append(str14);
            sb.append("&");
        }
        sb.append(str15);
        sb.append("&");
        sb.append(str16 == null ? "" : str16);
        sb.append("&");
        sb.append(str17 == null ? "" : str17);
        sb.append("&");
        sb.append(str18 == null ? "" : str18);
        sb.append("&");
        sb.append(str19 == null ? "" : str19);
        sb.append("&");
        sb.append(str20 == null ? "" : str20);
        sb.append("&");
        sb.append(str21 == null ? "" : str21);
        sb.append("&");
        sb.append(str22 != null ? str22 : "");
        HashMap map2 = new HashMap(2);
        map2.put("INPUT", sb.toString());
        return map2;
    }

    public static HashMap convertSsrBaseStrMap(String str, Map map) {
        if (map == null || map.size() < 1) {
            return null;
        }
        String str2 = (String) map.get("utdid");
        String str3 = (String) map.get("uid");
        String str4 = (String) map.get("data");
        String str5 = (String) map.get("t");
        String str6 = (String) map.get("sid");
        String str7 = (String) map.get("ttid");
        StringBuilder sb = new StringBuilder(64);
        if (str2 == null) {
            str2 = "";
        }
        sb.append(str2);
        sb.append("&");
        if (str3 == null) {
            str3 = "";
        }
        NCErrorCode$$ExternalSyntheticOutline0.m(sb, str3, Operators.AND, str, "&");
        sb.append(SecurityUtils.getMd5(str4));
        sb.append("&");
        sb.append(str5);
        sb.append("&&&");
        if (str6 == null) {
            str6 = "";
        }
        sb.append(str6);
        sb.append("&");
        if (str7 == null) {
            str7 = "";
        }
        sb.append(str7);
        sb.append("&&&&&&&&&&&&");
        HashMap map2 = new HashMap(2);
        map2.put("INPUT", sb.toString());
        return map2;
    }

    private String getAppKeyByIndex(int i, String str) {
        String instanceId = getInstanceId();
        String appKeyByIndex = null;
        try {
            appKeyByIndex = this.sgMgr.getStaticDataStoreComp().getAppKeyByIndex(i, str);
            if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.InfoEnable)) {
                TBSdkLog.i("mtopsdk.InnerSignImpl", instanceId + " [getAppKeyByIndex]getAppKeyByIndex  appKey=" + appKeyByIndex + ",appKeyIndex=" + i + ",authCode=" + str);
            }
        } catch (SecException e) {
            int errorCode = e.getErrorCode();
            SignStatistics.commitStats(SignStatistics.SignStatsType.TYPE_GET_APPKEY, String.valueOf(errorCode), "");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getAppKeyByIndex]getAppKeyByIndex error.errorCode=" + errorCode + ",appKeyIndex=" + i + ",authCode=" + str, e);
        } catch (Exception e2) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getAppKeyByIndex]getAppKeyByIndex error.appKeyIndex=" + i + ",authCode=" + str, e2);
        }
        return appKeyByIndex;
    }

    private int getMiddleTierEnv() {
        if (getEnv() == EnvModeEnum.PREPARE.getEnvMode()) {
            return 1;
        }
        return (getEnv() == EnvModeEnum.TEST.getEnvMode() || getEnv() == EnvModeEnum.TEST_SANDBOX.getEnvMode()) ? 2 : 0;
    }

    private void initMiddleTier(MtopConfig mtopConfig) {
        if (mtopConfig == null) {
            return;
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        try {
            HashMap<String, Object> map = new HashMap<>();
            MtopConfig mtopConfig2 = this.mtopConfig;
            map.put("auth_code", mtopConfig2 != null ? mtopConfig2.authCode : "");
            if (this.mMiddleTier == null) {
                IMiddleTierGenericComponent iMiddleTierGenericComponent = (IMiddleTierGenericComponent) SecurityGuardManager.getInstance(mtopConfig.context).getInterface(IMiddleTierGenericComponent.class);
                this.mMiddleTier = iMiddleTierGenericComponent;
                if (iMiddleTierGenericComponent != null && !iMiddleTierGenericComponent.init(map) && TBSdkLog.isLogEnable(TBSdkLog.LogEnable.InfoEnable)) {
                    TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [initMiddleTier]init middle tier failed");
                }
            }
            HashMap<String, Object> map2 = new HashMap<>();
            MtopConfig mtopConfig3 = this.mtopConfig;
            map2.put(SignConstants.MIDDLE_PARAM_AUTHCODE, mtopConfig3 != null ? mtopConfig3.authCode : "");
            if (this.mUnifiedSign == null) {
                IUnifiedSecurityComponent iUnifiedSecurityComponent = (IUnifiedSecurityComponent) SecurityGuardManager.getInstance(mtopConfig.context).getInterface(IUnifiedSecurityComponent.class);
                this.mUnifiedSign = iUnifiedSecurityComponent;
                if (iUnifiedSecurityComponent != null) {
                    iUnifiedSecurityComponent.init(map2);
                } else if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.InfoEnable)) {
                    TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [initMiddleTier]init sign failed");
                }
            }
        } catch (SecException e) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [initMiddleTier]init middleTier failed with errorCode " + e.getErrorCode() + ",appKeyIndex=" + mtopConfig.appKeyIndex + ",authCode=" + mtopConfig.authCode, e);
        } catch (Exception e2) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [initMiddleTier]init middleTier failed with unknown exception, appKeyIndex=" + mtopConfig.appKeyIndex + ",authCode=" + mtopConfig.authCode, e2);
        }
        try {
            HashMap map3 = new HashMap();
            RemoteLogin.getLogin(mtopConfig.mtopInstance);
            map3.put("key_login_module", Boolean.TRUE);
            IFCComponent iFCComponent = (IFCComponent) SecurityGuardManager.getInstance(mtopConfig.context).getInterface(IFCComponent.class);
            if (iFCComponent != null) {
                iFCComponent.setUp(mtopConfig.context, map3);
                mtopConfig.mtopGlobalHeaders.put(SignConstants.BX_VERSION, iFCComponent.getFCPluginVersion());
            }
        } catch (SecException e3) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [initMiddleTier]init ifcComponent failed with errorCode " + e3.getErrorCode() + ",appKeyIndex=" + mtopConfig.appKeyIndex + ",authCode=" + mtopConfig.authCode, e3);
        } catch (Exception e4) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [initMiddleTier]init ifcComponent failed with unknown exception, appKeyIndex=" + mtopConfig.appKeyIndex + ",authCode=" + mtopConfig.authCode, e4);
        }
        TBSdkLog.e("mtopsdk.InnerSignImpl", "[initMiddleTier] execute initMiddleTier cost time ", String.valueOf(System.currentTimeMillis() - jCurrentTimeMillis));
    }

    private void initUmidToken(String str, String str2) {
        final String instanceId = getInstanceId();
        try {
            IUMIDComponent uMIDComp = this.sgMgr.getUMIDComp();
            if (uMIDComp != null) {
                int env = getEnv();
                if (str2 == null) {
                    str2 = "";
                }
                uMIDComp.initUMID(str, env, str2, new IUMIDInitListenerEx() { // from class: mtopsdk.security.InnerSignImpl.2
                    @Override // com.alibaba.wireless.security.open.umid.IUMIDInitListenerEx
                    public final void onUMIDInitFinishedEx(String str3, int i) {
                        String str4 = instanceId;
                        if (i != 200) {
                            TBSdkLog.w("mtopsdk.InnerSignImpl", str4 + " [initUmidToken]IUMIDComponent initUMID error,resultCode :" + i);
                            return;
                        }
                        XState.setValue(str4, XStateConstants.KEY_UMID_TOKEN, str3);
                        TBSdkLog.i("mtopsdk.InnerSignImpl", str4 + " [initUmidToken]IUMIDComponent initUMID succeed,UMID token=" + str3);
                    }
                });
            }
        } catch (SecException e) {
            int errorCode = e.getErrorCode();
            SignStatistics.commitStats(SignStatistics.SignStatsType.TYPE_INIT_UMID, String.valueOf(errorCode), "");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + "[initUmidToken]IUMIDComponent initUMID error,errorCode=" + errorCode, e);
        } catch (Exception e2) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + "[initUmidToken]IUMIDComponent initUMID error.", e2);
        }
    }

    final IAVMPGenericComponent.IAVMPGenericInstance getAVMPInstance(@NonNull Context context) {
        if (this.mAVMPInstance == null) {
            synchronized (InnerSignImpl.class) {
                if (this.mAVMPInstance == null) {
                    try {
                        this.mAVMPInstance = ((IAVMPGenericComponent) SecurityGuardManager.getInstance(context).getInterface(IAVMPGenericComponent.class)).createAVMPInstance("mwua", "sgcipher");
                        if (this.mAVMPInstance == null) {
                            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getAVMPInstance] call createAVMPInstance return null.");
                        }
                    } catch (SecException e) {
                        int errorCode = e.getErrorCode();
                        SignStatistics.commitStats(SignStatistics.SignStatsType.TYPE_AVMP_INSTANCE, String.valueOf(errorCode), "");
                        TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getAVMPInstance] call createAVMPInstance error,errorCode=" + errorCode, e);
                    } catch (Exception e2) {
                        TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getAVMPInstance] call createAVMPInstance error.", e2);
                    }
                }
            }
        }
        return this.mAVMPInstance;
    }

    @Override // mtopsdk.security.ISign
    public final String getAppKey(ISign.SignCtx signCtx) {
        if (signCtx == null) {
            return null;
        }
        return getAppKeyByIndex(signCtx.index, signCtx.authCode);
    }

    /* JADX WARN: Removed duplicated region for block: B:10:0x002d A[Catch: Exception -> 0x007d, all -> 0x00e2, TryCatch #1 {Exception -> 0x007d, blocks: (B:6:0x000a, B:7:0x0026, B:9:0x002a, B:11:0x0031, B:15:0x003a, B:17:0x0076, B:10:0x002d), top: B:32:0x000a, outer: #0 }] */
    /* JADX WARN: Removed duplicated region for block: B:9:0x002a A[Catch: Exception -> 0x007d, all -> 0x00e2, TryCatch #1 {Exception -> 0x007d, blocks: (B:6:0x000a, B:7:0x0026, B:9:0x002a, B:11:0x0031, B:15:0x003a, B:17:0x0076, B:10:0x002d), top: B:32:0x000a, outer: #0 }] */
    @Override // mtopsdk.security.AbstractSignImpl, mtopsdk.security.ISign
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final java.lang.String getAvmpSign(java.lang.String r12, java.lang.String r13, int r14) {
        /*
            Method dump skipped, instruction units count: 229
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: mtopsdk.security.InnerSignImpl.getAvmpSign(java.lang.String, java.lang.String, int):java.lang.String");
    }

    @Override // mtopsdk.security.ISign
    public final String getCommonHmacSha1Sign(String str, String str2) {
        String instanceId = getInstanceId();
        if (str == null || str2 == null) {
            return null;
        }
        if (this.sgMgr == null) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getCommonHmacSha1Sign]SecurityGuardManager is null,please call ISign init()");
            return null;
        }
        try {
            HashMap map = new HashMap(1);
            map.put("INPUT", str);
            SecurityGuardParamContext securityGuardParamContext = new SecurityGuardParamContext();
            securityGuardParamContext.appKey = str2;
            securityGuardParamContext.requestType = 3;
            securityGuardParamContext.paramMap = map;
            ISecureSignatureComponent secureSignatureComp = this.sgMgr.getSecureSignatureComp();
            MtopConfig mtopConfig = this.mtopConfig;
            return secureSignatureComp.signRequest(securityGuardParamContext, mtopConfig != null ? mtopConfig.authCode : "");
        } catch (SecException e) {
            int errorCode = e.getErrorCode();
            SignStatistics.commitStats(SignStatistics.SignStatsType.TYPE_SIGN_HMAC_SHA1, String.valueOf(errorCode), "");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getCommonHmacSha1Sign] ISecureSignatureComponent signRequest error,errorCode=" + errorCode, e);
            return null;
        } catch (Exception e2) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getCommonHmacSha1Sign] ISecureSignatureComponent signRequest error", e2);
            return null;
        }
    }

    @Override // mtopsdk.security.AbstractSignImpl, mtopsdk.security.ISign
    public final String getMiniWua(HashMap<String, String> map, HashMap<String, String> map2) {
        String str = "";
        try {
            if (this.mMiddleTier == null) {
                TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getMiniWua]middleTier ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
                return "";
            }
            HashMap<String, Object> map3 = new HashMap<>();
            map3.put(SignConstants.MIDDLE_PARAM_ENV, Integer.valueOf(getMiddleTierEnv()));
            HashMap map4 = map2;
            if (map2 == null) {
                map4 = new HashMap();
            }
            map4.put("api_name", map.get("api"));
            map3.put(SignConstants.MIDDLE_PARAM_EXT, map4);
            HashMap<String, String> miniWua = this.mMiddleTier.getMiniWua(map3);
            if (miniWua != null && !miniWua.isEmpty()) {
                String strRemove = miniWua.remove(SignConstants.MIDDLE_OUTPUT_X_MINI_WUA);
                try {
                    if (!StringUtils.isNotBlank(strRemove)) {
                        return strRemove;
                    }
                    map.putAll(miniWua);
                    return strRemove;
                } catch (SecException e) {
                    e = e;
                    str = strRemove;
                    TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getMiniWua]get miniwua failed and SecException errorCode " + e.getErrorCode() + ",appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode, e);
                    return str;
                }
            }
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getMiniWua]get miniwua failed with no output ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
            return "";
        } catch (SecException e2) {
            e = e2;
        }
    }

    @Override // mtopsdk.security.ISign
    public final String getMtopApiSign(HashMap<String, String> map, String str, String str2) {
        String instanceId = getInstanceId();
        if (map == null) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getMtopApiSign] params is null.appKey=" + str);
            return null;
        }
        if (str == null) {
            map.put(XStateConstants.KEY_SG_ERROR_CODE, "AppKey is null");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getMtopApiSign] AppKey is null.");
            return null;
        }
        if (this.sgMgr == null) {
            map.put(XStateConstants.KEY_SG_ERROR_CODE, "SGManager is null");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getMtopApiSign]SecurityGuardManager is null,please call ISign init()");
            return null;
        }
        try {
            SwitchConfig.getInstance().getClass();
            sign = (SwitchConfig.getUseSecurityAdapter() & 1) == 1 ? getSign(map, str) : null;
            if (!StringUtils.isBlank(sign)) {
                return sign;
            }
            SecurityGuardParamContext securityGuardParamContext = new SecurityGuardParamContext();
            securityGuardParamContext.appKey = str;
            securityGuardParamContext.requestType = 7;
            HashMap mapConvertInnerBaseStrMap = convertInnerBaseStrMap(str, map, false);
            if (mapConvertInnerBaseStrMap != null && 2 == getEnv()) {
                mapConvertInnerBaseStrMap.put("ATLAS", "daily");
            }
            securityGuardParamContext.paramMap = mapConvertInnerBaseStrMap;
            return this.sgMgr.getSecureSignatureComp().signRequest(securityGuardParamContext, str2);
        } catch (SecException e) {
            int errorCode = e.getErrorCode();
            SignStatistics.commitStats(SignStatistics.SignStatsType.TYPE_SIGN_MTOP_REQUEST, String.valueOf(errorCode), "");
            map.put(XStateConstants.KEY_SG_ERROR_CODE, String.valueOf(errorCode));
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getMtopApiSign] ISecureSignatureComponent signRequest error,errorCode=" + errorCode, e);
            return sign;
        } catch (Exception e2) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getMtopApiSign] ISecureSignatureComponent signRequest error", e2);
            return sign;
        }
    }

    @Override // mtopsdk.security.AbstractSignImpl, mtopsdk.security.ISign
    public final String getSecBodyDataEx(String str, String str2, String str3, HashMap<String, String> map, int i) {
        try {
            return ((ISecurityBodyComponent) this.sgMgr.getInterface(ISecurityBodyComponent.class)).getSecurityBodyDataEx(str, str2, str3, map, i, getEnv());
        } catch (SecException e) {
            SignStatistics.commitStats(SignStatistics.SignStatsType.TYPE_GET_SECBODY, String.valueOf(e.getErrorCode()), String.valueOf(i));
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getSecBodyDataEx] ISecurityBodyComponent getSecurityBodyDataEx  error.errorCode=" + e.getErrorCode() + ", flag=" + i, e);
            return null;
        } catch (Exception e2) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", ArraySet$$ExternalSyntheticOutline0.m(new StringBuilder(), getInstanceId(), " [getSecBodyDataEx] ISecurityBodyComponent getSecurityBodyDataEx  error,flag=", i), e2);
            return null;
        }
    }

    @Override // mtopsdk.security.AbstractSignImpl, mtopsdk.security.ISign
    public final String getSign(HashMap<String, String> map, String str) {
        String str2 = "";
        String str3 = (String) convertInnerBaseStrMap(str, map, false).get("INPUT");
        try {
            if (this.mMiddleTier != null && !StringUtils.isBlank(str3)) {
                HashMap<String, Object> map2 = new HashMap<>();
                map2.put("data", str3.getBytes("UTF-8"));
                map2.put(SignConstants.MIDDLE_PARAM_ENV, Integer.valueOf(getMiddleTierEnv()));
                map2.put("appkey", str);
                HashMap<String, String> sign = this.mMiddleTier.getSign(map2);
                if (sign != null && !sign.isEmpty()) {
                    String strRemove = sign.remove("x-sign");
                    try {
                        if (!StringUtils.isNotBlank(strRemove)) {
                            return strRemove;
                        }
                        map.putAll(sign);
                        return strRemove;
                    } catch (SecException e) {
                        e = e;
                        str2 = strRemove;
                        TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getSign]get sign failed and SecException errorCode " + e.getErrorCode() + ",appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode, e);
                        return str2;
                    } catch (UnsupportedEncodingException e2) {
                        e = e2;
                        str2 = strRemove;
                        TBSdkLog.e("mtopsdk.InnerSignImpl", NCErrorCode$$ExternalSyntheticOutline0.m(new StringBuilder(), getInstanceId(), " [getSign]your input data transfer to byte utf-8 failed "), "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode, e);
                        return str2;
                    }
                }
                TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getSign]get sign failed with no output ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
                return "";
            }
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getSign]middleTier null or data data ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
            return "";
        } catch (SecException e3) {
            e = e3;
        } catch (UnsupportedEncodingException e4) {
            e = e4;
        }
    }

    @Override // mtopsdk.security.AbstractSignImpl, mtopsdk.security.ISign
    public final HashMap<String, String> getUnifiedSign(HashMap<String, String> map, HashMap<String, String> map2, String str, String str2, boolean z, String str3) {
        String instanceId = getInstanceId();
        if (str == null) {
            map.put(XStateConstants.KEY_SG_ERROR_CODE, "AppKey is null");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getUnifiedSign] AppKey is null.");
            return null;
        }
        if (map == null) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getUnifiedSign] params is null.appKey=" + str);
            return null;
        }
        if (this.mUnifiedSign == null) {
            map.put(XStateConstants.KEY_SG_ERROR_CODE, "unified is null");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [getUnifiedSign]sg unified sign is null, please call ISign init()");
            return null;
        }
        try {
            HashMap<String, Object> map3 = new HashMap<>();
            String str4 = TextUtils.isEmpty(map.get(XStateConstants.KEY_SSR_PV)) ? (String) convertInnerBaseStrMap(str, map, true).get("INPUT") : (String) convertSsrBaseStrMap(str, map).get("INPUT");
            if (StringUtils.isBlank(str4)) {
                TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getUnifiedSign]get sign failed with sign data empty ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
                return null;
            }
            map3.put("appkey", str);
            map3.put("data", str4);
            map3.put(SignConstants.MIDDLE_PARAM_USE_WUA, Boolean.valueOf(z));
            map3.put(SignConstants.MIDDLE_PARAM_ENV, Integer.valueOf(getMiddleTierEnv()));
            map3.put(SignConstants.MIDDLE_PARAM_AUTHCODE, str2);
            map3.put(SignConstants.MIDDLE_PARAM_EXT_PARAM, map2);
            map3.put("requestId", str3);
            map3.put("api", map.get("api"));
            map3.put(XStateConstants.KEY_MTOP_BUSINESS_TAG, map.get(XStateConstants.KEY_MTOP_BUSINESS_TAG));
            try {
                String str5 = map.get("bizId");
                if (!TextUtils.isEmpty(str5)) {
                    map3.put("bizId", Integer.valueOf(Integer.parseInt(str5)));
                }
            } catch (Throwable unused) {
            }
            HashMap<String, String> securityFactors = this.mUnifiedSign.getSecurityFactors(map3);
            if (securityFactors != null && !securityFactors.isEmpty()) {
                return securityFactors;
            }
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getUnifiedSign]get sign failed with no output ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
            return null;
        } catch (SecException e) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getUnifiedSign]get sign failed and SecException errorCode " + e.getErrorCode() + ",appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode, e);
            return null;
        } catch (Throwable th) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getUnifiedSign]get sign failed exception ,appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode, th);
            return null;
        }
    }

    @Override // mtopsdk.security.AbstractSignImpl, mtopsdk.security.ISign
    public final String getWua(HashMap<String, String> map, String str) {
        String str2 = "";
        String str3 = map.get("sign");
        try {
            if (this.mMiddleTier != null && !StringUtils.isBlank(str3)) {
                HashMap<String, Object> map2 = new HashMap<>();
                map2.put("data", str3.getBytes("UTF-8"));
                map2.put(SignConstants.MIDDLE_PARAM_ENV, Integer.valueOf(getMiddleTierEnv()));
                HashMap<String, String> wua = this.mMiddleTier.getWua(map2);
                if (wua != null && !wua.isEmpty()) {
                    String strRemove = wua.remove("wua");
                    try {
                        if (!StringUtils.isNotBlank(strRemove)) {
                            return strRemove;
                        }
                        map.putAll(wua);
                        return strRemove;
                    } catch (SecException e) {
                        e = e;
                        str2 = strRemove;
                        TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getWua]get wua failed and SecException errorCode " + e.getErrorCode() + ",appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode, e);
                        return str2;
                    } catch (UnsupportedEncodingException e2) {
                        e = e2;
                        str2 = strRemove;
                        TBSdkLog.e("mtopsdk.InnerSignImpl", NCErrorCode$$ExternalSyntheticOutline0.m(new StringBuilder(), getInstanceId(), " [getWua]your input data transfer to byte utf-8 failed "), "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode, e);
                        return str2;
                    }
                }
                TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getWua]get wua failed with no output ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
                return "";
            }
            TBSdkLog.e("mtopsdk.InnerSignImpl", getInstanceId() + " [getWua]middleTier null or data data ", "appKeyIndex=" + this.mtopConfig.appKeyIndex + ",authCode=" + this.mtopConfig.authCode);
            return "";
        } catch (SecException e3) {
            e = e3;
        } catch (UnsupportedEncodingException e4) {
            e = e4;
        }
    }

    @Override // mtopsdk.security.AbstractSignImpl, mtopsdk.security.ISign
    public final void init(@NonNull MtopConfig mtopConfig) {
        String appKeyByIndex;
        super.init(mtopConfig);
        final String instanceId = getInstanceId();
        try {
            SignStatistics.setIUploadStats(mtopConfig.uploadStats);
            long jCurrentTimeMillis = System.currentTimeMillis();
            this.sgMgr = SecurityGuardManager.getInstance(this.mtopConfig.context);
            if (StringUtils.isEmpty(mtopConfig.appKey)) {
                int i = mtopConfig.appKeyIndex;
                MtopConfig mtopConfig2 = this.mtopConfig;
                appKeyByIndex = getAppKeyByIndex(i, mtopConfig2 != null ? mtopConfig2.authCode : "");
            } else {
                appKeyByIndex = mtopConfig.appKey;
            }
            MtopConfig mtopConfig3 = this.mtopConfig;
            initUmidToken(appKeyByIndex, mtopConfig3 != null ? mtopConfig3.authCode : "");
            final Context context = this.mtopConfig.context;
            MtopSDKThreadPoolExecutorFactory.submit(new Runnable() { // from class: mtopsdk.security.InnerSignImpl.1
                @Override // java.lang.Runnable
                public final void run() {
                    try {
                        InnerSignImpl.this.getAVMPInstance(context);
                    } catch (Throwable th) {
                        TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [init]getAVMPInstance error when async init AVMP.", th);
                    }
                }
            });
            initMiddleTier(mtopConfig);
            if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.InfoEnable)) {
                TBSdkLog.i("mtopsdk.InnerSignImpl", instanceId + " [init]ISign init SecurityGuard succeed.init time=" + (System.currentTimeMillis() - jCurrentTimeMillis));
            }
        } catch (SecException e) {
            int errorCode = e.getErrorCode();
            SignStatistics.commitStats(SignStatistics.SignStatsType.TYPE_SG_MANAGER, String.valueOf(errorCode), "");
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [init]ISign init SecurityGuard error.errorCode=" + errorCode, e);
        } catch (Exception e2) {
            TBSdkLog.e("mtopsdk.InnerSignImpl", instanceId + " [init]ISign init SecurityGuard error.", e2);
        }
    }
}
