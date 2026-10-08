package mtopsdk.mtop.upload.service;

import android.content.Context;
import android.taobao.windvane.util.ImageTool$$ExternalSyntheticOutline0;
import android.util.SparseArray;
import anetwork.channel.Request;
import anetwork.channel.Response;
import anetwork.channel.degrade.DegradableNetwork;
import anetwork.channel.entity.BasicHeader;
import anetwork.channel.entity.RequestImpl;
import anetwork.channel.entity.StringParam;
import com.alimm.xadsdk.request.builder.IRequestConst;
import com.meizu.cloud.pushsdk.handler.a.a.a$$ExternalSyntheticOutline0;
import defpackage.NCErrorCode$$ExternalSyntheticOutline0;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mtopsdk.common.util.HeaderHandlerUtil;
import mtopsdk.common.util.MtopUtils;
import mtopsdk.common.util.RemoteConfig;
import mtopsdk.common.util.StringUtils;
import mtopsdk.common.util.TBSdkLog;
import mtopsdk.mtop.domain.MethodEnum;
import mtopsdk.mtop.global.MtopConfig;
import mtopsdk.mtop.intf.Mtop;
import mtopsdk.mtop.upload.domain.FileBaseInfo;
import mtopsdk.mtop.upload.domain.UploadConstants;
import mtopsdk.mtop.upload.domain.UploadResult;
import mtopsdk.mtop.upload.domain.UploadToken;
import mtopsdk.mtop.upload.util.FileUploadSetting;
import mtopsdk.mtop.util.ErrorConstant;
import mtopsdk.mtop.util.Result;
import mtopsdk.security.ISign;
import mtopsdk.xstate.XState;

/* JADX INFO: loaded from: classes9.dex */
public class UploadFileServiceImpl implements UploadFileService {
    private static volatile long timestampOffset;
    private DegradableNetwork networkImpl;
    private ISign signGenerator;

    enum TokenParamsEnum {
        VERSION("version"),
        BIZ_CODE(UploadConstants.BIZ_CODE),
        APPKEY("appkey"),
        TIMESTAMP("t"),
        UTDID("utdid"),
        USERID("userid"),
        FILE_ID("fileid"),
        FILE_NAME("filename"),
        FILE_SIZE("filesize"),
        SEGMENT_SIZE(UploadConstants.SEGMENT_SIZE);

        private String key;

        TokenParamsEnum(String str) {
            this.key = str;
        }

        public String getKey() {
            return this.key;
        }
    }

    public UploadFileServiceImpl() {
        this.networkImpl = null;
        this.signGenerator = null;
        MtopConfig mtopConfig = Mtop.instance(Mtop.Id.INNER, (Context) null).getMtopConfig();
        this.networkImpl = new DegradableNetwork(mtopConfig.context);
        this.signGenerator = mtopConfig.sign;
    }

