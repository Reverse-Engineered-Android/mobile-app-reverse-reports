package wy0;

import iz4.pf;
import iz4.qh;
import iz4.rh;
import iz4.sg;
import iz4.tg;

public class e0 implements com.tencent.mm.network.v0 {

    public final qh f445069a;

    public final rh f445070b;

    public int f445071c;

    public e0(boolean z16) {
        this.f445069a = new qh();
        this.f445070b = new rh();
    }

    @Override
    public int getEncryptAlgo() {
        return 0;
    }

    @Override
    public boolean getIsLongPolling() {
        return false;
    }

    @Override
    public boolean getIsUserCmd() {
        return false;
    }

    @Override
    public int getLongPollingTimeout() {
        return 0;
    }

    @Override
    public int getNewDNSBusinessType() {
        return 0;
    }

    @Override
    public int getNewExtFlags() {
        return 0;
    }

    @Override
    public int getOptions() {
        return 0;
    }

    @Override
    public String getReqHost() {
        return "";
    }

    @Override
    public sg getReqObj() {
        String strK = fo.w0.k();
        qh qhVar = this.f445069a;
        qhVar.setDeviceID(strK);
        int i16 = pf.f287378a;
        qhVar.setDeviceType(fo.q.f245245a);
        qhVar.setClientVersion(pf.f287384g);
        qhVar.setUin(this.f445071c);
        return qhVar;
    }

    @Override
    public tg getRespObj() {
        return this.f445070b;
    }

    @Override
    public int getTimeOut() {
        return 0;
    }

    @Override
    public byte[] getTransHeader() {
        return null;
    }

    @Override
    public int getType() {
        return 138;
    }

    @Override
    public String getUri() {
        return "/cgi-bin/micromsg-bin/newsync";
    }

    @Override
    public boolean isBindCellular() {
        return false;
    }

    @Override
    public boolean isSingleSession() {
        return true;
    }

    @Override
    public boolean keepAlive() {
        return false;
    }

    @Override
    public void setBindCellular(boolean z16) {
    }

    @Override
    public void setConnectionInfo(String str) {
    }

    @Override
    public void setEncryptAlgo(int i16) {
    }

    @Override
    public void setNewDNSBusinessType(int i16) {
    }

    @Override
    public void setReqHost(String str) {
    }

    @Override
    public void setSingleSession(boolean z16) {
    }

    public e0(rh rhVar) {
        this.f445069a = new qh();
        this.f445070b = rhVar;
    }
}
