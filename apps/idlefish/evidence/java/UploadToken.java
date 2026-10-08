package mtopsdk.mtop.upload.domain;

import androidx.collection.ArraySet$$ExternalSyntheticOutline0;
import com.taobao.weex.el.parse.Operators;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/* JADX INFO: loaded from: classes9.dex */
public class UploadToken {
    public String bizCode;
    public String domain;
    public FileBaseInfo fileBaseInfo;
    public long retryCount;
    public long segmentSize;
    public String token;
    public HashMap tokenParams;
    public AtomicLong uploadedLength = new AtomicLong();

    public final String toString() {
        StringBuilder sbM50m = ArraySet$$ExternalSyntheticOutline0.m50m(64, "UploadToken [token=");
        sbM50m.append(this.token);
        sbM50m.append(", domain=");
        sbM50m.append(this.domain);
        sbM50m.append(", tokenParams=");
        sbM50m.append(this.tokenParams);
        sbM50m.append(", retryCount=");
        sbM50m.append(this.retryCount);
        sbM50m.append(", patchSize=");
        sbM50m.append(this.segmentSize);
        sbM50m.append(", fileBaseInfo=");
        sbM50m.append(this.fileBaseInfo);
        sbM50m.append(Operators.ARRAY_END_STR);
        return sbM50m.toString();
    }
}
