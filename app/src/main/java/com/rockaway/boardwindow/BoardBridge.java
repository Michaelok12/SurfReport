package com.rockaway.boardwindow;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

public class BoardBridge {
    private static final String KEY_BOARD_SIGNATURE = "widget_board_signature";
    private final Context context;

    BoardBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    @JavascriptInterface
    public void syncState(String json) {
        if (json == null || json.length() > 100_000) return;
        SharedPreferences prefs = context.getSharedPreferences(ForecastRepository.PREFS_NAME, Context.MODE_PRIVATE);
        String signature = boardSignature(json);
        String previousSignature = prefs.getString(KEY_BOARD_SIGNATURE, "");
        prefs.edit()
                .putString(ForecastRepository.KEY_PWA_STATE, json)
                .putString(KEY_BOARD_SIGNATURE, signature)
                .apply();
        if (!signature.equals(previousSignature)) WidgetUpdateScheduler.requestImmediate(context);
    }

    private String boardSignature(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String active = root.optString("activeBoardId", "");
            JSONArray boards = root.optJSONArray("boards");
            if (boards == null) return active;
            for (int i = 0; i < boards.length(); i++) {
                JSONObject b = boards.optJSONObject(i);
                if (b != null && active.equals(b.optString("id"))) return active + "|" + b.toString();
            }
            return active;
        } catch (Exception e) {
            return json;
        }
    }
}
