package com.ut.mini.core.sign;

import com.alibaba.analytics.utils.Logger;
import com.alibaba.analytics.utils.MD5Utils;
import com.alibaba.analytics.utils.RC4;
import com.alipay.multimedia.img.utils.ImageFileType;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/* JADX INFO: loaded from: classes8.dex */
public class UTBaseRequestAuthentication implements IUTRequestAuthentication {
    private String mAppSecret;
    private String mAppkey;
    private byte[] mDefaultAppAppSecret;
    private boolean mEncode;

    public UTBaseRequestAuthentication(String aAppkey, String aAppSecret) {
        this.mEncode = false;
        this.mDefaultAppAppSecret = null;
        this.mAppkey = aAppkey;
        this.mAppSecret = aAppSecret;
    }

    public static String calcHmac(byte[] key, byte[] src) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, mac.getAlgorithm()));
        return MD5Utils.toHexString(mac.doFinal(src));
    }

    private byte[] getDefaultAppAppSecret() {
        if (this.mDefaultAppAppSecret == null) {
            this.mDefaultAppAppSecret = RC4.rc4(new byte[]{66, 37, 42, -119, 118, -104, -30, 4, -95, 15, -26, -12, -75, -102, ImageFileType.HEAD_GIF_0, 23, -3, -120, -1, -57, 42, 99, -16, -101, 103, -74, 93, -114, 112, -26, -24, -24});
        }
        return this.mDefaultAppAppSecret;
    }

    public String getAppSecret() {
        return this.mAppSecret;
    }

    @Override // com.ut.mini.core.sign.IUTRequestAuthentication
    public String getAppkey() {
        return this.mAppkey;
    }

    @Override // com.ut.mini.core.sign.IUTRequestAuthentication
    public String getSign(String toBeSignedStr) {
        String str;
        if (this.mAppkey == null || (str = this.mAppSecret) == null) {
            Logger.e("UTBaseRequestAuthentication", "There is no appkey,please check it!");
            return null;
        }
        if (toBeSignedStr == null) {
            return null;
        }
        try {
            return this.mEncode ? calcHmac(str.getBytes(), toBeSignedStr.getBytes()) : calcHmac(getDefaultAppAppSecret(), toBeSignedStr.getBytes());
        } catch (Exception unused) {
            return "";
        }
    }

    public boolean isEncode() {
        return this.mEncode;
    }

    public UTBaseRequestAuthentication(String aAppkey, String aAppSecret, boolean isEncode) {
        this.mDefaultAppAppSecret = null;
        this.mAppkey = aAppkey;
        this.mAppSecret = aAppSecret;
        this.mEncode = isEncode;
    }
}