    @Override // mtopsdk.mtop.upload.service.UploadFileService
    public final Result<UploadResult> fileUpload(UploadToken uploadToken, long j, int i) {
        String strM;
        ArrayList arrayList;
        ArrayList arrayList2;
        String str;
        String str2;
        Result<UploadResult> result;
        if (uploadToken != null) {
            FileBaseInfo fileBaseInfo = uploadToken.fileBaseInfo;
            if ((fileBaseInfo == null || fileBaseInfo.fileSize <= 0 || uploadToken.segmentSize <= 0 || StringUtils.isBlank(uploadToken.token) || StringUtils.isBlank(uploadToken.domain)) ? false : true) {
                if (StringUtils.isBlank(uploadToken.domain)) {
                    strM = null;
                } else {
                    StringBuilder sb = new StringBuilder(32);
                    String str3 = uploadToken.bizCode;
                    SparseArray<String> sparseArray = FileUploadSetting.uploadDomainMap;
                    if (RemoteConfig.getInstance().useHttpsBizcodeSets.contains(str3)) {
                        sb.append(IRequestConst.HTTPS);
                    } else {
                        sb.append("http://");
                    }
                    strM = NCErrorCode$$ExternalSyntheticOutline0.m(sb, uploadToken.domain, "/uploadv2.do");
                }
                try {
                    Request requestImpl = new RequestImpl(strM);
                    requestImpl.setBizId(4096);
                    requestImpl.setCookieEnabled(false);
                    requestImpl.setReadTimeout(40000);
                    requestImpl.setRetryTime(1);
                    requestImpl.setMethod(MethodEnum.POST.getMethod());
                    FileBaseInfo fileBaseInfo2 = uploadToken.fileBaseInfo;
                    long j2 = fileBaseInfo2.fileSize - j;
                    if (j2 < 0) {
                        return new Result<>(false, UploadConstants.ERRTYPE_OTHER_UPLOAD_ERROR, UploadConstants.ERRCODE_FILE_UPLOAD_FAIL, UploadConstants.ERRMSG_FILE_UPLOAD_FAIL);
                    }
                    long j3 = uploadToken.segmentSize;
                    long j4 = j2 > j3 ? j3 : j2;
                    HashMap mapM29m = ImageTool$$ExternalSyntheticOutline0.m29m("Content-Type", "application/octet-stream");
                    mapM29m.put("Content-Length", String.valueOf(j4));
                    String value = XState.getValue(null, "ua");
                    if (value != null) {
                        mapM29m.put("user-agent", value);
                    }
                    if (mapM29m.size() < 1) {
                        arrayList = null;
                    } else {
                        arrayList = new ArrayList();
                        for (Map.Entry entry : mapM29m.entrySet()) {
                            if (entry != null && StringUtils.isNotBlank((String) entry.getKey())) {
                                arrayList.add(new BasicHeader((String) entry.getKey(), (String) entry.getValue()));
                            }
                        }
                    }
                    requestImpl.setHeaders(arrayList);
                    HashMap map = new HashMap();
                    if (i > 0) {
                        map.put(UploadConstants.RETRY_TIMES, String.valueOf(i));
                    }
                    map.put("token", uploadToken.token);
                    map.put("offset", String.valueOf(j));
                    map.putAll(uploadToken.tokenParams);
                    if (map.size() < 1) {
                        arrayList2 = null;
                    } else {
                        arrayList2 = new ArrayList();
                        for (Map.Entry entry2 : map.entrySet()) {
                            arrayList2.add(new StringParam((String) entry2.getKey(), (String) entry2.getValue()));
                        }
                    }
                    requestImpl.setParams(arrayList2);
                    File file = fileBaseInfo2.file;
                    if (file != null) {
                        str = UploadConstants.ERRMSG_FILE_UPLOAD_FAIL;
                        str2 = UploadConstants.ERRCODE_FILE_UPLOAD_FAIL;
                        requestImpl.setBodyHandler(new FileUploadBodyHandlerImpl(file, j, j4));
                    } else {
                        str = UploadConstants.ERRMSG_FILE_UPLOAD_FAIL;
                        str2 = UploadConstants.ERRCODE_FILE_UPLOAD_FAIL;
                        requestImpl.setBodyHandler(new FileStreamUploadBodyHandlerImpl(null, fileBaseInfo2.fileSize, j, j4));
                    }
                    Response responseSyncSend = this.networkImpl.syncSend(requestImpl, null);
                    int statusCode = responseSyncSend.getStatusCode();
                    Map<String, List<String>> connHeadFields = responseSyncSend.getConnHeadFields();
                    if (statusCode < 0) {
                        result = -200 == statusCode ? new Result<>(false, UploadConstants.ERRTYPE_NETWORK_ERROR, ErrorConstant.ERRCODE_NO_NETWORK, "无网络") : new Result<>(false, UploadConstants.ERRTYPE_NETWORK_ERROR, "ANDROID_SYS_NETWORK_ERROR", anet.channel.util.ErrorConstant.getErrMsg(statusCode));
                        result.setStatusCode(statusCode);
                    } else {
                        if (200 == statusCode) {
                            String singleHeaderFieldByKey = HeaderHandlerUtil.getSingleHeaderFieldByKey(connHeadFields, UploadConstants.X_ERROR_CODE);
                            if (StringUtils.isBlank(singleHeaderFieldByKey)) {
                                singleHeaderFieldByKey = str2;
                            }
                            if ("SUCCESS".equalsIgnoreCase(singleHeaderFieldByKey)) {
                                String singleHeaderFieldByKey2 = HeaderHandlerUtil.getSingleHeaderFieldByKey(connHeadFields, UploadConstants.X_DATA);
                                if (StringUtils.isNotBlank(singleHeaderFieldByKey2)) {
                                    singleHeaderFieldByKey2 = MtopUtils.urlDecode(singleHeaderFieldByKey2, "utf-8");
                                }
                                if (StringUtils.isNotBlank(singleHeaderFieldByKey2)) {
                                    UploadResult uploadResult = new UploadResult(true, singleHeaderFieldByKey2);
                                    uploadResult.serverRT = HeaderHandlerUtil.getSingleHeaderFieldByKey(connHeadFields, UploadConstants.X_SERVER_RT);
                                    result = new Result<>(uploadResult);
                                } else {
                                    result = new Result<>(new UploadResult(false, null));
                                }
                                result.setErrType("SUCCESS");
                                result.setErrCode("SUCCESS");
                            } else {
                                if (UploadConstants.ERRCODE_TOKEN_EXPIRED.equalsIgnoreCase(singleHeaderFieldByKey)) {
                                    String singleHeaderFieldByKey3 = HeaderHandlerUtil.getSingleHeaderFieldByKey(connHeadFields, UploadConstants.X_SERVER_TIMESTAMP);
                                    if (!StringUtils.isBlank(singleHeaderFieldByKey3)) {
                                        try {
                                            long j5 = Long.parseLong(singleHeaderFieldByKey3);
                                            if (j5 > 0) {
                                                timestampOffset = j5 - System.currentTimeMillis();
                                            }
                                        } catch (Exception unused) {
                                            a$$ExternalSyntheticOutline0.m626m("[computeTimeStampOffset] compute TimeStampOffset error,serverTimeStamp=", singleHeaderFieldByKey3, "mtopsdk.UploadFileServiceImpl");
                                        }
                                    }
                                }
                                String strUrlDecode = MtopUtils.urlDecode(HeaderHandlerUtil.getSingleHeaderFieldByKey(connHeadFields, UploadConstants.X_ERROR_MSG), "utf-8");
                                if (StringUtils.isBlank(strUrlDecode)) {
                                    strUrlDecode = str;
                                }
                                result = new Result<>(false, UploadConstants.ERRTYPE_OTHER_UPLOAD_ERROR, singleHeaderFieldByKey, strUrlDecode);
                            }
                        } else {
                            result = new Result<>(false, UploadConstants.ERRTYPE_OTHER_UPLOAD_ERROR, "ANDROID_SYS_NETWORK_ERROR", "ANDROID_SYS_NETWORK_ERROR");
                        }
                        result.setStatusCode(statusCode);
                    }
                    return result;
                } catch (Exception e) {
                    TBSdkLog.e("mtopsdk.UploadFileServiceImpl", "[fileUpload]gen fileUpload address url error", e);
                    new Result(false, UploadConstants.ERRTYPE_OTHER_UPLOAD_ERROR, UploadConstants.ERRCODE_INVALID_UPLOAD_ADDRESS, UploadConstants.ERRMSG_INVALID_UPLOAD_ADDRESS);
                    return null;
                }
            }
        }
        return new Result<>(false, UploadConstants.ERRTYPE_OTHER_UPLOAD_ERROR, UploadConstants.ERRCODE_INVALID_UPLOAD_TOKEN, UploadConstants.ERRMSG_INVALID_UPLOAD_TOKEN);
    }

    /* JADX WARN: Removed duplicated region for block: B:23:0x0059  */
    /* JADX WARN: Removed duplicated region for block: B:25:0x005c  */
    /* JADX WARN: Removed duplicated region for block: B:50:0x00cc  */
    /* JADX WARN: Removed duplicated region for block: B:56:0x00e2  */
    @Override // mtopsdk.mtop.upload.service.UploadFileService
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final mtopsdk.mtop.util.Result<mtopsdk.mtop.upload.domain.UploadToken> getUploadToken(mtopsdk.mtop.upload.domain.UploadFileInfo r14) {
        /*
            Method dump skipped, instruction units count: 522
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: mtopsdk.mtop.upload.service.UploadFileServiceImpl.getUploadToken(mtopsdk.mtop.upload.domain.UploadFileInfo):mtopsdk.mtop.util.Result");
    }
}
