package com.alibaba.security.ccrc.service.enums;

import com.alibaba.security.ccrc.common.keep.WKeep;
import com.taobao.android.dinamicx.DXError;

/* JADX INFO: loaded from: classes2.dex */
@WKeep
public enum WukongResultCode {
    ACTIVATE_SUCCESS(100000),
    ACTIVATE_ING(1000001),
    ACTIVATED(1000002),
    UN_ACTIVATE(1000003),
    ACTIVATE_FAIL(1000004),
    DETECT_HIT_ACTION(200000),
    DETECT_NO_HIT(DXError.DX_ERROR_CODE_SLIDER_LAYOUT_RECYCLER_VIEW_ERROR),
    DETECT_HIT_NO_ACTION(DXError.DX_ERROR_CODE_SLIDER_LAYOUT_RECYCLER_VIEW_NULL),
    DETECT_PRE_FAIL(DXError.DX_ERROR_CODE_SLIDER_LAYOUT_RENDER_SCROLL_TO_FAILED),
    DETECT_ENGINE_EVALUATE_FAIL(DXError.DX_ERROR_CODE_SLIDER_LAYOUT_IDLE_SCROLL_TO_FAILED);

    private final int code;

    WukongResultCode(int s) {
        this.code = s;
    }

    public int getCode() {
        return this.code;
    }
}
