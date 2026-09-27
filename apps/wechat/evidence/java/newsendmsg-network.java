package wy0;

import com.tencent.mars.xlog.Log;
import com.tencent.mm.autogen.events.SendMsgFailEvent;
import com.tencent.mm.autogen.events.SendMsgSuccessEvent;
import com.tencent.mm.plugin.messenger.foundation.PluginMessengerFoundation;
import com.tencent.mm.sdk.platformtools.s8;
import com.tencent.mm.storage.y8;
import com.tencent.wcdb.database.SQLiteException;
import dx0.fd;
import dx0.ha;
import dx0.v8;
import dx0.v9;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lz4.bg4;
import lz4.cg4;
import lz4.do5;
import lz4.ft5;
import lz4.gt5;
import qb3.c4;
import qb3.f4;
import qb3.k4;
import qb3.r4;
import qb3.z3;

public class r0 extends com.tencent.mm.modelbase.m1 implements com.tencent.mm.network.l0 {

    public static final List f445183r = new ArrayList();

    public com.tencent.mm.modelbase.u0 f445184d;

    public com.tencent.mm.modelbase.o f445185e;

    public final long f445186f;

    public final String f445187g;

    public int f445188h;

    public final List f445189i;

    public int f445190m;

    public boolean f445191n;

    public final List f445192o;

    public y8 f445193p;

    public f4 f445194q;

