package com.taobao.android.remoteobject.easy;

import android.text.TextUtils;
import com.taobao.idlefish.msg.protocol.RequestWrapper;
import com.taobao.idlefish.protocol.api.annotations.Api;
import com.taobao.idlefish.protocol.api.annotations.ApiConfig;
import com.taobao.idlefish.protocol.appinfo.PApplicationUtil;
import com.taobao.idlefish.protocol.env.PEnv;
import com.taobao.idlefish.protocol.net.ApiCallBack;
import com.taobao.idlefish.protocol.net.CacheConfig;
import com.taobao.idlefish.protocol.net.api.ApiCache;
import com.taobao.idlefish.protocol.net.api.BaseApiProtocol;
import com.taobao.idlefish.protocol.net.api.RequestConfig;
import com.taobao.idlefish.protocol.remoteconfig.PRemoteConfigs;
import com.taobao.idlefish.xframework.util.StringUtil;
import com.taobao.idlefish.xmc.XModuleCenter;
import defpackage.NCErrorCode$$ExternalSyntheticOutline0;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/* JADX INFO: loaded from: classes6.dex */
public class ApiBusiness implements IMtopBusiness {
    private static final Map<String, String> sMainInterList;
    private static final Map<String, String> sMsgInterList;
    private static final Map<String, String> sSearchInterList;
    public static boolean useSearch_V60;
    private String apiName;
    private CacheConfig cacheConf;
    private String cacheKey;
    protected ApiCallBack callback;
    private final AtomicBoolean callbacked = new AtomicBoolean(false);
    private transient int connTimeoutMilliSecond;
    private boolean isOriginJson;
    private boolean needLogin;
    private boolean needWua;
    protected Object parameter;
    private BaseApiProtocol request;
    private transient int socketTimeoutMilliSecond;
    private String version;

    private static class ApiNV implements Serializable {
        public String apiName;
        public String apiVersion;

        public ApiNV(String str, String str2) {
            this.apiName = str;
            this.apiVersion = str2;
        }
    }

    static {
        HashMap map = new HashMap();
        sSearchInterList = map;
        HashMap map2 = new HashMap();
        sMainInterList = map2;
        HashMap map3 = new HashMap();
        sMsgInterList = map3;
        map.put("mtop.taobao.idle.main.item.search/4.0", "mtop.taobao.idle.main.search.glue/1.0");
        map.put("mtop.taobao.idle.search.shade/1.0", "mtop.taobao.idlemtopsearch.search.shade/3.0");
        map.put("mtop.taobao.idle.search.suggest/1.0", "mtop.taobao.idlemtopsearch.search.suggest/2.0");
        map.put("mtop.taobao.idle.item.search.filter/3.0", "mtop.taobao.idlemtopsearch.item.search.filter/4.0");
        map.put("mtop.taobao.idle.item.search.activate/1.0", "mtop.taobao.idlemtopsearch.item.search.activate/2.0");
        map.put("com.taobao.idle.item.search.remind/2.0", "mtop.taobao.idlemtopsearch.item.search.remind/3.0");
        map.put("mtop.taobao.idle.fishpool.search/3.0", "mtop.taobao.idlemtopsearch.main.fishpool.search/4.0");
        map.put("mtop.taobao.idle.fishpool.itemsearch/5.0", "mtop.taobao.idlemtopsearch.fishpool.itemsearch/6.0");
        map2.put("mtop.taobao.idle.home.firstdata/5.0", "mtop.taobao.idlehome.home.firstdata/6.0");
        map2.put("mtop.taobao.idle.home.nextfresh/3.0", "mtop.taobao.idlehome.home.nextfresh/4.0");
        map3.put("mtop.idle.x.message.session.remove/1.0", "mtop.taobao.idlemessage.session.remove/1.0");
        map3.put("mtop.idle.x.message.session.get/1.0", "mtop.taobao.idlemessage.session.get/1.0");
        map3.put("mtop.idle.x.message.session.report/1.0", "mtop.taobao.idlemessage.session.report/1.0");
        map3.put("mtop.idle.x.message.session.create/1.0", "mtop.taobao.idlemessage.session.create/1.0");
        map3.put("mtop.idle.x.message.session.query/1.0", "mtop.taobao.idlemessage.session.query/1.0");
        map3.put("mtop.idle.x.message.session.sync/2.0", "mtop.taobao.idlemessage.session.sync/2.0");
        map3.put("mtop.idle.x.message.session.report.arrival/1.0", "mtop.taobao.idlemessage.session.report.arrival/1.0");
        map3.put("mtop.idle.x.message.session.report.read/1.0", "mtop.taobao.idlemessage.session.report.read/1.0");
        map3.put("mtop.idle.x.message.message.send/1.0", "mtop.taobao.idlemessage.message.send/1.0");
        map3.put("mtop.idle.x.message.message.sync/1.0", "mtop.taobao.idlemessage.message.sync/1.0");
        map3.put("mtop.idle.x.message.message.topn/1.0", "mtop.taobao.idlemessage.message.topn/1.0");
        map3.put("mtop.idle.x.message.message.del/1.0", "mtop.taobao.idlemessage.message.del/1.0");
        map3.put("mtop.idle.x.message.profile.notice.close/1.0", "mtop.taobao.idlemessage.profile.notice.close/1.0");
        map3.put("mtop.idle.x.message.profile.notice.open/1.0", "mtop.taobao.idlemessage.profile.notice.open/1.0");
        map3.put("mtop.idle.x.message.profile.notice.sync/1.0", "mtop.taobao.idlemessage.profile.notice.sync/1.0");
        map3.put("mtop.idle.x.message.region.sync/2.0", "mtop.taobao.idlemessage.region.sync/2.0");
        map3.put("mtop.idle.x.message.region.report/1.0", "mtop.taobao.idlemessage.region.report/1.0");
        map3.put("mtop.idle.x.message.p2p.data.send/1.0", "mtop.taobao.idlemessage.p2p.data.send/1.0");
    }

