package iz4;

import android.text.TextUtils;
import com.tencent.mars.xlog.Log;
import com.tencent.mm.pointers.PByteArray;
import com.tencent.mm.protocal.MMProtocalJni;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lz4.be0;
import lz4.c95;
import lz4.co5;
import lz4.hz6;
import lz4.wz6;

public class fg extends ng {

    public final lz4.hc f287336b = new lz4.hc();

    public String f287337c;

    @Override
    public String a() {
        return tf.f287403a ? "/cgi-bin/micromsg-bin/secautoauth" : "/cgi-bin/micromsg-bin/autoauth";
    }

    @Override
    public int getFuncId() {
        return tf.f287403a ? 763 : 702;
    }

    @Override
    public byte[] toProtoBuf() {
        int iC;
        nj0.m.f355568w = "";
        if (getSceneStatus() == 12) {
            iC = 1;
        } else {
            nj0.k1.i();
            iC = nj0.k1.u().f355482a.c(46, 0);
        }
        setRsaInfo(fi.d());
        if (10002 == q21.o1.f385824q && q21.o1.f385825r > 0) {
            q21.o1.f385825r = 0;
            fi.f("", "", 0);
        }
        lz4.hc hcVar = this.f287336b;
        lz4.fc fcVar = hcVar.f326868e;
        fcVar.setBaseRequest(ug.a(this));
        Log.i("MicroMsg.AutoReq", "summerauth autoauth toProtoBuf uin[%d]", Integer.valueOf(getUin()));
        fcVar.f325201f = fo.w0.g(true);
        zi3.r rVar = zi3.r.INSTANCE;
        fcVar.f325202g = rVar.B3(iC);
        fcVar.f325203h = 0;
        fcVar.f325204i = nj0.m.e();
        fcVar.f325205m = com.tencent.mm.sdk.platformtools.s8.k0(com.tencent.mm.sdk.platformtools.v2.f193033a);
        fcVar.f325206n = pf.f287381d;
        fcVar.f325207o = com.tencent.mm.storage.ea.G0();
        fcVar.f325208p = com.tencent.mm.sdk.platformtools.k2.d();
        fcVar.f325209q = "" + com.tencent.mm.sdk.platformtools.s8.l0();
        fcVar.f325210r = com.tencent.mm.sdk.platformtools.a0.f192450b;
        fcVar.f325214v = com.tencent.mm.sdk.platformtools.v2.f193034b;
        nj0.k1.i();
        String str = (String) nj0.k1.u().f355482a.a(18);
        lz4.rd rdVar = fcVar.f325199d;
        int i16 = rdVar.f336175i;
        wz6 wz6Var = rdVar.f336171e;
        co5 co5Var = new co5();
        co5Var.d(com.tencent.mm.sdk.platformtools.s8.i(str));
        wz6Var.f340713g = co5Var;
        try {
            byte[] bArrH = rVar.h();
            hz6 hz6Var = new hz6();
            co5 co5Var2 = new co5();
            co5Var2.d(bArrH);
            hz6Var.f327530f = co5Var2;
            co5 co5Var3 = new co5();
            co5Var3.d(rVar.d9());
            hz6Var.f327534m = co5Var3;
            co5 co5Var4 = new co5();
            co5Var4.d(hz6Var.toByteArray());
            fcVar.f325212t = co5Var4;
        } catch (Throwable th6) {
            Log.printErrStackTrace("MicroMsg.AutoReq", th6, "cc throws exception.", new Object[0]);
        }
        fcVar.f325215w = new c95();
        com.tencent.mm.network.j jVar = com.tencent.mm.network.j.f74069e;
        String strA = jVar.a();
        if (TextUtils.isEmpty(strA)) {
            c95 c95Var = fcVar.f325215w;
            c95Var.f322544d = 0;
            co5 co5Var5 = new co5();
            co5Var5.d(new byte[0]);
            c95Var.f322545e = co5Var5;
            Log.e("MicroMsg.AutoReq", "get sign key failed");
        } else {
            c95 c95Var2 = fcVar.f325215w;
            jVar.b();
            c95Var2.f322544d = jVar.f74070a.f322544d;
            c95 c95Var3 = fcVar.f325215w;
            co5 co5Var6 = new co5();
            co5Var6.d(strA.getBytes(StandardCharsets.ISO_8859_1));
            c95Var3.f322545e = co5Var6;
            Log.i("MicroMsg.AutoReq", "autoauth add public key , length " + strA.length());
        }
        com.tencent.mm.network.i iVar = new com.tencent.mm.network.i();
        iVar.f74046b = jVar.a();
        jVar.b();
        iVar.f74045a = jVar.f74071b;
        setCGiVerifyKey(iVar);
        lz4.ic icVar = hcVar.f326867d;
        be0 be0Var = new be0();
        be0Var.f321701d = 713;
        PByteArray pByteArray = new PByteArray();
        PByteArray pByteArray2 = new PByteArray();
        MMProtocalJni.generateECKey(be0Var.f321701d, pByteArray, pByteArray2);
        byte[] bArr = pByteArray.value;
        byte[] bArr2 = pByteArray2.value;
        this.f287366a = bArr2 != null ? bArr2 : new byte[0];
        com.tencent.mm.sdk.platformtools.s8.k(bArr);
        com.tencent.mm.sdk.platformtools.s8.k(bArr2);
        co5 co5Var7 = new co5();
        co5Var7.d(bArr);
        be0Var.f321702e = co5Var7;
        icVar.f327886e = be0Var;
        Log.i("MicroMsg.AutoReq", "summerauth auto IMEI:%s SoftType:%s ClientSeqID:%s Signature:%s DeviceName:%s DeviceType:%s Language:%s TimeZone:%s AndroidPackageName:%s chan[%d,%d,%d]", fcVar.f325201f, fcVar.f325202g, fcVar.f325204i, fcVar.f325205m, fcVar.f325206n, fcVar.f325207o, fcVar.f325208p, fcVar.f325209q, fcVar.f325214v, Integer.valueOf(fcVar.f325210r), Integer.valueOf(com.tencent.mm.sdk.platformtools.a0.f192450b), Integer.valueOf(com.tencent.mm.sdk.platformtools.a0.f192451c));
        try {
            return hcVar.toByteArray();
        } catch (IOException e16) {
            Log.e("MicroMsg.AutoReq", "summerauth toProtoBuf :%s", com.tencent.mm.sdk.platformtools.x3.c(e16));
            return null;
        }
    }
}
