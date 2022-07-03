package com.essane.mkeyhook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import external.org.apache.commons.lang3.ObjectUtils;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * 主线程逻辑
 */
public class HookMain implements IXposedHookLoadPackage {
    private Context ctx;
    private Activity mActivity;
    private TextView tv;
    public static Socket mSocket;

    public void startClient() {
        try {
            if (ObjectUtil.isNotNull(mSocket)) return;
            Xlog("开始连接服务器");
            IO.Options opts = new IO.Options();
            opts.query = "username=" + NetUtil.getLocalMacAddress();
            opts.reconnection = true;
            opts.reconnectionAttempts = 10;
            opts.reconnectionDelay = 1000;
            opts.reconnectionDelayMax = 2000;
            opts.timeout = 5000;
            opts.transports = new String[]{"websocket"};
//            mSocket = IO.socket("http://81.69.254.58:9099", opts);//这里的地址我们用后台提供的
            mSocket = IO.socket("http://192.168.4.52:9099", opts);//这里的地址我们用后台提供的
            mSocket.connect();
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                Xlog("连接服务器成功");
            });
            mSocket.on(Socket.EVENT_CONNECTING, args -> {
                Xlog("连接服务器中");
            });
            mSocket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                String errMsg = String.valueOf(args[0]);
                Xlog(errMsg.substring(errMsg.indexOf(":") + 1));
            });
            mSocket.on(Socket.EVENT_ERROR, args -> {
                String errMsg = String.valueOf(args[0]);
                Xlog(errMsg.substring(errMsg.indexOf(":") + 1));
            });
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Xlog(e.getMessage());
        }
    }

    public void stopClient() {
        mSocket.off();
        mSocket.disconnect();
    }

    public void Xlog(String content) {
        XposedBridge.log(content);
        if (ObjectUtil.isNotNull(tv)) {
            mActivity.runOnUiThread(() -> {
                tv.setText(content);
                tv.setTextColor(Color.rgb(255, 0, 0));
            });
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        Log.d("tag", "hook开始......");
        if (!loadPackageParam.packageName.equals("com.netease.mkey")) return;

        XposedHelpers.findAndHookMethod("com.netease.mkey.core.OtpLib", loadPackageParam.classLoader, "a", long.class, long.class, byte[].class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                String mkey = param.getResult().toString();
                XposedBridge.log("Mkey-------------" + mkey);
                ThreadUtil.execAsync(() -> {
                    while (mSocket == null) ThreadUtil.sleep(1000);
                    mSocket.emit("updateMkey", mkey);
                });

            }
        });

        XposedHelpers.findAndHookMethod("com.netease.mkey.activity.NtSecActivity", loadPackageParam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                mActivity = (Activity) param.thisObject;
                ThreadUtil.execAsync(() -> {
                    while (tv == null) ThreadUtil.sleep(1000);
                    startClient();
                });


            }
        });
        XposedHelpers.findAndHookMethod("com.netease.mkey.activity.NtSecActivity", loadPackageParam.classLoader, "onDestroy", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                stopClient();
            }
        });
        XposedHelpers.findAndHookMethod("com.netease.mkey.fragment.LoginFragment", loadPackageParam.classLoader, "onAttach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                ctx = (Context) param.args[0];
            }
        });

        XposedHelpers.findAndHookMethod("com.netease.mkey.fragment.LoginFragment", loadPackageParam.classLoader, "onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                View view = (View) param.getResult();
                tv = view.findViewById(2131296978);
                //Xlog("已HOOK");
            }
        });
        Log.d("test", "hook结束.....");
    }


}
