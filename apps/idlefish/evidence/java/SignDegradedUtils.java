package mtopsdk.security.util;

import android.content.Context;
import android.text.TextUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import mtopsdk.common.util.RemoteConfig;
import mtopsdk.common.util.StringUtils;
import mtopsdk.common.util.TBSdkLog;
import mtopsdk.framework.domain.MtopContext;
import mtopsdk.mtop.global.SwitchConfig;
import mtopsdk.security.SignConfig;

/* JADX INFO: loaded from: classes9.dex */
public class SignDegradedUtils {
    private static ArrayList degradedList;
    private static AtomicBoolean mInitStatus = new AtomicBoolean(false);

    public static void initSignDegradedList(Context context) {
        JSONArray jSONArray;
        if (mInitStatus.compareAndSet(false, true)) {
            SwitchConfig.getInstance().getClass();
            if (SwitchConfig.isABGlobalFeatureOpened(context, SwitchConfig.AB_SIMPLE_LINK_ENABLE)) {
                degradedList = new ArrayList();
                SwitchConfig.getInstance().getClass();
                if (SwitchConfig.isABGlobalFeatureOpened(context, SwitchConfig.AB_SIGN_DEGRADED) && StringUtils.isNotBlank(RemoteConfig.getInstance().signDegradedApiList)) {
                    try {
                        degradedList.addAll(JSON.parseArray(RemoteConfig.getInstance().signDegradedApiList, String.class));
                    } catch (Throwable th) {
                        TBSdkLog.e("mtopsdk.SignDegradedUtils", "[parseSignDegradedList]parse and update signDegradedApiList error.", th);
                    }
                }
                if (StringUtils.isNotBlank(RemoteConfig.getInstance().signDegradedApiList2)) {
                    try {
                        JSONArray array = JSON.parseArray(RemoteConfig.getInstance().signDegradedApiList2);
                        for (int i = 0; i < array.size(); i++) {
                            JSONObject jSONObject = array.getJSONObject(i);
                            String string = jSONObject.getString("ab");
                            if (!TextUtils.isEmpty(string)) {
                                String str = "mtop_sign_degraded_" + string;
                                SwitchConfig.getInstance().getClass();
                                if (SwitchConfig.isABGlobalFeatureOpened(context, str) && (jSONArray = jSONObject.getJSONArray("api")) != null && jSONArray.size() > 0) {
                                    for (int i2 = 0; i2 < jSONArray.size(); i2++) {
                                        String string2 = jSONArray.getString(i2);
                                        if (!TextUtils.isEmpty(string2)) {
                                            degradedList.add(string2);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable th2) {
                        TBSdkLog.e("mtopsdk.SignDegradedUtils", "[parseSignDegradedList2]parse and update signDegradedApiList2 error.", th2);
                    }
                }
                TBSdkLog.e("mtopsdk.SignDegradedUtils", "[initSignDegradedList]signDegradedList=" + degradedList);
            }
        }
    }

    public static boolean isSignDegraded(MtopContext mtopContext) {
        boolean zContains;
        Object th;
        boolean z = false;
        if (mtopContext == null || mtopContext.stats.isSignDegradedRetry) {
            return false;
        }
        ArrayList arrayList = degradedList;
        if (arrayList != null) {
            return arrayList.contains(mtopContext.mtopRequest.getKey());
        }
        try {
            parseSignDegradedList();
            zContains = SwitchConfig.getInstance().signDegradedApiSet != null ? SwitchConfig.getInstance().signDegradedApiSet.contains("*") ? true : SwitchConfig.getInstance().signDegradedApiSet.contains(mtopContext.mtopRequest.getKey()) : false;
            try {
                if (SwitchConfig.getInstance().isEnableSignDegraded() && zContains) {
                    z = true;
                }
                if (z) {
                    return true;
                }
                parseSignDegradedList2();
                if (SwitchConfig.getInstance().signDegradedApiSet2 != null) {
                    for (SignConfig signConfig : SwitchConfig.getInstance().signDegradedApiSet2) {
                        String str = TextUtils.isEmpty(signConfig.getAbExperiment()) ? SwitchConfig.AB_SIGN_DEGRADED : "mtop_sign_degraded_" + signConfig.getAbExperiment();
                        SwitchConfig switchConfig = SwitchConfig.getInstance();
                        Context context = mtopContext.mtopInstance.getMtopConfig().context;
                        switchConfig.getClass();
                        if (SwitchConfig.isABGlobalFeatureOpened(context, str) && (signConfig.checkValid("*") || signConfig.checkValid(mtopContext.mtopRequest.getKey()))) {
                            return true;
                        }
                    }
                }
                return z;
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (Throwable th3) {
            zContains = z;
            th = th3;
        }
        TBSdkLog.e("mtopsdk.SignDegradedUtils", mtopContext.seqNo, "[isSignDegraded] error " + th);
        return zContains;
    }

    public static void parseSignDegradedList() {
        if (SwitchConfig.getInstance().signDegradedApiSet == null && StringUtils.isNotBlank(RemoteConfig.getInstance().signDegradedApiList)) {
            try {
                List array = JSON.parseArray(RemoteConfig.getInstance().signDegradedApiList, String.class);
                if (array != null) {
                    SwitchConfig.getInstance().signDegradedApiSet = new HashSet(array);
                }
            } catch (Throwable th) {
                TBSdkLog.e("mtopsdk.SignDegradedUtils", "[parseSignDegradedList]parse and update signDegradedApiList error.", th);
            }
            if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.InfoEnable)) {
                TBSdkLog.i("mtopsdk.SignDegradedUtils", "[parseSignDegradedList]parse and update signDegradedApiList succeed");
            }
        }
    }

    public static void parseSignDegradedList2() {
        if (SwitchConfig.getInstance().signDegradedApiSet2 == null && StringUtils.isNotBlank(RemoteConfig.getInstance().signDegradedApiList2)) {
            try {
                ArrayList arrayList = new ArrayList();
                org.json.JSONArray jSONArray = new org.json.JSONArray(RemoteConfig.getInstance().signDegradedApiList2);
                for (int i = 0; i < jSONArray.length(); i++) {
                    arrayList.add(SignConfig.create(jSONArray.getJSONObject(i)));
                }
                SwitchConfig.getInstance().signDegradedApiSet2 = new HashSet(arrayList);
            } catch (Throwable th) {
                TBSdkLog.e("mtopsdk.SignDegradedUtils", "[parseSignDegradedList2]parse and update signDegradedApiList2 error.", th);
            }
            if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.InfoEnable)) {
                TBSdkLog.i("mtopsdk.SignDegradedUtils", "[parseSignDegradedList2]parse and update signDegradedApiList2 succeed");
            }
        }
    }
}
