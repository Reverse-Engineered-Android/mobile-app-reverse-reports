package mtopsdk.security;

import android.text.TextUtils;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes9.dex */
public class SignConfig {
    private String abExperiment;
    private final CopyOnWriteArrayList apiList = new CopyOnWriteArrayList();

    public static SignConfig create(JSONObject jSONObject) {
        SignConfig signConfig = new SignConfig();
        try {
            signConfig.abExperiment = jSONObject.getString("ab");
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("api");
            if (jSONArrayOptJSONArray != null) {
                for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                    String string = jSONArrayOptJSONArray.getString(i);
                    if (!TextUtils.isEmpty(string)) {
                        signConfig.apiList.add(string);
                    }
                }
            }
        } catch (Throwable unused) {
        }
        return signConfig;
    }

    public final boolean checkValid(String str) {
        return this.apiList.contains(str);
    }

    public final String getAbExperiment() {
        return this.abExperiment;
    }
}
