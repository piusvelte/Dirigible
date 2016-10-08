package com.piusvelte.dirigible.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

/**
 * Created by bemmanuel on 2/27/16.
 */
public class SharedPreferencesUtils {

    private static final String PREFERENCES_NAME = "settings";
    private static final String KEY_ACCOUNT = "account";

    private SharedPreferencesUtils() {
        // not instantiable
    }

    public static SharedPreferences get(@NonNull Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor edit(@NonNull Context context) {
        return get(context).edit();
    }

    @Nullable
    public static String getAccount(@NonNull Context context) {
        return get(context).getString(KEY_ACCOUNT, null);
    }

    public static void putAccount(@NonNull Context context, @NonNull String account) {
        if (TextUtils.isEmpty(account)) return;
        edit(context).putString(KEY_ACCOUNT, account)
                .apply();
    }

}