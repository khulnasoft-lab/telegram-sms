package com.qwe7002.telegram_sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.qwe7002.telegram_sms.config.proxy;
import com.qwe7002.telegram_sms.data_structure.request_message;
import com.qwe7002.telegram_sms.static_class.log;
import com.qwe7002.telegram_sms.static_class.network;
import com.qwe7002.telegram_sms.static_class.other;
import com.qwe7002.telegram_sms.static_class.resend;
import com.qwe7002.telegram_sms.static_class.sms;
import com.qwe7002.telegram_sms.value.const_value;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class call_receiver extends BroadcastReceiver {
    private static int slot;
    private static String incomingNumber;

    @Override
    public void onReceive(Context context, @NotNull Intent intent) {
        Paper.init(context);
        Log.d("call_receiver", "Receive action: " + intent.getAction());
        switch (Objects.requireNonNull(intent.getAction())) {
            case "android.intent.action.PHONE_STATE":
                if (intent.getStringExtra("incoming_number") != null) {
                    incomingNumber = intent.getStringExtra("incoming_number");
                }
                TelephonyManager telephony = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                callStatusListener custom_phone_listener = new callStatusListener(context, slot, incomingNumber);
                assert telephony != null;
                telephony.listen(custom_phone_listener, PhoneStateListener.LISTEN_CALL_STATE);
                break;
            case "android.intent.action.SUBSCRIPTION_PHONE_STATE":
                slot = intent.getIntExtra("slot", -1);
                break;

        }
    }

    static class callStatusListener extends PhoneStateListener {
        private static int last_receive_status = TelephonyManager.CALL_STATE_IDLE;
        private static String incoming_number;
        private final Context context;
        private final int slot;

        callStatusListener(Context context, int slot, String incoming_number) {
            super();
            this.context = context;
            this.slot = slot;
            callStatusListener.incoming_number = incoming_number;
        }

        public void onCallStateChanged(int now_state, String now_incoming_number) {
            if (last_receive_status == TelephonyManager.CALL_STATE_RINGING
                    && now_state == TelephonyManager.CALL_STATE_IDLE) {
                final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
                if (!sharedPreferences.getBoolean("initialized", false)) {
                    Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.");
                    return;
                }
                String bot_token = sharedPreferences.getString("bot_token", "");
                String chat_id = sharedPreferences.getString("chat_id", "");
                String message_thread_id = sharedPreferences.getString("message_thread_id", "");
                String requestUri = network.getUrl(bot_token, "sendMessage");
                final request_message request_body = new request_message();
                request_body.chat_id = chat_id;
                request_body.message_thread_id = message_thread_id;
                String dual_sim = other.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
                request_body.text = "[" + dual_sim + context.getString(R.string.missed_call_head) + "]" + "\n" + context.getString(R.string.Incoming_number) + callStatusListener.incoming_number;
                String request_body_raw = new Gson().toJson(request_body);
                RequestBody body = RequestBody.create(request_body_raw, const_value.JSON);
                OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
                Request request = new Request.Builder().url(requestUri).method("POST", body).build();
                Call call = okhttp_client.newCall(request);
                final String error_head = "Send missed call error:";
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        e.printStackTrace();
                        log.writeLog(context, error_head + e.getMessage());
                        sms.send_fallback_sms(context, request_body.text, other.getSubId(context, slot));
                        resend.addResendLoop(context, request_body.text);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        assert response.body() != null;
                        if (response.code() != 200) {
                            String error_message = error_head + response.code() + " " + Objects.requireNonNull(response.body()).string();
                            log.writeLog(context, error_message);
                            resend.addResendLoop(context, request_body.text);
                        } else {
                            String result = Objects.requireNonNull(response.body()).string();
                            if (!other.isPhoneNumber(callStatusListener.incoming_number)) {
                                log.writeLog(context, "[" + callStatusListener.incoming_number + "] Not a regular phone number.");
                                return;
                            }
                            other.add_message_list(other.get_message_id(result), callStatusListener.incoming_number, slot);
                        }
                    }
                });
            }
            last_receive_status = now_state;
        }
    }
}