    public r0(String str, String str2, int i16, int i17, Object obj) {
        String str3;
        boolean z16;
        this.f445188h = 15;
        this.f445189i = new LinkedList();
        this.f445190m = 3;
        this.f445191n = false;
        this.f445192o = new ArrayList();
        this.f445193p = null;
        this.f445194q = null;
        if (Log.getLogLevel() <= 1) {
            boolean z17 = s8.f192981a;
        }
        if (s8.M0(str)) {
            return;
        }
        y8 y8Var = new y8();
        y8Var.F1(1);
        y8Var.G1(str);
        y8Var.k1(v9.o(str));
        y8Var.o1(1);
        y8Var.i1(str2);
        y8Var.setType(i16);
        String strBg = ((i1) ((r4) x35.n0.c(r4.class))).Bg(y8Var);
        Object[] objArr = new Object[3];
        objArr[0] = strBg;
        objArr[1] = Boolean.valueOf(obj == null);
        objArr[2] = Integer.valueOf(i17);
        Log.i("MicroMsg.NetSceneSendMsg", "[mergeMsgSource] rawSource:%s args is null:%s flag:%s", objArr);
        if (!s8.M0(strBg) && !strBg.startsWith("<msgsource>")) {
            Log.w("MicroMsg.NetSceneSendMsg", "[mergeMsgSource] the msgsource is right? %s", strBg);
        } else if ((i17 & 1) != 0 && (obj instanceof HashMap)) {
            StringBuffer stringBuffer = new StringBuffer();
            if (s8.M0(strBg)) {
                stringBuffer.append("<msgsource>");
            }
            for (Map.Entry entry : ((HashMap) obj).entrySet()) {
                String str4 = (String) entry.getValue();
                String str5 = (String) entry.getKey();
                if (s8.M0(str4) || s8.M0(str5)) {
                    Log.w("MicroMsg.NetSceneSendMsg", "%s %s", str5, str4);
                } else if (!"similar_paste_seq".equalsIgnoreCase(str5)) {
                    stringBuffer.append("<");
                    stringBuffer.append(str5);
                    stringBuffer.append(">");
                    stringBuffer.append(str4);
                    stringBuffer.append("</");
                    stringBuffer.append(str5);
                    stringBuffer.append(">");
                }
            }
            if (s8.M0(strBg)) {
                stringBuffer.append("</msgsource>");
                strBg = stringBuffer.toString();
            } else {
                strBg = strBg.replace("<msgsource>", "<msgsource>" + stringBuffer.toString());
            }
        }
        if (!s8.M0(strBg)) {
            y8Var.u3(strBg);
            Log.i("MicroMsg.NetSceneSendMsg", "NetSceneSendMsg:MsgSource:%s", y8Var.G);
        }
        int i18 = i17 & 4;
        if (i18 == 0 && (i17 & 8) == 0) {
            int i19 = i17 & 16;
            if (i19 != 0 || (i17 & 32) != 0) {
                int i26 = i19 != 0 ? 4 : 5;
                Log.i("MicroMsg.NetSceneSendMsg", "has paste similar change flag, %d", Integer.valueOf(i26));
                HashMap map = new HashMap();
                map.put(".msgsource.alnode.cf", String.valueOf(i26));
                if (obj instanceof HashMap) {
                    map.put(".msgsource.alnode.inlenlist", (String) ((HashMap) obj).get("similar_paste_seq"));
                }
                ha.O(y8Var, ha.G(map), false);
            }
        } else {
            int i27 = i18 != 0 ? 2 : 3;
            Log.i("MicroMsg.NetSceneSendMsg", "has paste fully flag, %d", Integer.valueOf(i27));
            HashMap map2 = new HashMap();
            map2.put(".msgsource.alnode.cf", String.valueOf(i27));
            if (obj instanceof HashMap) {
                map2.put(".msgsource.alnode.inlenlist", (String) ((HashMap) obj).get("similar_paste_seq"));
            }
            ha.O(y8Var, ha.G(map2), false);
        }
        try {
            this.f445186f = ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().Ba(y8Var, true);
        } catch (SQLiteException e16) {
            zr3.f.INSTANCE.idkeyStat(111L, 255L, 1L, false);
            if (!e16.toString().contains("UNIQUE constraint failed")) {
                throw e16;
            }
            Log.e("MicroMsg.NetSceneSendMsg", "fallback to insert");
            ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().Zb();
            this.f445186f = ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().Ba(y8Var, true);
        } catch (IllegalStateException e17) {
            Log.printErrStackTrace("MicroMsg.NetSceneSendMsg", e17, "", new Object[0]);
        }
        if (this.f445186f == -1) {
            zr3.f.INSTANCE.idkeyStat(111L, 255L, 1L, false);
        } else if (((x13.e) ((z3) x35.n0.c(z3.class))).Bg(str)) {
            z3 z3Var = (z3) x35.n0.c(z3.class);
            String strR = dx0.z1.r();
            long j16 = this.f445186f;
            String strR0 = y8Var.R0();
            x13.e eVar = (x13.e) z3Var;
            eVar.getClass();
            eVar.Hg(str, strR, str2, j16, fd.e(), strR0);
        }
        if (this.f445186f != -1) {
            z16 = true;
            str3 = null;
        } else {
            str3 = null;
            z16 = false;
        }
        pq5.a.g(str3, z16);
        Log.i("MicroMsg.NetSceneSendMsg", "new msg inserted to db , local id = " + this.f445186f);
    }

    public final void I(String str) {
        int iDoScene = doScene(dispatcher(), this.f445184d);
        if (iDoScene == -2) {
            this.f445184d.onSceneEnd(0, 0, str, this);
        } else if (iDoScene < 0) {
            this.f445184d.onSceneEnd(3, -1, str, this);
        }
    }

    public final void J(int i16) {
        y8 y8Var = (y8) this.f445189i.get(i16);
        Log.w("MicroMsg.NetSceneSendMsg", "markMsgFailed for id:%d", Long.valueOf(y8Var.getMsgId()));
        K(y8Var);
    }

    public final void K(y8 y8Var) {
        y8Var.F1(5);
        zr3.f.INSTANCE.idkeyStat(111L, 30L, 1L, true);
        ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().zb(y8Var.getMsgId(), y8Var, true);
        for (v8 v8Var : (ArrayList) f445183r) {
            y8Var.j();
            v8Var.getClass();
        }
    }

