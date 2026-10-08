package mtopsdk.mtop.antiattack;

import com.alibaba.ariver.app.PageNode$$ExternalSyntheticOutline0;
import com.taobao.alivfssdk.utils.AVFSCacheConstants;
import java.util.concurrent.ConcurrentHashMap;
import mtopsdk.common.util.StringUtils;
import mtopsdk.common.util.TBSdkLog;
import mtopsdk.mtop.global.SwitchConfig;

/* JADX INFO: loaded from: classes9.dex */
public class ApiLockHelper {
    private static ConcurrentHashMap<String, LockedEntity> lockedMap = new ConcurrentHashMap<>();

    public static boolean iSApiLocked(long j, String str) {
        boolean z = false;
        if (StringUtils.isBlank(str)) {
            return false;
        }
        LockedEntity lockedEntity = lockedMap.get(str);
        if (lockedEntity != null) {
            if (Math.abs(j - lockedEntity.lockStartTime) < lockedEntity.lockInterval) {
                z = true;
            } else {
                lockedMap.remove(str);
                if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.WarnEnable)) {
                    TBSdkLog.w("mtopsdk.ApiLockHelper", "[iSApiLocked]remove apiKey=" + str);
                }
            }
            if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.WarnEnable)) {
                StringBuilder sbM = PageNode$$ExternalSyntheticOutline0.m("[iSApiLocked] isLocked=", z, AVFSCacheConstants.COMMA_SEP);
                StringBuilder sb = new StringBuilder(32);
                sb.append(", currentTime=");
                sb.append(j);
                sb.append(", lockEntity=");
                sb.append(lockedEntity.toString());
                sbM.append((Object) sb);
                TBSdkLog.w("mtopsdk.ApiLockHelper", sbM.toString());
            }
        }
        return z;
    }

    public static void lock(long j, long j2, String str) {
        long individualApiLockInterval;
        if (StringUtils.isBlank(str)) {
            return;
        }
        LockedEntity lockedEntity = lockedMap.get(str);
        if (j2 > 0) {
            individualApiLockInterval = j2 / 1000;
        } else {
            SwitchConfig.getInstance().getClass();
            individualApiLockInterval = SwitchConfig.getIndividualApiLockInterval(str);
        }
        if (individualApiLockInterval <= 0) {
            SwitchConfig.getInstance().getClass();
            individualApiLockInterval = SwitchConfig.getGlobalApiLockInterval();
            if (individualApiLockInterval <= 0) {
                individualApiLockInterval = 10;
            }
        }
        long j3 = individualApiLockInterval;
        if (lockedEntity == null) {
            lockedEntity = new LockedEntity(str, j, j3);
        } else {
            lockedEntity.lockStartTime = j;
            lockedEntity.lockInterval = j3;
        }
        lockedMap.put(str, lockedEntity);
        if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.WarnEnable)) {
            StringBuilder sb = new StringBuilder("[lock]");
            StringBuilder sb2 = new StringBuilder(32);
            sb2.append(", currentTime=");
            sb2.append(j);
            sb2.append(", lockEntity=");
            sb2.append(lockedEntity.toString());
            sb.append((Object) sb2);
            TBSdkLog.w("mtopsdk.ApiLockHelper", sb.toString());
        }
    }
}
