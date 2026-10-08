package mtopsdk.security;

import androidx.annotation.NonNull;
import java.util.HashMap;
import mtopsdk.mtop.global.MtopConfig;

/* JADX INFO: loaded from: classes9.dex */
public interface ISign {

    public static class SignCtx {
        public String authCode;
        public int index;

        public SignCtx(int i, String str) {
            this.index = i;
            this.authCode = str;
        }
    }

    String getAppKey(SignCtx signCtx);

    String getAvmpSign(String str, String str2, int i);

    String getCommonHmacSha1Sign(String str, String str2);

    String getMiniWua(HashMap<String, String> map, HashMap<String, String> map2);

    String getMtopApiSign(HashMap<String, String> map, String str, String str2);

    String getSecBodyDataEx(String str, String str2, String str3, HashMap<String, String> map, int i);

    String getSign(HashMap<String, String> map, String str);

    HashMap<String, String> getUnifiedSign(HashMap<String, String> map, HashMap<String, String> map2, String str, String str2, boolean z, String str3);

    String getWua(HashMap<String, String> map, String str);

    void init(@NonNull MtopConfig mtopConfig);
}
