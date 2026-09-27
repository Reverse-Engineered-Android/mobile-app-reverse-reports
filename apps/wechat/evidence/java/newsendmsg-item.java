package lz4;

import java.util.LinkedList;
import org.json.JSONObject;

public class bg4 extends com.tencent.mm.protobuf.f {

    public do5 f321747d;

    public String f321748e;

    public int f321749f;

    public int f321750g;

    public int f321751h;

    public String f321752i;

    public String f321753m;

    @Override
    public boolean compareContent(com.tencent.mm.protobuf.f fVar) {
        if (fVar == null || !(fVar instanceof bg4)) {
            return false;
        }
        bg4 bg4Var = (bg4) fVar;
        return g21.f.a(this.f321747d, bg4Var.f321747d) && g21.f.a(this.f321748e, bg4Var.f321748e) && g21.f.a(Integer.valueOf(this.f321749f), Integer.valueOf(bg4Var.f321749f)) && g21.f.a(Integer.valueOf(this.f321750g), Integer.valueOf(bg4Var.f321750g)) && g21.f.a(Integer.valueOf(this.f321751h), Integer.valueOf(bg4Var.f321751h)) && g21.f.a(this.f321752i, bg4Var.f321752i) && g21.f.a(this.f321753m, bg4Var.f321753m);
    }

    @Override
    public final int op(int i16, Object... objArr) {
        if (i16 == 0) {
            nu5.f fVar = (nu5.f) objArr[0];
            do5 do5Var = this.f321747d;
            if (do5Var != null) {
                fVar.i(1, do5Var.computeSize());
                this.f321747d.writeFields(fVar);
            }
            String str = this.f321748e;
            if (str != null) {
                fVar.j(2, str);
            }
            fVar.e(3, this.f321749f);
            fVar.e(4, this.f321750g);
            fVar.e(5, this.f321751h);
            String str2 = this.f321752i;
            if (str2 != null) {
                fVar.j(6, str2);
            }
            String str3 = this.f321753m;
            if (str3 != null) {
                fVar.j(9, str3);
            }
            return 0;
        }
        if (i16 == 1) {
            do5 do5Var2 = this.f321747d;
            int i17 = do5Var2 != null ? 0 + iu5.f.i(1, do5Var2.computeSize()) : 0;
            String str4 = this.f321748e;
            if (str4 != null) {
                i17 += iu5.f.j(2, str4);
            }
            int iE = i17 + iu5.f.e(3, this.f321749f) + iu5.f.e(4, this.f321750g) + iu5.f.e(5, this.f321751h);
            String str5 = this.f321752i;
            if (str5 != null) {
                iE += iu5.f.j(6, str5);
            }
            String str6 = this.f321753m;
            return str6 != null ? iE + iu5.f.j(9, str6) : iE;
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
        bg4 bg4Var = (bg4) objArr[1];
        int iIntValue = ((Integer) objArr[2]).intValue();
        if (iIntValue == 9) {
            bg4Var.f321753m = aVar2.k(iIntValue);
            return 0;
        }
        switch (iIntValue) {
            case 1:
                LinkedList linkedListJ = aVar2.j(iIntValue);
                int size = linkedListJ.size();
                for (int i18 = 0; i18 < size; i18++) {
                    byte[] bArr = (byte[]) linkedListJ.get(i18);
                    do5 do5Var3 = new do5();
                    if (bArr != null && bArr.length > 0) {
                        do5Var3.b(bArr);
                    }
                    bg4Var.f321747d = do5Var3;
                }
                return 0;
            case 2:
                bg4Var.f321748e = aVar2.k(iIntValue);
                return 0;
            case 3:
                bg4Var.f321749f = aVar2.g(iIntValue);
                return 0;
            case 4:
                bg4Var.f321750g = aVar2.g(iIntValue);
                return 0;
            case 5:
                bg4Var.f321751h = aVar2.g(iIntValue);
                return 0;
            case 6:
                bg4Var.f321752i = aVar2.k(iIntValue);
                return 0;
            default:
                return -1;
        }
    }

    @Override
    public Object toJSON() {
        JSONObject jSONObject = new JSONObject();
        try {
            do5 do5Var = this.f321747d;
            g21.e eVar = g21.f.f259069a;
            eVar.d(jSONObject, "ToUserName", do5Var, false);
            eVar.d(jSONObject, "Content", this.f321748e, false);
            eVar.d(jSONObject, "Type", Integer.valueOf(this.f321749f), false);
            eVar.d(jSONObject, "CreateTime", Integer.valueOf(this.f321750g), false);
            eVar.d(jSONObject, "ClientMsgId", Integer.valueOf(this.f321751h), false);
            eVar.d(jSONObject, "MsgSource", this.f321752i, false);
            eVar.d(jSONObject, "SendMsgTicket", this.f321753m, false);
        } catch (Exception unused) {
        }
        return jSONObject;
    }
}
