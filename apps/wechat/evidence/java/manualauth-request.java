package iz4;

import android.text.TextUtils;
import com.tencent.mars.xlog.Log;
import com.tencent.mm.pointers.PByteArray;
import com.tencent.mm.protocal.MMProtocalJni;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lz4.ad4;
import lz4.bd4;
import lz4.be0;
import lz4.c95;
import lz4.cd4;
import lz4.co5;
import lz4.hz6;
import lz4.wz6;

public class lg extends ng {

    public final bd4 f287357b = new bd4();

    public boolean f287358c = false;

    @Override
    public String a() {
        return tf.f287403a ? "/cgi-bin/micromsg-bin/secmanualauth" : "/cgi-bin/micromsg-bin/manualauth";
    }

    @Override
    public int getFuncId() {
        return tf.f287403a ? 252 : 701;
    }

    @Override
    public byte[] toProtoBuf() {
        int iC;
        int i16;
        nj0.m.f355568w = "";
        if (getSceneStatus() == 16) {
            iC = 1;
        } else if (this.f287358c) {
            iC = 3;
        } else {
            nj0.k1.i();
            iC = nj0.k1.u().f355482a.c(46, 0);
        }
        if (10002 == q21.o1.f385824q && q21.o1.f385825r > 0) {
            q21.o1.f385825r = 0;
            fi.f("", "", 0);
        }
        setRsaInfo(fi.d());
        bd4 bd4Var = this.f287357b;
        ad4 ad4Var = bd4Var.f321683e;
        ad4Var.setBaseRequest(ug.a(this));
        ad4Var.f320810e = fo.w0.g(true);
        zi3.r rVar = zi3.r.INSTANCE;
        ad4Var.f320811f = rVar.B3(iC);
        ad4Var.f320812g = 0;
        ad4Var.f320813h = nj0.m.e();
        ad4Var.f320814i = com.tencent.mm.sdk.platformtools.s8.k0(com.tencent.mm.sdk.platformtools.v2.f193033a);
        ad4Var.f320815m = pf.f287381d;
        ad4Var.f320816n = com.tencent.mm.storage.ea.G0();
        ad4Var.f320817o = com.tencent.mm.sdk.platformtools.k2.d();
        ad4Var.f320818p = "" + com.tencent.mm.sdk.platformtools.s8.l0();
        ad4Var.f320819q = com.tencent.mm.sdk.platformtools.a0.f192450b;
        if (10012 == q21.o1.f385824q && (i16 = q21.o1.f385825r) > 0) {
            ad4Var.f320819q = i16;
        }
        ad4Var.f320821s = pf.f287379b;
        ad4Var.f320822t = pf.f287380c;
        ad4Var.f320823u = fo.q.f245249e;
        ad4Var.f320824v = fo.w0.o();
        ad4Var.C = com.tencent.mm.sdk.platformtools.v2.f193034b;
        nj0.k1.i();
        String str = (String) nj0.k1.u().f355482a.a(18);
        lz4.rd rdVar = ad4Var.f320809d;
        int i17 = rdVar.f336175i;
        wz6 wz6Var = rdVar.f336171e;
        co5 co5Var = new co5();
        co5Var.d(com.tencent.mm.sdk.platformtools.s8.i(str));
        wz6Var.f340713g = co5Var;
        try {
            byte[] bArrH = rVar.h();
            hz6 hz6Var = new hz6();
            int i18 = ad4Var.f320828z;
            if (2 == i18 || 1 == i18 || i18 == 0) {
                if (rVar.Ae("ie_login_id")) {
                    rVar.w7("ie_login_id");
                }
                co5 co5Var2 = new co5();
                co5Var2.d(rVar.bh("ie_login_id"));
                hz6Var.f327528d = co5Var2;
                co5 co5Var3 = new co5();
                co5Var3.d(rVar.Gh("ce_login_id"));
                hz6Var.f327529e = co5Var3;
                String strXe = rVar.Xe("ce_login_id");
                if (strXe != null) {
                    co5 co5Var4 = new co5();
                    co5Var4.d(strXe.getBytes());
                    hz6Var.f327535n = co5Var4;
                }
            }
            co5 co5Var5 = new co5();
            co5Var5.d(rVar.d9());
            hz6Var.f327534m = co5Var5;
            co5 co5Var6 = new co5();
            co5Var6.d(bArrH);
            hz6Var.f327530f = co5Var6;
            co5 co5Var7 = new co5();
            co5Var7.d(hz6Var.toByteArray());
            ad4Var.B = co5Var7;
        } catch (Throwable th6) {
            Log.printErrStackTrace("MicroMsg.ManualReq", th6, "cc throws exception.", new Object[0]);
        }
        cd4 cd4Var = bd4Var.f321682d;
        co5 co5Var8 = new co5();
        co5Var8.d(com.tencent.mm.sdk.platformtools.s8.t0());
        cd4Var.f322629d = co5Var8;
        be0 be0Var = new be0();
        be0Var.f321701d = 713;
        PByteArray pByteArray = new PByteArray();
        PByteArray pByteArray2 = new PByteArray();
        MMProtocalJni.generateECKey(be0Var.f321701d, pByteArray, pByteArray2);
        byte[] bArr = pByteArray.value;
        byte[] bArr2 = pByteArray2.value;
        this.f287366a = bArr2 != null ? bArr2 : new byte[0];
        ad4Var.D = new c95();
        com.tencent.mm.network.j jVar = com.tencent.mm.network.j.f74069e;
        String strA = jVar.a();
        if (TextUtils.isEmpty(strA)) {
            c95 c95Var = ad4Var.D;
            c95Var.f322544d = 0;
            co5 co5Var9 = new co5();
            co5Var9.d(new byte[0]);
            c95Var.f322545e = co5Var9;
            Log.e("MicroMsg.ManualReq", "get sign key failed");
        } else {
            c95 c95Var2 = ad4Var.D;
            jVar.b();
            c95Var2.f322544d = jVar.f74070a.f322544d;
            c95 c95Var3 = ad4Var.D;
            co5 co5Var10 = new co5();
            co5Var10.d(strA.getBytes(StandardCharsets.ISO_8859_1));
            c95Var3.f322545e = co5Var10;
            Log.i("MicroMsg.ManualReq", "manualauth add public key , length " + strA.length());
        }
        com.tencent.mm.network.i iVar = new com.tencent.mm.network.i();
        iVar.f74046b = jVar.a();
        jVar.b();
        iVar.f74045a = jVar.f74071b;
        setCGiVerifyKey(iVar);
        com.tencent.mm.sdk.platformtools.s8.k(bArr);
        com.tencent.mm.sdk.platformtools.s8.k(bArr2);
        co5 co5Var11 = new co5();
        co5Var11.d(bArr);
        be0Var.f321702e = co5Var11;
        cd4Var.f322630e = be0Var;
        Log.i("MicroMsg.ManualReq", "summerauth manual IMEI:%s SoftType:%s ClientSeqID:%s Signature:%s DeviceName:%s DeviceType:%s Language:%s TimeZone:%s chan[%d,%d,%d] DeviceBrand:%s DeviceModel:%s OSType:%s RealCountry:%s AndroidPackageName:%s", ad4Var.f320810e, ad4Var.f320811f, ad4Var.f320813h, ad4Var.f320814i, ad4Var.f320815m, ad4Var.f320816n, ad4Var.f320817o, ad4Var.f320818p, Integer.valueOf(ad4Var.f320819q), Integer.valueOf(com.tencent.mm.sdk.platformtools.a0.f192450b), Integer.valueOf(com.tencent.mm.sdk.platformtools.a0.f192451c), ad4Var.f320821s, ad4Var.f320822t, ad4Var.f320823u, ad4Var.f320824v, ad4Var.C);
        try {
            return bd4Var.toByteArray();
        } catch (IOException e16) {
            Log.e("MicroMsg.ManualReq", "summerauth toProtoBuf :%s", com.tencent.mm.sdk.platformtools.x3.c(e16));
            return null;
        }
    }
}
