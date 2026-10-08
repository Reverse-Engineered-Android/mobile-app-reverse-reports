package com.alibaba.security.wukong.bx;

import android.text.TextUtils;
import com.alibaba.security.ccrc.common.keep.WKeep;
import com.alibaba.security.ccrc.manager.CcrcContextImpl;
import com.alibaba.security.ccrc.service.build.n2;
import com.alibaba.security.ccrc.service.build.p2;
import com.alibaba.security.ccrc.service.build.s2;
import com.alibaba.security.ccrc.service.build.t2;
import com.alibaba.security.ccrc.service.build.x3;
import com.alibaba.security.ccrc.service.interfaces.AbsWuKongDetectListener;
import com.alibaba.security.ccrc.service.interfaces.OnWuKongActivateListener;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: classes2.dex */
@WKeep
public class CcrcBHService {
    private static final int MFE_BX_COMPARE = 2;
    private static final int MFE_ONLY = 1;
    private static final Map<String, CcrcBHService> SERVICE_MAP = new HashMap();
    private final n2 mBHServiceImpl;

    private CcrcBHService(String ccrcCode) {
        int iB = x3.c().b(ccrcCode);
        if (iB == 2) {
            this.mBHServiceImpl = new p2(ccrcCode);
        } else if (iB == 1) {
            this.mBHServiceImpl = new t2(ccrcCode);
        } else {
            this.mBHServiceImpl = new s2(ccrcCode);
        }
    }

    public static synchronized CcrcBHService getBHService(String ccrcCode) {
        if (TextUtils.isEmpty(ccrcCode)) {
            return null;
        }
        if (CcrcContextImpl.getContext() == null) {
            return null;
        }
        Map<String, CcrcBHService> map = SERVICE_MAP;
        if (map.containsKey(ccrcCode) && map.get(ccrcCode) != null) {
            return map.get(ccrcCode);
        }
        CcrcBHService ccrcBHService = new CcrcBHService(ccrcCode);
        map.put(ccrcCode, ccrcBHService);
        return ccrcBHService;
    }

    public void activate() {
        activate(null);
    }

    public void deActivate() {
        this.mBHServiceImpl.b();
    }

    public void detect(Map<String, String> extras) {
        this.mBHServiceImpl.a(extras);
    }

    public void setOnDetectListener(AbsWuKongDetectListener onDetectListener) {
        n2 n2Var = this.mBHServiceImpl;
        if (n2Var == null) {
            return;
        }
        n2Var.b(onDetectListener);
    }

    public void activate(final OnWuKongActivateListener listener) {
        this.mBHServiceImpl.a(listener);
    }
}
