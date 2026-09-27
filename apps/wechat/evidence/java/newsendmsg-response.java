package lz4;

import java.util.LinkedList;
import org.json.JSONObject;

public class gt5 extends km5 {

    public int f326423d;

    public final LinkedList f326424e = new LinkedList();

    public int f326425f;

    @Override
    public boolean compareContent(com.tencent.mm.protobuf.f fVar) {
        if (fVar == null || !(fVar instanceof gt5)) {
            return false;
        }
        gt5 gt5Var = (gt5) fVar;
        return g21.f.a(this.BaseResponse, gt5Var.BaseResponse) && g21.f.a(Integer.valueOf(this.f326423d), Integer.valueOf(gt5Var.f326423d)) && g21.f.a(this.f326424e, gt5Var.f326424e) && g21.f.a(Integer.valueOf(this.f326425f), Integer.valueOf(gt5Var.f326425f));
    }

    @Override
    public final int op(int i16, Object... objArr) {
        LinkedList linkedList = this.f326424e;
        if (i16 == 0) {
            nu5.f fVar = (nu5.f) objArr[0];
            ae aeVar = this.BaseResponse;
            if (aeVar != null) {
                fVar.i(1, aeVar.computeSize());
                this.BaseResponse.writeFields(fVar);
            }
            fVar.e(2, this.f326423d);
            fVar.g(3, 8, linkedList);
            fVar.e(4, this.f326425f);
            return 0;
        }
        if (i16 == 1) {
            ae aeVar2 = this.BaseResponse;
            return (aeVar2 != null ? 0 + iu5.f.i(1, aeVar2.computeSize()) : 0) + iu5.f.e(2, this.f326423d) + iu5.f.g(3, 8, linkedList) + iu5.f.e(4, this.f326425f);
        }
        if (i16 == 2) {
            byte[] bArr = (byte[]) objArr[0];
            linkedList.clear();
            ju5.a aVar = new ju5.a(bArr, com.tencent.mm.protobuf.f.unknownTagHandler);
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
        gt5 gt5Var = (gt5) objArr[1];
        int iIntValue = ((Integer) objArr[2]).intValue();
        if (iIntValue == 1) {
            LinkedList linkedListJ = aVar2.j(iIntValue);
            int size = linkedListJ.size();
            for (int i17 = 0; i17 < size; i17++) {
                byte[] bArr2 = (byte[]) linkedListJ.get(i17);
                ae aeVar3 = new ae();
                if (bArr2 != null && bArr2.length > 0) {
                    aeVar3.parseFrom(bArr2);
                }
                gt5Var.BaseResponse = aeVar3;
            }
            return 0;
        }
        if (iIntValue == 2) {
            gt5Var.f326423d = aVar2.g(iIntValue);
            return 0;
        }
        if (iIntValue != 3) {
            if (iIntValue != 4) {
                return -1;
            }
            gt5Var.f326425f = aVar2.g(iIntValue);
            return 0;
        }
        LinkedList linkedListJ2 = aVar2.j(iIntValue);
        int size2 = linkedListJ2.size();
        for (int i18 = 0; i18 < size2; i18++) {
            byte[] bArr3 = (byte[]) linkedListJ2.get(i18);
            cg4 cg4Var = new cg4();
            if (bArr3 != null && bArr3.length > 0) {
                cg4Var.parseFrom(bArr3);
            }
            gt5Var.f326424e.add(cg4Var);
        }
        return 0;
    }

    @Override
    public Object toJSON() {
        JSONObject jSONObject = new JSONObject();
        try {
            ae aeVar = this.BaseResponse;
            g21.e eVar = g21.f.f259069a;
            eVar.d(jSONObject, "BaseResponse", aeVar, false);
            eVar.d(jSONObject, "Count", Integer.valueOf(this.f326423d), false);
            eVar.d(jSONObject, "List", this.f326424e, false);
            eVar.d(jSONObject, "ActionFlag", Integer.valueOf(this.f326425f), false);
        } catch (Exception unused) {
        }
        return jSONObject;
    }
}
