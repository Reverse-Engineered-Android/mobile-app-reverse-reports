package lz4;

import java.util.LinkedList;
import org.json.JSONObject;

public class cg4 extends km5 {

    public int f322695d;

    public do5 f322696e;

    public int f322697f;

    public int f322698g;

    public int f322699h;

    public int f322700i;

    public int f322701m;

    public long f322702n;

    public String f322703o;

    @Override
    public boolean compareContent(com.tencent.mm.protobuf.f fVar) {
        if (fVar == null || !(fVar instanceof cg4)) {
            return false;
        }
        cg4 cg4Var = (cg4) fVar;
        return g21.f.a(Integer.valueOf(this.f322695d), Integer.valueOf(cg4Var.f322695d)) && g21.f.a(this.f322696e, cg4Var.f322696e) && g21.f.a(Integer.valueOf(this.f322697f), Integer.valueOf(cg4Var.f322697f)) && g21.f.a(Integer.valueOf(this.f322698g), Integer.valueOf(cg4Var.f322698g)) && g21.f.a(Integer.valueOf(this.f322699h), Integer.valueOf(cg4Var.f322699h)) && g21.f.a(Integer.valueOf(this.f322700i), Integer.valueOf(cg4Var.f322700i)) && g21.f.a(Integer.valueOf(this.f322701m), Integer.valueOf(cg4Var.f322701m)) && g21.f.a(Long.valueOf(this.f322702n), Long.valueOf(cg4Var.f322702n)) && g21.f.a(this.f322703o, cg4Var.f322703o);
    }

    @Override
    public final int op(int i16, Object... objArr) {
        if (i16 == 0) {
            nu5.f fVar = (nu5.f) objArr[0];
            fVar.e(1, this.f322695d);
            do5 do5Var = this.f322696e;
            if (do5Var != null) {
                fVar.i(2, do5Var.computeSize());
                this.f322696e.writeFields(fVar);
            }
            fVar.e(3, this.f322697f);
            fVar.e(4, this.f322698g);
            fVar.e(5, this.f322699h);
            fVar.e(6, this.f322700i);
            fVar.e(7, this.f322701m);
            fVar.h(8, this.f322702n);
            String str = this.f322703o;
            if (str != null) {
                fVar.j(9, str);
            }
            return 0;
        }
        if (i16 == 1) {
            int iE = iu5.f.e(1, this.f322695d) + 0;
            do5 do5Var2 = this.f322696e;
            if (do5Var2 != null) {
                iE += iu5.f.i(2, do5Var2.computeSize());
            }
            int iE2 = iE + iu5.f.e(3, this.f322697f) + iu5.f.e(4, this.f322698g) + iu5.f.e(5, this.f322699h) + iu5.f.e(6, this.f322700i) + iu5.f.e(7, this.f322701m) + iu5.f.h(8, this.f322702n);
            String str2 = this.f322703o;
            return str2 != null ? iE2 + iu5.f.j(9, str2) : iE2;
        }
        if (i16 == 2) {
            ju5.a aVar = new ju5.a((byte[]) objArr[0], com.tencent.mm.protobuf.f.unknownTagHandler);
            for (int nextFieldNumber = com.tencent.mm.protobuf.f.getNextFieldNumber(aVar); nextFieldNumber > 0; nextFieldNumber = com.tencent.mm.protobuf.f.getNextFieldNumber(aVar)) {
                if (!super.populateBuilderWithField(aVar, this, nextFieldNumber)) {
                    aVar.b();
                }
            }
            return 0;
        }
        if (i16 != 3) {
            return -1;
        }
        ju5.a aVar2 = (ju5.a) objArr[0];
        cg4 cg4Var = (cg4) objArr[1];
        int iIntValue = ((Integer) objArr[2]).intValue();
        switch (iIntValue) {
            case 1:
                cg4Var.f322695d = aVar2.g(iIntValue);
                return 0;
            case 2:
                LinkedList linkedListJ = aVar2.j(iIntValue);
                int size = linkedListJ.size();
                for (int i17 = 0; i17 < size; i17++) {
                    byte[] bArr = (byte[]) linkedListJ.get(i17);
                    do5 do5Var3 = new do5();
                    if (bArr != null && bArr.length > 0) {
                        do5Var3.b(bArr);
                    }
                    cg4Var.f322696e = do5Var3;
                }
                return 0;
            case 3:
                cg4Var.f322697f = aVar2.g(iIntValue);
                return 0;
            case 4:
                cg4Var.f322698g = aVar2.g(iIntValue);
                return 0;
            case 5:
                cg4Var.f322699h = aVar2.g(iIntValue);
                return 0;
            case 6:
                cg4Var.f322700i = aVar2.g(iIntValue);
                return 0;
            case 7:
                cg4Var.f322701m = aVar2.g(iIntValue);
                return 0;
            case 8:
                cg4Var.f322702n = aVar2.i(iIntValue);
                return 0;
            case 9:
                cg4Var.f322703o = aVar2.k(iIntValue);
                return 0;
            default:
                return -1;
        }
    }

    @Override
    public Object toJSON() {
        JSONObject jSONObject = new JSONObject();
        try {
            Integer numValueOf = Integer.valueOf(this.f322695d);
            g21.e eVar = g21.f.f259069a;
            eVar.d(jSONObject, "Ret", numValueOf, false);
            eVar.d(jSONObject, "ToUserName", this.f322696e, false);
            eVar.d(jSONObject, "MsgId", Integer.valueOf(this.f322697f), false);
            eVar.d(jSONObject, "ClientMsgId", Integer.valueOf(this.f322698g), false);
            eVar.d(jSONObject, "CreateTime", Integer.valueOf(this.f322699h), false);
            eVar.d(jSONObject, "ServerTime", Integer.valueOf(this.f322700i), false);
            eVar.d(jSONObject, "Type", Integer.valueOf(this.f322701m), false);
            eVar.d(jSONObject, "NewMsgId", Long.valueOf(this.f322702n), false);
            eVar.d(jSONObject, "MsgSource", this.f322703o, false);
        } catch (Exception unused) {
        }
        return jSONObject;
    }
}
