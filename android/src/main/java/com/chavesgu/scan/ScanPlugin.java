package com.chavesgu.scan;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;

import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.content.Context.VIBRATOR_SERVICE;

/** ScanPlugin */
public class ScanPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  private MethodChannel channel;
  private Activity activity;
  private FlutterPluginBinding flutterPluginBinding;
  private Result _result;
  private ExecutorService executorService;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    this.flutterPluginBinding = flutterPluginBinding;
    this.executorService = Executors.newSingleThreadExecutor();
  }

  private void configChannel(ActivityPluginBinding binding) {
    activity = binding.getActivity();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "chavesgu/scan");
    channel.setMethodCallHandler(this);
    flutterPluginBinding.getPlatformViewRegistry()
            .registerViewFactory("chavesgu/scan_view", new ScanViewFactory(
                    flutterPluginBinding.getBinaryMessenger(),
                    flutterPluginBinding.getApplicationContext(),
                    activity,
                    binding
            ));
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
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
    }
    this.flutterPluginBinding = null;
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
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if (call.method.equals("parse")) {
      String path = (String) call.arguments;
      executeQRCodeDecoding(path);
    } else {
      result.notImplemented();
    }
  }

  private void executeQRCodeDecoding(String path) {
    executorService.execute(() -> {
      String decodedResult = QRCodeDecoder.decodeQRCode(
          flutterPluginBinding.getApplicationContext(),
          path
      );

      activity.runOnUiThread(() -> {
        _result.success(decodedResult);
        if (decodedResult != null) {
          Vibrator myVib = (Vibrator) flutterPluginBinding.getApplicationContext()
              .getSystemService(VIBRATOR_SERVICE);
          if (myVib != null) {
            if (Build.VERSION.SDK_INT >= 26) {
              myVib.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
              myVib.vibrate(50);
            }
          }
        }
      });
    });
  }
}
