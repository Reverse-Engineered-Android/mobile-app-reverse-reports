package iz4;

import com.tencent.mars.xlog.Log;
import com.tencent.mm.jni.utils.UtilsJni;
import com.tencent.mm.pointers.PByteArray;
import com.tencent.mm.protocal.MMProtocalJni;
import lz4.ei0;

public class dh implements pg {

    public final sg f287329a;

    public dh(eh ehVar, sg sgVar) {
        this.f287329a = sgVar;
    }

    @Override
    public boolean a(PByteArray pByteArray, int i16, byte[] bArr, byte[] bArr2, byte[] bArr3, int i17, boolean z16, int i18, int i19) {
        sg sgVar = this.f287329a;
        qg qgVar = (qg) sgVar;
        long uin = sgVar.getUin();
        if (p15.c.a() && uin == 0) {
            uin = pf.f287383f;
        }
        fi rsaInfo = sgVar.getRsaInfo();
        if (i16 == 722) {
            Log.e("MicroMsg.MMEncryptCheckResUpdate", "MMEncryptCheckResUpdate reqToBuf rsaReqData");
            ei0 ei0Var = ((eh) sgVar).f287332a;
            byte[][] bArrE = com.tencent.mm.modelbase.p2.e(uin, ei0Var.f324463d, ei0Var.f324464e);
            if (bArrE == null) {
                return false;
            }
            if (MMProtocalJni.packHybrid(pByteArray, bArr2, sgVar.getDeviceID(), (int) uin, qgVar.getFuncId(), rsaInfo.f287341c, bArrE[0], bArrE[1], rsaInfo.f287339a.getBytes(), rsaInfo.f287340b.getBytes(), sgVar.getPassKey(), i18, ((eh) sgVar).getRouteInfo(), 0)) {
                int length = pByteArray.value.length;
                return true;
            }
        } else if (i16 == 784) {
            Log.i("MicroMsg.MMEncryptCheckResUpdate", "summerauths rsaInfo[%s] EcdhMgr.USE_ECDH[%s] engine[%s]", Integer.valueOf(rsaInfo.f287341c), Boolean.valueOf(tf.f287403a), Long.valueOf(((qg) sgVar).getECDHEngine()));
            ((qg) sgVar).getFuncId();
            PByteArray pByteArray2 = new PByteArray();
            byte[] protoBuf = ((qg) sgVar).toProtoBuf();
            if (protoBuf == null) {
                Log.f("MicroMsg.MMEncryptCheckResUpdate", "protobuf is null");
                return false;
            }
            long jD = tf.d(null);
            sgVar.setEcdhEngine(jD);
            boolean zPackHybridEcdh = MMProtocalJni.packHybridEcdh(pByteArray2, bArr2, sgVar.getDeviceID(), (int) uin, qgVar.getFuncId(), tf.a(), UtilsJni.HybridEcdhEncrypt(jD, protoBuf), i18, qgVar.getRouteInfo(), 0, 12);
            tf.a();
            int length2 = pByteArray2.value.length;
            return zPackHybridEcdh;
        }
        return false;
    }
}
