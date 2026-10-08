package mtopsdk.security;

import androidx.collection.ArraySet$$ExternalSyntheticOutline0;
import defpackage.NCErrorCode$$ExternalSyntheticOutline0;
import java.util.HashMap;
import java.util.Map;
import mtopsdk.common.util.StringUtils;
import mtopsdk.common.util.TBSdkLog;
import mtopsdk.security.ISign;
import mtopsdk.security.util.HmacSha1Utils;
import mtopsdk.security.util.SecurityUtils;
import mtopsdk.xstate.util.XStateConstants;

/* JADX INFO: loaded from: classes9.dex */
public class LocalInnerSignImpl extends AbstractSignImpl {
    private static String convertInnerBaseStrMap(String str, Map map) {
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
        if (StringUtils.isNotBlank(str14)) {
            sb.append(str14);
            sb.append("&");
        }
        sb.append(str15);
        return sb.toString();
    }

    @Override // mtopsdk.security.ISign
    public final String getAppKey(ISign.SignCtx signCtx) {
        return null;
    }

    @Override // mtopsdk.security.ISign
    public final String getCommonHmacSha1Sign(String str, String str2) {
        String instanceId = getInstanceId();
        if (StringUtils.isBlank(str)) {
            TBSdkLog.e("mtopsdk.LocalInnerSignImpl", instanceId + " [getCommonHmacSha1Sign] baseStr is null.appKey=" + str2);
            return null;
        }
        if (str2 != null && str2.equals(null)) {
            HmacSha1Utils.hmacSha1Hex(str);
            return null;
        }
        StringBuilder sb = new StringBuilder(64);
        sb.append(instanceId);
        sb.append(" [getCommonHmacSha1Sign] request appKey mismatches global appKey.requestAppKey=");
        sb.append(str2);
        sb.append(",globalAppKey=null");
        TBSdkLog.e("mtopsdk.LocalInnerSignImpl", sb.toString());
        return null;
    }

    @Override // mtopsdk.security.ISign
    public final String getMtopApiSign(HashMap<String, String> map, String str, String str2) {
        String instanceId = getInstanceId();
        if (map == null) {
            TBSdkLog.e("mtopsdk.LocalInnerSignImpl", instanceId + " [getMtopApiSign] params is null.appKey=" + str);
            return null;
        }
        if (str == null) {
            map.put(XStateConstants.KEY_SG_ERROR_CODE, "AppKey is null");
            TBSdkLog.e("mtopsdk.LocalInnerSignImpl", instanceId + " [getMtopApiSign] AppKey is null.");
            return null;
        }
        try {
            return getCommonHmacSha1Sign(convertInnerBaseStrMap(str, map), str);
        } catch (Exception e) {
            TBSdkLog.e("mtopsdk.LocalInnerSignImpl", instanceId + " [getMtopApiSign] ISecureSignatureComponent signRequest error", e);
            return null;
        }
    }
}
