package com.chavesgu.scan;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class ScanPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

    private MethodChannel channel;
    private Activity activity;
    private FlutterPluginBinding flutterPluginBinding;
    private Result _result;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.flutterPluginBinding = flutterPluginBinding;
    }

    private void configChannel(ActivityPluginBinding binding) {
        activity = binding.getActivity();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "chavesgu/scan");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        configChannel(binding);
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        configChannel(binding);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        flutterPluginBinding = null;
        executorService.shutdown(); // Libera os recursos da thread
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        _result = result;
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + Build.VERSION.RELEASE);
        } else if (call.method.equals("parse")) {
            String path = call.argument("path");
            if (path != null) {
                if (hasPermission()) {
                    decodeQrCode(path);
                } else {
                    requestPermission();
                }
            } else {
                result.error("INVALID_ARGUMENT", "Path cannot be null", null);
            }
        } else {
            result.notImplemented();
        }
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1001);
    }

    private void decodeQrCode(String path) {
        executorService.execute(() -> {
            String result = QRCodeDecoder.decodeQRCode(flutterPluginBinding.getApplicationContext(), path);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (_result != null) {
                    if (result != null) {
                        _result.success(result);
                        vibrate();
                    } else {
                        _result.error("QR_CODE_NOT_FOUND", "Failed to decode QR code", null);
                    }
                    _result = null;
                }
            });
        });
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) flutterPluginBinding.getApplicationContext().getSystemService(Activity.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    // Permissões de retorno
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida, continue o processo
                if (_result != null) {
                    decodeQrCode(_result.toString());
                }
            } else {
                // Permissão negada
                if (_result != null) {
                    _result.error("PERMISSION_DENIED", "Storage permission denied", null);
                    _result = null;
                }
            }
        }
    }
}