    public final void L(int i16) {
        List list = this.f445189i;
        if (list == null) {
            Log.e("MicroMsg.NetSceneSendMsg", "publishMsgSendFailEvent, sendingList is null");
            return;
        }
        LinkedList linkedList = (LinkedList) list;
        if (i16 >= linkedList.size() || i16 < 0) {
            Log.e("MicroMsg.NetSceneSendMsg", "publishMsgSendFailEvent, index:%d, sendingList.size:%d", Integer.valueOf(i16), Integer.valueOf(linkedList.size()));
            return;
        }
        y8 y8Var = (y8) linkedList.get(i16);
        SendMsgFailEvent sendMsgFailEvent = new SendMsgFailEvent();
        sendMsgFailEvent.f57134g.f304661a = y8Var;
        sendMsgFailEvent.e();
        y8Var.getMsgId();
    }

    @Override
    public int doScene(com.tencent.mm.network.s sVar, com.tencent.mm.modelbase.u0 u0Var) {
        List listG7;
        this.f445184d = u0Var;
        com.tencent.mm.modelbase.l lVar = new com.tencent.mm.modelbase.l();
        lVar.f72699a = new ft5();
        lVar.f72700b = new gt5();
        lVar.f72701c = "/cgi-bin/micromsg-bin/newsendmsg";
        lVar.f72702d = 522;
        lVar.f72703e = 237;
        lVar.f72704f = 1000000237;
        com.tencent.mm.modelbase.o oVarA = lVar.a();
        this.f445185e = oVarA;
        ft5 ft5Var = (ft5) oVarA.f72750a.f72723a;
        y8 y8Var = this.f445193p;
        if (y8Var == null) {
            listG7 = ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().G7(this.f445188h);
        } else {
            if (y8Var.Q0() != 5) {
                Log.w("MicroMsg.NetSceneSendMsg", "msg:%d status:%d should not be resend !", Long.valueOf(this.f445193p.getMsgId()), Integer.valueOf(this.f445193p.Q0()));
            }
            this.f445193p.F1(1);
            ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().yb(this.f445186f, this.f445193p);
            ArrayList arrayList = new ArrayList();
            arrayList.add(this.f445193p);
            this.f445193p = null;
            listG7 = arrayList;
        }
        if (listG7.size() == 0) {
            Log.w("MicroMsg.NetSceneSendMsg", "no sending message");
            return -2;
        }
        List list = this.f445189i;
        LinkedList linkedList = (LinkedList) list;
        linkedList.clear();
        for (int i16 = 0; i16 < listG7.size(); i16++) {
            y8 y8Var2 = (y8) listG7.get(i16);
            if (y8Var2.C0() == 1) {
                bg4 bg4Var = new bg4();
                do5 do5Var = new do5();
                do5Var.f323705d = y8Var2.R0();
                do5Var.f323706e = true;
                bg4Var.f321747d = do5Var;
                bg4Var.f321750g = (int) (y8Var2.getCreateTime() / 1000);
                bg4Var.f321749f = y8Var2.getType();
                bg4Var.f321748e = y8Var2.j();
                bg4Var.f321751h = dx0.y1.a(dx0.z1.r(), y8Var2.getCreateTime()).hashCode();
                if (((PluginMessengerFoundation) ((k4) x35.n0.c(k4.class))).Hg(y8Var2.R0())) {
                    bg4Var.f321753m = ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).Hg().Y(y8Var2.R0());
                }
                if (this.f445194q == null) {
                    this.f445194q = ((i1) ((r4) x35.n0.c(r4.class))).f445116d;
                }
                Log.i("MicroMsg.NetSceneSendMsg", "using message source assembler %s", this.f445194q);
                this.f445194q.e(bg4Var, y8Var2);
                Log.i("MicroMsg.NetSceneSendMsg", "reqCmd.MsgSource:%s", bg4Var.f321752i);
                ft5Var.f325614e.add(bg4Var);
                ft5Var.f325613d = ft5Var.f325614e.size();
                linkedList.add(y8Var2);
                Log.i("MicroMsg.NetSceneSendMsg", "Req MsgSource %s", bg4Var.f321752i);
            }
        }
        int iDispatch = dispatch(sVar, this.f445185e, this);
        if (iDispatch < 0) {
            Log.i("MicroMsg.NetSceneSendMsg", "mark all failed. do scene %d", Integer.valueOf(iDispatch));
            for (int i17 = 0; i17 < ((LinkedList) list).size(); i17++) {
                J(i17);
            }
        }
        return iDispatch;
    }

    @Override
    public int getType() {
        return 522;
    }

    @Override
    public void onGYNetEnd(int i16, int i17, int i18, String str, com.tencent.mm.network.v0 v0Var, byte[] bArr) {
        boolean z16;
        int i19;
        List list = this.f445189i;
        if (i17 != 0 || i18 != 0) {
            Log.i("MicroMsg.NetSceneSendMsg", "mark all failed. onGYNetEnd. errType:%d errCode:%d", Integer.valueOf(i17), Integer.valueOf(i18));
            zr3.f fVar = zr3.f.INSTANCE;
            fVar.idkeyStat(111L, i17 + 40, 1L, true);
            fVar.idkeyStat(111L, 253L, 1L, false);
            if (i18 == 413) {
                int iComputeSize = this.f445185e.f72750a.f72723a.computeSize();
                int i26 = this.f445188h / 2;
                Log.e("MicroMsg.NetSceneSendMsg", "send msgs req exceed max limit, size %s, sendMsgMaxNum %s, newSendMsgMaxNum %s", Integer.valueOf(iComputeSize), Integer.valueOf(this.f445188h), Integer.valueOf(i26));
                if (this.f445188h != 1) {
                    this.f445188h = i26;
                    I(str);
                    return;
                }
                Log.e("MicroMsg.NetSceneSendMsg", "one msg exceed?????");
                for (int i27 = 0; i27 < ((LinkedList) list).size(); i27++) {
                    J(i27);
                }
                this.f445184d.onSceneEnd(i17, i18, str, this);
                for (int i28 = 0; i28 < ((LinkedList) list).size(); i28++) {
                    L(i28);
                }
                return;
            }
            if (i17 == 3 || i17 == 9 || i17 == 7 || i17 == 8 || i17 == 1) {
                this.f445184d.onSceneEnd(i17, i18, str, this);
                Log.e("MicroMsg.NetSceneSendMsg", "Message delivery failed due to network reasons.");
                return;
            }
            for (int i29 = 0; i29 < ((LinkedList) list).size(); i29++) {
                J(i29);
            }
            this.f445184d.onSceneEnd(i17, i18, str, this);
            for (int i36 = 0; i36 < ((LinkedList) list).size(); i36++) {
                L(i36);
            }
            Log.i("MicroMsg.NetSceneSendMsg", "send fail, continue send SENDING msg");
            I(str);
            return;
        }
        gt5 gt5Var = (gt5) this.f445185e.f72751b.f72738a;
        LinkedList linkedList = gt5Var.f326424e;
        ((PluginMessengerFoundation) ((k4) x35.n0.c(k4.class))).Kg(gt5Var.f326425f);
        ArrayList arrayList = new ArrayList();
        LinkedList linkedList2 = (LinkedList) list;
        if (linkedList2.size() == linkedList.size()) {
            int i37 = 0;
            while (true) {
                int size = linkedList.size();
                List list2 = this.f445192o;
                if (i37 >= size) {
                    ArrayList arrayList2 = (ArrayList) list2;
                    Log.i("MicroMsg.NetSceneSendMsg", "summerdktext total  [%d]msgs sent successfully, [%d]msgs need verifypsw", Integer.valueOf(i37 - arrayList2.size()), Integer.valueOf(arrayList2.size()));
                    break;
                }
                cg4 cg4Var = (cg4) linkedList.get(i37);
                if (cg4Var.f322695d != 0 || q21.o1.K) {
                    zr3.f.INSTANCE.idkeyStat(111L, 252L, 1L, false);
                    int i38 = cg4Var.f322695d;
                    if (i38 == -49 || q21.o1.K) {
                        Log.i("MicroMsg.NetSceneSendMsg", "summerdktext send msg failed: item ret code[%d], index[%d], testVerifyPsw[%b], retryVerifyCount[%d]", Integer.valueOf(i38), Integer.valueOf(i37), Boolean.valueOf(q21.o1.K), Integer.valueOf(this.f445190m));
                        if (this.f445191n) {
                            ((ArrayList) list2).add((y8) linkedList2.get(i37));
                        } else {
                            int i39 = this.f445190m;
                            if (i39 < 0) {
                                J(i37);
                                this.f445184d.onSceneEnd(4, cg4Var.f322695d, str, this);
                                L(i37);
                                return;
                            } else {
                                this.f445191n = true;
                                this.f445190m = i39 - 1;
                                ((ArrayList) list2).add((y8) linkedList2.get(i37));
                                nj0.k1.e().j(new q0(this, str));
                            }
                        }
                    } else {
                        Log.i("MicroMsg.NetSceneSendMsg", "send msg fail ret = %s MsgId=%s MsgSource=%s", Integer.valueOf(i38), Integer.valueOf(cg4Var.f322697f), cg4Var.f322703o);
                        J(i37);
                        this.f445184d.onSceneEnd(4, cg4Var.f322695d, str, this);
                        L(i37);
                    }
                } else {
                    long msgId = ((y8) linkedList2.get(i37)).getMsgId();
                    String strR0 = ((y8) linkedList2.get(i37)).R0();
                    Log.i("MicroMsg.NetSceneSendMsg", "msg local id = " + msgId + ", SvrId = " + cg4Var.f322702n + " sent successfully!");
                    y8 y8VarQh = vq0.e0.Qh(strR0, msgId);
                    y8VarQh.x1(cg4Var.f322702n);
                    ha.Q(y8VarQh, cg4Var.f322703o, false);
                    if (10007 == q21.o1.f385824q && (i19 = q21.o1.f385825r) != 0) {
                        y8VarQh.x1(i19);
                        q21.o1.f385825r = 0;
                    }
                    if (((y8VarQh.F & 512) > 0) && y8VarQh.getType() == 42) {
                        ((wb0.e) ((rb3.l0) x35.n0.c(rb3.l0.class))).getClass();
                        z16 = true;
                        com.tencent.mm.modelsimple.g1.M(y8VarQh, 21, 1);
                    } else {
                        z16 = true;
                    }
                    y8VarQh.F1(2);
                    ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().zb(msgId, y8VarQh, z16);
                    if (i37 >= linkedList2.size() || i37 < 0) {
                        Log.e("MicroMsg.NetSceneSendMsg", "publishMsgSendSuccessEvent, index:%d, sendingList.size:%d", Integer.valueOf(i37), Integer.valueOf(linkedList2.size()));
                    } else {
                        y8 y8VarQh2 = vq0.e0.Qh(((y8) linkedList2.get(i37)).R0(), ((y8) linkedList2.get(i37)).getMsgId());
                        SendMsgSuccessEvent sendMsgSuccessEvent = new SendMsgSuccessEvent();
                        sendMsgSuccessEvent.f57136g.f304869a = y8VarQh2;
                        sendMsgSuccessEvent.e();
                    }
                    arrayList.add(Integer.valueOf(i37));
                    if (1 == cg4Var.f322701m) {
                        zr3.f fVar2 = zr3.f.INSTANCE;
                        fVar2.q(11942, true, false, Long.valueOf(cg4Var.f322702n));
                        fVar2.q(11945, false, true, Long.valueOf(cg4Var.f322702n));
                        fVar2.q(11946, false, false, Long.valueOf(cg4Var.f322702n));
                        fVar2.idkeyStat(90L, 0L, 1L, false);
                        fVar2.idkeyStat(90L, 1L, 1L, true);
                    }
                }
                i37++;
            }
        }
        Log.i("MicroMsg.NetSceneSendMsg", "summerdktext send finish, continue send SENDING msg verifyingPsw[%b]", Boolean.valueOf(this.f445191n));
        if (this.f445191n) {
            this.f445184d.onSceneEnd(i17, i18, str, this);
        } else {
            I(str);
        }
    }

    @Override
    public int securityLimitCount() {
        return 10;
    }

    @Override
    public boolean securityLimitCountReach() {
        boolean zSecurityLimitCountReach = super.securityLimitCountReach();
        if (zSecurityLimitCountReach) {
            zr3.f.INSTANCE.idkeyStat(111L, 254L, 1L, false);
        }
        return zSecurityLimitCountReach;
    }

    @Override
    public com.tencent.mm.modelbase.o1 securityVerificationChecked(com.tencent.mm.network.v0 v0Var) {
        return this.f445189i.size() > 0 ? com.tencent.mm.modelbase.o1.EOk : com.tencent.mm.modelbase.o1.EFailed;
    }

    @Override
    public boolean uniqueInNetsceneQueue() {
        return true;
    }

    public r0(String str, String str2, int i16, int i17, long j16) {
        String str3;
        this.f445188h = 15;
        this.f445189i = new LinkedList();
        this.f445190m = 3;
        this.f445191n = false;
        this.f445192o = new ArrayList();
        this.f445193p = null;
        this.f445194q = null;
        if (Log.getLogLevel() <= 1) {
            boolean z16 = s8.f192981a;
        }
        if (s8.M0(str)) {
            return;
        }
        y8 y8Var = new y8();
        y8Var.F1(1);
        y8Var.G1(str);
        y8Var.k1(v9.o(str));
        y8Var.o1(1);
        y8Var.i1(str2);
        y8Var.setType(i16);
        if (i17 == 1 && i16 == 42) {
            y8Var.t3();
        }
        String strBg = ((i1) ((r4) x35.n0.c(r4.class))).Bg(y8Var);
        if (!s8.M0(strBg)) {
            y8Var.u3(strBg);
            Log.i("MicroMsg.NetSceneSendMsg", "NetSceneSendMsg:MsgSource:%s", y8Var.G);
        }
        try {
            this.f445186f = ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().Ba(y8Var, true);
            this.f445187g = str;
        } catch (SQLiteException e16) {
            zr3.f.INSTANCE.idkeyStat(111L, 255L, 1L, false);
            if (e16.toString().contains("UNIQUE constraint failed")) {
                Log.e("MicroMsg.NetSceneSendMsg", "fallback to insert");
                ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().Zb();
                this.f445186f = ((com.tencent.mm.plugin.messenger.foundation.e2) ((c4) nj0.k1.s(c4.class))).mh().Ba(y8Var, true);
            } else {
                throw e16;
            }
        } catch (IllegalStateException e17) {
            Log.printErrStackTrace("MicroMsg.NetSceneSendMsg", e17, "", new Object[0]);
        }
        if (this.f445186f == -1 || !((x13.e) ((z3) x35.n0.c(z3.class))).Bg(str)) {
            str3 = "MicroMsg.NetSceneSendMsg";
        } else {
            z3 z3Var = (z3) x35.n0.c(z3.class);
            String strR = dx0.z1.r();
            long j17 = this.f445186f;
            String strR0 = y8Var.R0();
            x13.e eVar = (x13.e) z3Var;
            eVar.getClass();
            str3 = "MicroMsg.NetSceneSendMsg";
            eVar.Hg(str, strR, str2, j17, fd.e(), strR0);
        }
        pq5.a.g(null, this.f445186f != -1);
        Log.i(str3, "new msg inserted to db , local id = " + this.f445186f);
    }

    public r0(long j16, int i16, String str) {
        this.f445188h = 15;
        this.f445189i = new LinkedList();
        this.f445190m = 3;
        this.f445191n = false;
        this.f445192o = new ArrayList();
        this.f445193p = null;
        this.f445194q = null;
        Log.i("MicroMsg.NetSceneSendMsg", "resend msg , local id = " + j16);
        this.f445186f = j16;
        this.f445187g = str;
        this.f445193p = vq0.e0.Qh(str, j16);
    }

    public r0() {
        this.f445188h = 15;
        this.f445189i = new LinkedList();
        this.f445190m = 3;
        this.f445191n = false;
        this.f445192o = new ArrayList();
        this.f445193p = null;
        this.f445194q = null;
        boolean z16 = s8.f192981a;
        Log.i("MicroMsg.NetSceneSendMsg", "empty msg sender created");
    }
}
