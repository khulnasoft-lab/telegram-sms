package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.resend;
import com.qwe7002.telegram_sms.static_class.service;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;

import io.paperdb.Paper;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NotNull final Context context, @NotNull Intent intent) {
        final String TAG = "boot_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("initialized", false)) {
            Paper.init(context);
            log.writeLog(context, "Received [" + intent.getAction() + "] broadcast, starting background service.");
            service.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
            if (Objects.requireNonNull(Paper.book().read("resend_list", new ArrayList<>())).size() != 0) {
                Log.d(TAG, "An unsent message was detected, and the automatic resend process was initiated.");
                resend.startResend(context);
            }
        }
    }
}
