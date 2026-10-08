package com.ut.mini.core.sign;

import android.content.Context;
import com.alibaba.analytics.core.Variables;
import com.alibaba.analytics.utils.Logger;
import com.alibaba.wireless.security.open.SecurityGuardManager;
import com.alibaba.wireless.security.open.SecurityGuardParamContext;
import com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent;
import com.taobao.idlefish.home.SectionAttrs;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/* JADX INFO: loaded from: classes8.dex */
class SecuritySDK {
    private static final String TAG = "SecuritySDK";
    private String mAppkey;
    private String mAuthcode;
    private Object s_securityGuardManagerObj = null;
    private Object s_secureSignatureCompObj = null;
    private Class s_securityGuardParamContextClz = null;
    private Field s_securityGuardParamContext_appKey = null;
    private Field s_securityGuardParamContext_paramMap = null;
    private Field s_securityGuardParamContext_requestType = null;
    private Method s_signRequestMethod = null;
    private int s_secureIndex = 3;
    private boolean isInitSecurityCheck = false;

    public SecuritySDK(String appkey, String authCode) {
        this.mAppkey = appkey;
        this.mAuthcode = authCode;
    }

    private synchronized void initSecurityCheck() {
        Class<SecurityGuardManager> cls;
        Object th;
        if (this.isInitSecurityCheck) {
            return;
        }
        try {
            cls = SecurityGuardManager.class;
            int i = SecurityGuardManager.$r8$clinit;
            try {
                Method method = cls.getMethod("getInstance", Context.class);
                Variables.s_instance.getClass();
                this.s_securityGuardManagerObj = method.invoke(null, Variables.getContext());
                this.s_secureSignatureCompObj = cls.getMethod("getSecureSignatureComp", new Class[0]).invoke(this.s_securityGuardManagerObj, new Object[0]);
            } catch (Throwable th2) {
                th = th2;
                Logger.w(TAG, "initSecurityCheck", th);
            }
        } catch (Throwable th3) {
            cls = null;
            th = th3;
        }
        if (cls == null) {
            this.isInitSecurityCheck = true;
            return;
        }
        try {
            this.s_securityGuardParamContextClz = SecurityGuardParamContext.class;
            this.s_securityGuardParamContext_appKey = SecurityGuardParamContext.class.getDeclaredField("appKey");
            this.s_securityGuardParamContext_paramMap = this.s_securityGuardParamContextClz.getDeclaredField("paramMap");
            this.s_securityGuardParamContext_requestType = this.s_securityGuardParamContextClz.getDeclaredField(SectionAttrs.REQUEST_TYPE);
            this.s_signRequestMethod = ISecureSignatureComponent.class.getMethod("signRequest", this.s_securityGuardParamContextClz, String.class);
        } catch (Throwable th4) {
            Logger.w(TAG, "initSecurityCheck", th4);
        }
        this.isInitSecurityCheck = true;
        return;
    }

    public String getSign(String toBeSignedStr) {
        Class cls;
        Logger.d(TAG, "toBeSignedStr", toBeSignedStr);
        if (!this.isInitSecurityCheck) {
            initSecurityCheck();
        }
        String str = null;
        if (this.mAppkey == null) {
            Logger.d(TAG, "There is no appkey,please check it!");
            return null;
        }
        if (toBeSignedStr == null) {
            return null;
        }
        Object obj = this.s_securityGuardManagerObj;
        if (obj == null || (cls = this.s_securityGuardParamContextClz) == null || this.s_securityGuardParamContext_appKey == null || this.s_securityGuardParamContext_paramMap == null || this.s_securityGuardParamContext_requestType == null || this.s_signRequestMethod == null || this.s_secureSignatureCompObj == null) {
            Logger.w(TAG, "UTSecurityThridRequestAuthentication.getSign s_securityGuardManagerObj", obj, "s_securityGuardParamContextClz", this.s_securityGuardParamContextClz, "s_securityGuardParamContext_appKey", this.s_securityGuardParamContext_appKey, "s_securityGuardParamContext_paramMap", this.s_securityGuardParamContext_paramMap, "s_securityGuardParamContext_requestType", this.s_securityGuardParamContext_requestType, "s_signRequestMethod", this.s_signRequestMethod);
        } else {
            try {
                Object objNewInstance = cls.newInstance();
                this.s_securityGuardParamContext_appKey.set(objNewInstance, this.mAppkey);
                ((Map) this.s_securityGuardParamContext_paramMap.get(objNewInstance)).put("INPUT", toBeSignedStr);
                this.s_securityGuardParamContext_requestType.set(objNewInstance, Integer.valueOf(this.s_secureIndex));
                str = (String) this.s_signRequestMethod.invoke(this.s_secureSignatureCompObj, objNewInstance, this.mAuthcode);
            } catch (Exception e) {
                Logger.e(TAG, e, new Object[0]);
            }
        }
        Logger.d(TAG, "lSignedStr", str);
        return str;
    }
}