    public ApiBusiness(BaseApiProtocol baseApiProtocol, ApiCallBack apiCallBack) {
        this.connTimeoutMilliSecond = 0;
        this.socketTimeoutMilliSecond = 0;
        this.request = baseApiProtocol;
        if (baseApiProtocol != null) {
            this.isOriginJson = baseApiProtocol.isOriginJson();
            this.socketTimeoutMilliSecond = baseApiProtocol.getSocketTimeoutMilliSecond();
            this.connTimeoutMilliSecond = baseApiProtocol.getConnTimeoutMilliSecond();
        }
        this.callback = apiCallBack;
        setDefaultParameter(baseApiProtocol);
        parseParam(baseApiProtocol, apiCallBack);
    }

    private ApiNV changeKey(String str, Map<String, String> map, String str2) {
        if (((PRemoteConfigs) XModuleCenter.moduleForProtocol(PRemoteConfigs.class)).getValue("android_switch_high", str2, 0) != 1) {
            return null;
        }
        String[] strArrSplit = map.get(str).split("/");
        return new ApiNV(strArrSplit[0], strArrSplit[1]);
    }

    private void interceptApiAndVer(RequestConfig requestConfig) {
        ApiNV apiNVChangeKey;
        String str = requestConfig.apiName;
        String str2 = requestConfig.apiVersion;
        if (XModuleCenter.moduleReady(PRemoteConfigs.class)) {
            String strM$1 = NCErrorCode$$ExternalSyntheticOutline0.m$1(str, "/", str2);
            Map<String, String> map = sSearchInterList;
            if (map.containsKey(strM$1)) {
                map.put("mtop.taobao.idle.main.item.search/4.0", "mtop.taobao.idle.main.search.glue/1.0");
                apiNVChangeKey = changeKey(strM$1, map, "search_api_fl_switch");
            } else {
                Map<String, String> map2 = sMainInterList;
                if (map2.containsKey(strM$1)) {
                    apiNVChangeKey = changeKey(strM$1, map2, "main_api_fl_switch");
                } else {
                    Map<String, String> map3 = sMsgInterList;
                    apiNVChangeKey = map3.containsKey(strM$1) ? changeKey(strM$1, map3, "msg_api_fl_switch") : null;
                }
            }
            if (apiNVChangeKey == null || TextUtils.isEmpty(apiNVChangeKey.apiName) || TextUtils.isEmpty(apiNVChangeKey.apiVersion)) {
                return;
            }
            requestConfig.apiName = apiNVChangeKey.apiName;
            requestConfig.apiVersion = apiNVChangeKey.apiVersion;
        }
    }

