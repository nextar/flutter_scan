#!/bin/bash
# Script de correção do flutter_scan para compatibilidade com Android API 35
# Execute este script na raiz do plugin flutter_scan

echo "==================================="
echo "Correção do flutter_scan para API 35"
echo "==================================="
echo ""

# Detectar automaticamente o caminho do plugin
PLUGIN_PATH=$(ls -d /Users/richardrsa/.pub-cache/git/flutter_scan-* 2>/dev/null | head -n 1)

if [ -z "$PLUGIN_PATH" ]; then
  echo "❌ Erro: Plugin flutter_scan não encontrado no cache!"
  echo ""
  echo "Execute primeiro:"
  echo "  cd /Users/richardrsa/git/nex_mobile_app"
  echo "  flutter pub get"
  echo ""
  echo "Depois rode este script novamente."
  exit 1
fi

echo "📦 Plugin encontrado: $PLUGIN_PATH"
echo ""

# 1. Corrigir ScanPlugin.java - Substituir AsyncTask por executors
echo "1. Corrigindo ScanPlugin.java..."
cat > "${PLUGIN_PATH}/android/src/main/java/com/chavesgu/scan/ScanPlugin.java" << 'EOF'
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
EOF

echo "   ✓ ScanPlugin.java corrigido"

# 2. Corrigir ScanViewNew.java - Substituir vibrate(long) depreciado
echo "2. Corrigindo ScanViewNew.java..."
if [ -f "${PLUGIN_PATH}/android/src/main/java/com/chavesgu/scan/ScanViewNew.java" ]; then
  sed -i.bak '73s/.*/                        myVib.vibrate(50);/' "${PLUGIN_PATH}/android/src/main/java/com/chavesgu/scan/ScanViewNew.java"
  echo "   ✓ ScanViewNew.java corrigido"
else
  echo "   - ScanViewNew.java não encontrado (pode não existir nesta versão)"
fi

# 3. Adicionar namespace ao build.gradle
echo "3. Adicionando namespace ao build.gradle..."
if ! grep -q "namespace 'com.chavesgu.scan'" "${PLUGIN_PATH}/android/build.gradle"; then
  sed -i.bak '/^android {$/a\
    namespace '\''com.chavesgu.scan'\''
' "${PLUGIN_PATH}/android/build.gradle"
  echo "   ✓ Namespace adicionado ao build.gradle"
else
  echo "   - Namespace já existe no build.gradle"
fi

echo ""
echo "==================================="
echo "Correções aplicadas com sucesso!"
echo "==================================="
echo ""
echo "Próximos passos:"
echo "1. Execute: cd /Users/richardrsa/git/nex_mobile_app"
echo "2. Execute: flutter clean"
echo "3. Execute: flutter pub get"
echo "4. Execute: flutter build apk --debug"
echo ""
echo "Arquivos modificados:"
echo "- ScanPlugin.java (AsyncTask → ExecutorService)"
echo "- ScanViewNew.java (vibrate depreciado corrigido)"
echo "- build.gradle (namespace adicionado)"
