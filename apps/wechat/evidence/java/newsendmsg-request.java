package lz4;

import java.util.LinkedList;
import org.json.JSONObject;

public class ft5 extends ol5 {

    public int f325613d;

    public final LinkedList f325614e = new LinkedList();

    @Override
    public boolean compareContent(com.tencent.mm.protobuf.f fVar) {
        if (fVar == null || !(fVar instanceof ft5)) {
            return false;
        }
        ft5 ft5Var = (ft5) fVar;
        return g21.f.a(Integer.valueOf(this.f325613d), Integer.valueOf(ft5Var.f325613d)) && g21.f.a(this.f325614e, ft5Var.f325614e);
    }

    @Override
    public final int op(int i16, Object... objArr) {
        LinkedList linkedList = this.f325614e;
        if (i16 == 0) {
            nu5.f fVar = (nu5.f) objArr[0];
            fVar.e(1, this.f325613d);
            fVar.g(2, 8, linkedList);
            return 0;
        }
        if (i16 == 1) {
            return iu5.f.e(1, this.f325613d) + 0 + iu5.f.g(2, 8, linkedList);
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
        ft5 ft5Var = (ft5) objArr[1];
        int iIntValue = ((Integer) objArr[2]).intValue();
        if (iIntValue == 1) {
            ft5Var.f325613d = aVar2.g(iIntValue);
            return 0;
        }
        if (iIntValue != 2) {
            return -1;
        }
        LinkedList linkedListJ = aVar2.j(iIntValue);
        int size = linkedListJ.size();
        for (int i17 = 0; i17 < size; i17++) {
            byte[] bArr2 = (byte[]) linkedListJ.get(i17);
            bg4 bg4Var = new bg4();
            if (bArr2 != null && bArr2.length > 0) {
                bg4Var.parseFrom(bArr2);
            }
            ft5Var.f325614e.add(bg4Var);
        }
        return 0;
    }

    @Override
    public Object toJSON() {
        JSONObject jSONObject = new JSONObject();
        try {
            Integer numValueOf = Integer.valueOf(this.f325613d);
            g21.e eVar = g21.f.f259069a;
            eVar.d(jSONObject, "Count", numValueOf, false);
            eVar.d(jSONObject, "List", this.f325614e, false);
        } catch (Exception unused) {
        }
        return jSONObject;
    }
}
