package com.tencent.mm.protocal;

import com.tencent.mm.pointers.PByteArray;
import com.tencent.mm.pointers.PInt;

public final class MMProtocalJni {
    public static native byte[] aesDecrypt(byte[] bArr, byte[] bArr2);

    public static native int aesDecryptFile(String str, String str2, byte[] bArr);

    public static native byte[] aesEncrypt(byte[] bArr, byte[] bArr2);

    public static native int compress(byte[] bArr, PByteArray pByteArray, int i16, int i17);

    public static native int computerKeyWithAllStr(int i16, byte[] bArr, byte[] bArr2, PByteArray pByteArray, int i17);

    public static native byte[] decodeSecureNotifyData(byte[] bArr, int i16, int i17, int i18, int i19, int i26, int i27, int i28, byte[] bArr2);

    public static native void genClientCheckKVRes(int i16, String str, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4, PByteArray pByteArray);

    public static native int genSignature(int i16, byte[] bArr, byte[] bArr2);

    public static native int generateECKey(int i16, PByteArray pByteArray, PByteArray pByteArray2);

    public static native boolean mergeSyncKey(byte[] bArr, byte[] bArr2, PByteArray pByteArray);

    public static native boolean pack(byte[] bArr, PByteArray pByteArray, byte[] bArr2, int i16, byte[] bArr3, String str, int i17, int i18, int i19, byte[] bArr4, byte[] bArr5, int i26, int i27, int i28, int i29, int i36, int i37, int i38);

    public static native boolean packDoubleHybrid(PByteArray pByteArray, byte[] bArr, String str, int i16, int i17, int i18, byte[] bArr2, byte[] bArr3, byte[] bArr4, byte[] bArr5, byte[] bArr6, int i19, int i26, int i27);

    public static native boolean packHybrid(PByteArray pByteArray, byte[] bArr, String str, int i16, int i17, int i18, byte[] bArr2, byte[] bArr3, byte[] bArr4, byte[] bArr5, byte[] bArr6, int i19, int i26, int i27);

    public static native boolean packHybridEcdh(PByteArray pByteArray, byte[] bArr, String str, int i16, int i17, int i18, byte[] bArr2, int i19, int i26, int i27, int i28);

    public static native boolean rsaPublicEncrypt(byte[] bArr, PByteArray pByteArray, byte[] bArr2, byte[] bArr3);

    public static native boolean rsaPublicEncryptPemkey(byte[] bArr, PByteArray pByteArray, byte[] bArr2);

    public static native boolean setClientPackVersion(int i16);

    public static native void setDeviceTypeId(int i16);

    public static native void setIsLite(boolean z16);

    public static native boolean setProtocalJniLogLevel(int i16);

    public static native boolean unpack(PByteArray pByteArray, byte[] bArr, byte[] bArr2, PByteArray pByteArray2, PInt pInt, PInt pInt2, PInt pInt3, PInt pInt4, PInt pInt5, PInt pInt6, PInt pInt7);

    public static native boolean verifySyncKey(byte[] bArr);
}
