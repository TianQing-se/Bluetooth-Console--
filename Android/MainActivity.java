package com.bluetoothconsole.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.io.OutputStream;

public final class MainActivity extends Activity {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private BluetoothAdapter adapter;
    private BluetoothDevice selected;
    private volatile BluetoothSocket socket;
    private WebView view;
    private ValueCallback<Uri[]> filePicker;
    private String exportData;
    private int permissionRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = ((BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        view = new WebView(this);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = callback;
                try { startActivityForResult(params.createIntent(), 20); }
                catch (Exception e) { filePicker = null; callback.onReceiveValue(null); }
                return true;
            }
            @Override public boolean onJsConfirm(WebView v, String url, String text, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(text)
                    .setPositiveButton("继续", (d,w) -> result.confirm())
                    .setNegativeButton("取消", (d,w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });
        view.addJavascriptInterface(new Bridge(), "AndroidBluetooth");
        setContentView(view);
        view.loadUrl("file:///android_asset/console.html");
    }

    private void done(int id, String error) {
        runOnUiThread(() -> {
            if (!isDestroyed()) view.evaluateJavascript("window.serialResult(" + id + "," +
                (error == null ? "null" : JSONObject.quote(error)) + ")", null);
        });
    }

    private void pick(int id) {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionRequest = id;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 10);
            return;
        }
        try {
            if (adapter == null || !adapter.isEnabled()) {
                done(id, "请先在手机设置中打开蓝牙。");
                return;
            }
            List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
            if (devices.isEmpty()) {
                done(id, "请先在手机系统设置中配对蓝牙串口设备。");
                return;
            }
            String[] names = new String[devices.size()];
            for (int i = 0; i < names.length; i++) {
                BluetoothDevice device = devices.get(i);
                names[i] = (device.getName() == null ? "蓝牙设备" : device.getName()) + "\n" + device.getAddress();
            }
            new AlertDialog.Builder(this).setTitle("选择已配对设备")
                .setItems(names, (dialog,index) -> { selected = devices.get(index); done(id,null); })
                .setNegativeButton("取消", (dialog,which) -> done(id,"已取消选择。"))
                .setOnCancelListener(dialog -> done(id,"已取消选择。")).show();
        } catch (Exception e) { done(id, "无法读取蓝牙设备：" + e.getMessage()); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 10) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) pick(permissionRequest);
        else done(permissionRequest, "请允许附近设备权限后重试。");
    }

    private void closeSocket() {
        BluetoothSocket active = socket;
        socket = null;
        if (active != null) try { active.close(); } catch (Exception ignored) {}
    }

    public final class Bridge {
        @JavascriptInterface public void pickDevice(int id) { runOnUiThread(() -> pick(id)); }

        @JavascriptInterface public void connect(int id) {
            io.execute(() -> {
                try {
                    closeSocket();
                    if (selected == null) throw new IllegalStateException("请先选择设备。");
                    BluetoothSocket pending = selected.createRfcommSocketToServiceRecord(SPP);
                    socket = pending;
                    pending.connect();
                    done(id,null);
                } catch (Exception e) {
                    closeSocket();
                    done(id,"无法打开蓝牙串口：" + e.getMessage());
                }
            });
        }

        @JavascriptInterface public void send(int id, String encoded) {
            io.execute(() -> {
                try {
                    BluetoothSocket active = socket;
                    if (active == null) throw new IllegalStateException("蓝牙串口未连接。");
                    active.getOutputStream().write(Base64.decode(encoded, Base64.NO_WRAP));
                    done(id,null);
                } catch (Exception e) {
                    closeSocket();
                    done(id,e.getMessage() == null ? "发送失败。" : e.getMessage());
                    runOnUiThread(() -> {
                        if (!isDestroyed()) view.evaluateJavascript("window.serialDisconnected()",null);
                    });
                }
            });
        }

        @JavascriptInterface public void disconnect(int id) {
            io.execute(() -> { closeSocket(); done(id,null); });
        }

        @JavascriptInterface public void exportProfile(String text) {
            runOnUiThread(() -> {
                exportData = text;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "Bluetooth Console.json");
                try { startActivityForResult(intent, 21); }
                catch (Exception e) { exportData = null; }
            });
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 20 && filePicker != null) {
            filePicker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
            filePicker = null;
        }
        if (request == 21) {
            if (result == RESULT_OK && data != null && exportData != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    out.write(exportData.getBytes("UTF-8"));
                } catch (Exception e) {
                    new AlertDialog.Builder(this).setMessage("无法保存方案。").setPositiveButton("确定",null).show();
                }
            }
            exportData = null;
        }
    }

    @Override protected void onDestroy() {
        closeSocket();
        io.shutdownNow();
        view.destroy();
        super.onDestroy();
    }
}
