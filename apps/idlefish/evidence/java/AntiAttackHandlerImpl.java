package mtopsdk.mtop.antiattack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import com.ali.user.mobile.utils.UTConstans;
import com.taobao.tao.remotebusiness.RequestPoolManager;
import java.util.concurrent.atomic.AtomicBoolean;
import mtopsdk.common.util.TBSdkLog;
import mtopsdk.mtop.global.SwitchConfig;
import mtopsdk.mtop.intf.Mtop;
import mtopsdk.mtop.util.ErrorConstant;
import mtopsdk.xstate.XState;

/* JADX INFO: loaded from: classes9.dex */
public class AntiAttackHandlerImpl implements AntiAttackHandler {
    final Context mContext;
    final AtomicBoolean isHandling = new AtomicBoolean(false);
    private final IntentFilter intentFilter = new IntentFilter("mtopsdk.extra.antiattack.result.notify.action");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = new Runnable() { // from class: mtopsdk.mtop.antiattack.AntiAttackHandlerImpl.1
        @Override // java.lang.Runnable
        public final void run() {
            AntiAttackHandlerImpl antiAttackHandlerImpl = AntiAttackHandlerImpl.this;
            antiAttackHandlerImpl.isHandling.set(false);
            RequestPoolManager.getPool(RequestPoolManager.Type.ANTI).failAllRequest(Mtop.instance(Mtop.Id.INNER, antiAttackHandlerImpl.mContext), "", ErrorConstant.ERRCODE_API_41X_ANTI_ATTACK, ErrorConstant.ERRMSG_API_41X_ANTI_ATTACK);
        }
    };
    final BroadcastReceiver antiAttackReceiver = new BroadcastReceiver() { // from class: mtopsdk.mtop.antiattack.AntiAttackHandlerImpl.2
        @Override // android.content.BroadcastReceiver
        public final void onReceive(Context context, Intent intent) {
            Context context2;
            BroadcastReceiver broadcastReceiver;
            AntiAttackHandlerImpl antiAttackHandlerImpl = AntiAttackHandlerImpl.this;
            try {
                try {
                    try {
                        String stringExtra = intent.getStringExtra(UTConstans.CustomEvent.UT_REG_RESULT);
                        TBSdkLog.i("mtopsdk.AntiAttackHandlerImpl", "[onReceive]AntiAttack result: " + stringExtra);
                        if ("success".equals(stringExtra)) {
                            RequestPoolManager.getPool(RequestPoolManager.Type.ANTI).retryAllRequest(Mtop.instance(Mtop.Id.INNER, antiAttackHandlerImpl.mContext), "");
                        } else {
                            RequestPoolManager.getPool(RequestPoolManager.Type.ANTI).failAllRequest(Mtop.instance(Mtop.Id.INNER, antiAttackHandlerImpl.mContext), "", ErrorConstant.ERRCODE_API_41X_ANTI_ATTACK, ErrorConstant.ERRMSG_API_41X_ANTI_ATTACK);
                        }
                        antiAttackHandlerImpl.handler.removeCallbacks(antiAttackHandlerImpl.timeoutRunnable);
                        antiAttackHandlerImpl.isHandling.set(false);
                        context2 = antiAttackHandlerImpl.mContext;
                        broadcastReceiver = antiAttackHandlerImpl.antiAttackReceiver;
                    } catch (Throwable th) {
                        antiAttackHandlerImpl.handler.removeCallbacks(antiAttackHandlerImpl.timeoutRunnable);
                        antiAttackHandlerImpl.isHandling.set(false);
                        try {
                            antiAttackHandlerImpl.mContext.unregisterReceiver(antiAttackHandlerImpl.antiAttackReceiver);
                        } catch (Exception unused) {
                            TBSdkLog.e("mtopsdk.AntiAttackHandlerImpl", "waiting antiattack exception");
                        }
                        throw th;
                    }
                } catch (Exception unused2) {
                    TBSdkLog.e("mtopsdk.AntiAttackHandlerImpl", "[onReceive]AntiAttack exception");
                    RequestPoolManager.getPool(RequestPoolManager.Type.ANTI).failAllRequest(Mtop.instance(Mtop.Id.INNER, antiAttackHandlerImpl.mContext), "", ErrorConstant.ERRCODE_API_41X_ANTI_ATTACK, ErrorConstant.ERRMSG_API_41X_ANTI_ATTACK);
                    antiAttackHandlerImpl.handler.removeCallbacks(antiAttackHandlerImpl.timeoutRunnable);
                    antiAttackHandlerImpl.isHandling.set(false);
                    context2 = antiAttackHandlerImpl.mContext;
                    broadcastReceiver = antiAttackHandlerImpl.antiAttackReceiver;
                }
                context2.unregisterReceiver(broadcastReceiver);
            } catch (Exception unused3) {
                TBSdkLog.e("mtopsdk.AntiAttackHandlerImpl", "waiting antiattack exception");
            }
        }
    };

    public AntiAttackHandlerImpl(Context context) {
        this.mContext = context;
    }

    @Override // mtopsdk.mtop.antiattack.AntiAttackHandler
    public final void handle(String str, String str2) {
        Runnable runnable = this.timeoutRunnable;
        Handler handler = this.handler;
        Context context = this.mContext;
        String string = new StringBuilder(str).toString();
        boolean zIsAppBackground = XState.isAppBackground();
        if (TBSdkLog.isLogEnable(TBSdkLog.LogEnable.InfoEnable)) {
            TBSdkLog.i("mtopsdk.AntiAttackHandlerImpl", "[handle]execute new 419 Strategy,location=" + string + ", isBackground=" + zIsAppBackground);
        }
        AtomicBoolean atomicBoolean = this.isHandling;
        if (!atomicBoolean.compareAndSet(false, true)) {
            TBSdkLog.i("mtopsdk.AntiAttackHandlerImpl", "isHandling");
            return;
        }
        try {
            SwitchConfig.getInstance().getClass();
            long globalAttackAttackWaitInterval = SwitchConfig.getGlobalAttackAttackWaitInterval();
            handler.postDelayed(runnable, globalAttackAttackWaitInterval > 0 ? globalAttackAttackWaitInterval * 1000 : 20000L);
            Intent intent = new Intent();
            intent.setAction("mtopsdk.mtop.antiattack.checkcode.validate.activity_action");
            intent.setPackage(context.getPackageName());
            intent.setFlags(268435456);
            intent.putExtra("Location", string);
            context.startActivity(intent);
            context.registerReceiver(this.antiAttackReceiver, this.intentFilter);
        } catch (Exception e) {
            atomicBoolean.set(false);
            handler.removeCallbacks(runnable);
            RequestPoolManager.getPool(RequestPoolManager.Type.ANTI).failAllRequest(Mtop.instance(Mtop.Id.INNER, context), "", ErrorConstant.ERRCODE_API_41X_ANTI_ATTACK, ErrorConstant.ERRMSG_API_41X_ANTI_ATTACK);
            TBSdkLog.w("mtopsdk.AntiAttackHandlerImpl", "[handle] execute new 419 Strategy error.", e);
        }
    }
}