    private void parseAnno(BaseApiProtocol baseApiProtocol) {
        RequestConfig requestConfig = baseApiProtocol.getRequestConfig();
        if (requestConfig == null) {
            ApiConfig apiConfig = (ApiConfig) baseApiProtocol.getClass().getAnnotation(ApiConfig.class);
            if (apiConfig == null) {
                throw new Error("ApiConfig 为空, 你可以使用ApiConfig进行标注!" + baseApiProtocol);
            }
            RequestConfig requestConfig2 = new RequestConfig();
            Api api = apiConfig.api();
            Api api2 = Api.empty_api;
            requestConfig2.apiName = api != api2 ? apiConfig.api().api : apiConfig.apiName();
            requestConfig2.apiVersion = apiConfig.api() != api2 ? apiConfig.api().version : apiConfig.apiVersion();
            if (TextUtils.isEmpty(requestConfig2.apiName) || TextUtils.isEmpty(requestConfig2.apiVersion)) {
                throw new RuntimeException("必须设置apiName 与 apiVersion!");
            }
            requestConfig2.needLogin = apiConfig.needLogin();
            requestConfig2.needWua = apiConfig.needWua();
            requestConfig2.needJsonReq = apiConfig.needJsonReq();
            requestConfig = requestConfig2;
        }
        interceptApiAndVer(requestConfig);
        this.apiName = requestConfig.apiName;
        this.version = requestConfig.apiVersion;
        this.needLogin = requestConfig.needLogin;
        this.needWua = requestConfig.needWua;
        if (requestConfig.needJsonReq) {
            baseApiProtocol.paramObj(new RequestWrapper(baseApiProtocol));
        }
        Object param = baseApiProtocol.getParam();
        BaseApiProtocol param2 = baseApiProtocol;
        if (param != null) {
            param2 = baseApiProtocol.getParam();
        }
        this.parameter = param2;
        if (param2 instanceof BaseApiProtocol) {
            Object objSeirCopy = seirCopy(param2);
            this.parameter = objSeirCopy;
            ((BaseApiProtocol) objSeirCopy).cacheConfig = null;
        }
    }

    private void parseCache(BaseApiProtocol baseApiProtocol) {
        CacheConfig cacheConfig = baseApiProtocol.cacheConfig;
        if (cacheConfig != null) {
            this.cacheConf = cacheConfig;
            return;
        }
        ApiCache apiCache = (ApiCache) baseApiProtocol.getClass().getAnnotation(ApiCache.class);
        CacheConfig cacheConfig2 = null;
        if (apiCache != null) {
            CacheConfig cacheConfig3 = new CacheConfig();
            if (!StringUtil.isEmptyOrNullStr(apiCache.apiTag())) {
                cacheConfig3.setApiTag(apiCache.apiTag());
            }
            if (apiCache.expiry() != -1) {
                cacheConfig3.setExpiry(apiCache.expiry() > 0 ? Long.valueOf(apiCache.expiry()) : null);
            }
            cacheConfig3.setMode(apiCache.mode());
            cacheConfig2 = cacheConfig3;
        }
        if (this.cacheConf == null) {
            this.cacheConf = cacheConfig2;
        }
    }

    private void parseParam(BaseApiProtocol baseApiProtocol, ApiCallBack apiCallBack) {
        parseAnno(baseApiProtocol);
        parseCache(baseApiProtocol);
    }

    private void setDefaultParameter(BaseApiProtocol baseApiProtocol) {
        try {
            if (((PApplicationUtil) XModuleCenter.moduleForProtocol(PApplicationUtil.class)).getFishApplicationInfo().getLat() == null || ((PApplicationUtil) XModuleCenter.moduleForProtocol(PApplicationUtil.class)).getFishApplicationInfo().getLon() == null || !TextUtils.isEmpty(baseApiProtocol.getGps())) {
                return;
            }
            baseApiProtocol.setGps(((PApplicationUtil) XModuleCenter.moduleForProtocol(PApplicationUtil.class)).getFishApplicationInfo().getLat() + "," + ((PApplicationUtil) XModuleCenter.moduleForProtocol(PApplicationUtil.class)).getFishApplicationInfo().getLon());
        } catch (Throwable unused) {
        }
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public String getApiName() {
        return this.apiName;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public String getApiVer() {
        return this.version;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public CacheConfig getCacheConfig() {
        return this.cacheConf;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public String getCacheKey() {
        return this.cacheKey;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public ApiCallBack getCallBack() {
        return this.callback;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public int getConnTimeoutMilliSecond() {
        return this.connTimeoutMilliSecond;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public Object getParam() {
        return this.parameter;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public BaseApiProtocol getRequest() {
        return this.request;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public int getSocketTimeoutMilliSecond() {
        return this.socketTimeoutMilliSecond;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public AtomicBoolean isCallBacked() {
        return this.callbacked;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public boolean isLogin() {
        return this.needLogin;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public boolean isOriginJson() {
        return this.isOriginJson;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public boolean isWua() {
        return this.needWua;
    }

    public <T> T seirCopy(T t) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            new ObjectOutputStream(byteArrayOutputStream).writeObject(t);
            return (T) new ObjectInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray())).readObject();
        } catch (Exception e) {
            if (((PEnv) XModuleCenter.moduleForProtocol(PEnv.class)).getDebug().booleanValue()) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public void setCacheConfig(CacheConfig cacheConfig) {
        this.cacheConf = cacheConfig;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public void setCacheKey(String str) {
        this.cacheKey = str;
    }

    public void setOriginJson(boolean z) {
        this.isOriginJson = z;
    }

    @Override // com.taobao.android.remoteobject.easy.IMtopBusiness
    public void setParam(Object obj) {
        this.parameter = obj;
    }
}
