package com.bcm.messenger.utility;

import android.annotation.SuppressLint;
import android.app.Application;

import org.jetbrains.annotations.NotNull;

/**
 * Created by bcm.social.01 on 2018/9/4.
 */

public class AppContextHolder {

    @SuppressLint("StaticFieldLeak")
    public static Application APP_CONTEXT;
    public static void init(@NotNull Application application) {
        APP_CONTEXT = application;
    }

}
