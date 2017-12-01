package it.innove;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import org.json.JSONException;
import org.json.JSONObject;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import java.util.*;

import static android.app.Activity.RESULT_OK;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

import static android.app.Activity.RESULT_OK;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.facebook.react.bridge.UiThreadUtil.runOnUiThread;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.*;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

class BleManager extends ReactContextBaseJavaModule implements ActivityEventListener {

   private static final String LOG_TAG = "logs";
   static final int ENABLE_REQUEST = 1;


   private BluetoothAdapter bluetoothAdapter;
   private Context context;
   private ReactContext reactContext;
   private Callback enableBluetoothCallback;

   private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
      @Override
      public void onDeviceConnecting(final String deviceAddress) {
         System.out.println("Device Connecting to " + deviceAddress);
      }

      @Override
      public void onDfuProcessStarting(final String deviceAddress) {
         System.out.println("Process Starting " + deviceAddress);
      }

      @Override
      public void onEnablingDfuMode(final String deviceAddress) {
         System.out.println("enabling dfu mode " + deviceAddress);
      }

      @Override
      public void onFirmwareValidating(final String deviceAddress) {
         System.out.println("Firmware Validating" + deviceAddress);
      }

      @Override
      public void onDeviceDisconnecting(final String deviceAddress) {
         System.out.println("Device Disconnecting" + deviceAddress);
      }

      @Override
      public void onDfuCompleted(final String deviceAddress) {
         System.out.println("Dfu completed" + deviceAddress);
      }

      @Override
      public void onDfuAborted(final String deviceAddress) {
         System.out.println("Dfu aborted" + deviceAddress);
    }

      @Override
      public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
         System.out.println("Dfu progress" + percent);
      }

      @Override
      public void onError(final String deviceAddress, final int error, final int errorType, final String message) {
         System.out.println("Dfu error" + message);
      }
   };

   // key is the MAC Address
   private Map<String, Peripheral> peripherals = new LinkedHashMap<>();


   public BleManager(ReactApplicationContext reactContext) {
      super(reactContext);
      context = reactContext;
      this.reactContext = reactContext;
      reactContext.addActivityEventListener(this);
      Log.d(LOG_TAG, "BleManager created");
   }

   @Override
   public String getName() {
      return "BleManager";
   }

   private BluetoothAdapter getBluetoothAdapter() {
      if (bluetoothAdapter == null) {
         BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
         bluetoothAdapter = manager.getAdapter();
      }
      return bluetoothAdapter;
   }

   private void sendEvent(String eventName,
                     @Nullable WritableMap params) {
      getReactApplicationContext()
            .getJSModule(RCTNativeAppEventEmitter.class)
            .emit(eventName, params);
   }

   @ReactMethod
   public void start(ReadableMap options, Callback callback) {
      Log.d(LOG_TAG, "start");
      if (getBluetoothAdapter() == null) {
         Log.d(LOG_TAG, "No bluetooth support");
         try { callback.invoke("No bluetooth support"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         return;
      }
      IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      context.registerReceiver(mReceiver, filter);
      try { callback.invoke(); }
      catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
      Log.d(LOG_TAG, "BleManager initialized");
   }

   @ReactMethod
   public void enableBluetooth(Callback callback) {
      if (getBluetoothAdapter() == null) {
         Log.d(LOG_TAG, "No bluetooth support");
         try { callback.invoke("No bluetooth support"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         return;
      }
      if (!getBluetoothAdapter().isEnabled()) {
         enableBluetoothCallback = callback;
         Intent intentEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
         if (getCurrentActivity() == null)
            try { callback.invoke("Current activity not available"); }
            catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         else
            getCurrentActivity().startActivityForResult(intentEnable, ENABLE_REQUEST);
      } else
         try { callback.invoke(); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   @ReactMethod
   public void scan(ReadableArray serviceUUIDs, final int scanSeconds, boolean allowDuplicates, Callback callback) {
      Log.d(LOG_TAG, "scan");
      if (getBluetoothAdapter() == null) {
         Log.d(LOG_TAG, "No bluetooth support");
         try { callback.invoke("No bluetooth support"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         return;
      }
      if (!getBluetoothAdapter().isEnabled())
         return;

      for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
         Map.Entry<String, Peripheral> entry = iterator.next();
         if (!entry.getValue().isConnected()) {
            iterator.remove();
         }
      }

      if (Build.VERSION.SDK_INT >= LOLLIPOP) {
         scan21(serviceUUIDs, scanSeconds, callback);
      } else {
         scan19(serviceUUIDs, scanSeconds, callback);
      }
   }

   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   private void scan21(ReadableArray serviceUUIDs, final int scanSeconds, Callback callback) {
      ScanSettings settings = new ScanSettings.Builder().build();
      List<ScanFilter> filters = new ArrayList<>();

      if (serviceUUIDs.size() > 0) {
         for(int i = 0; i < serviceUUIDs.size(); i++){
            ScanFilter.Builder builder = new ScanFilter.Builder();
            builder.setServiceUuid(new ParcelUuid(UUIDHelper.uuidFromString(serviceUUIDs.getString(i))));
            filters.add(builder.build());
            Log.d(LOG_TAG, "Filter service: " + serviceUUIDs.getString(i));
         }
      }

      final ScanCallback mScanCallback = new ScanCallback() {
         @Override
         public void onScanResult(final int callbackType, final ScanResult result) {

            runOnUiThread(new Runnable() {
               @Override
               public void run() {
                  Log.i(LOG_TAG, "DiscoverPeripheral: " + result.getDevice().getName());
                  String address = result.getDevice().getAddress();

                  if (!peripherals.containsKey(address)) {

                     Peripheral peripheral = new Peripheral(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes(), reactContext);
                     peripherals.put(address, peripheral);

                     BundleJSONConverter bjc = new BundleJSONConverter();
                     try {
                        Bundle bundle = bjc.convertToBundle(peripheral.asJSONObject());
                        WritableMap map = Arguments.fromBundle(bundle);
                        sendEvent("BleManagerDiscoverPeripheral", map);
                     } catch (JSONException ignored) {

                     }


                  } else {
                     // this isn't necessary
                     Peripheral peripheral = peripherals.get(address);
                     peripheral.updateRssi(result.getRssi());
                  }
               }
            });
         }

         @Override
         public void onBatchScanResults(final List<ScanResult> results) {
         }

         @Override
         public void onScanFailed(final int errorCode) {
         }
      };

      getBluetoothAdapter().getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
      if (scanSeconds > 0) {
         Thread thread = new Thread() {

            @Override
            public void run() {

               try {
                  Thread.sleep(scanSeconds * 1000);
               } catch (InterruptedException ignored) {
               }

               runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                     getBluetoothAdapter().getBluetoothLeScanner().stopScan(mScanCallback);
                     WritableMap map = Arguments.createMap();
                     sendEvent("BleManagerStopScan", map);
                  }
               });

            }

         };
         thread.start();
      }
      try { callback.invoke(); }
      catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   private void scan19(ReadableArray serviceUUIDs, final int scanSeconds, Callback callback) {
      getBluetoothAdapter().startLeScan(mLeScanCallback);
      if (scanSeconds > 0) {
         Thread thread = new Thread() {

            @Override
            public void run() {

               try {
                  Thread.sleep(scanSeconds * 1000);
               } catch (InterruptedException ignored) {
               }

               runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                     getBluetoothAdapter().stopLeScan(mLeScanCallback);
                     WritableMap map = Arguments.createMap();
                     sendEvent("BleManagerStopScan", map);
                  }
               });

            }

         };
         thread.start();
      }
      try { callback.invoke(); }
      catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }


   @TargetApi(Build.VERSION_CODES.LOLLIPOP)
   private void stopScan21(Callback callback) {

      final ScanCallback mScanCallback = new ScanCallback() {
         @Override
         public void onScanResult(final int callbackType, final ScanResult result) {
         }

      };

      getBluetoothAdapter().getBluetoothLeScanner().stopScan(mScanCallback);
      try { callback.invoke(); }
      catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   private void stopScan19(Callback callback) {
      getBluetoothAdapter().stopLeScan(mLeScanCallback);
      try { callback.invoke(); }
      catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }


   @ReactMethod
   public void stopScan(Callback callback) {
      Log.d(LOG_TAG, "Stop scan");
      if (getBluetoothAdapter() == null) {
         Log.d(LOG_TAG, "No bluetooth support");
         try { callback.invoke("No bluetooth support"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         return;
      }
      if (!getBluetoothAdapter().isEnabled()) {
         try { callback.invoke("Bluetooth not enabled"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         return;
      }
      if (Build.VERSION.SDK_INT >= LOLLIPOP) {
         stopScan21(callback);
      } else {
         stopScan19(callback);
      }
   }

   @ReactMethod
   public void connect(String peripheralUUID, Callback callback) {
      Log.d(LOG_TAG, "Connect to: " + peripheralUUID );

      Peripheral peripheral = peripherals.get(peripheralUUID);
      if (peripheral == null) {
         if (peripheralUUID != null) {
            peripheralUUID = peripheralUUID.toUpperCase();
         }
         if (bluetoothAdapter.checkBluetoothAddress(peripheralUUID)) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(peripheralUUID);
            peripheral = new Peripheral(device, reactContext);
            peripherals.put(peripheralUUID, peripheral);
         } else {
            try { callback.invoke("Invalid peripheral uuid"); }
            catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
            return;
         }
      }
      peripheral.connect(callback, getCurrentActivity());
   }

   @ReactMethod
   public void disconnect(String peripheralUUID, Callback callback) {
      Log.d(LOG_TAG, "Disconnect from: " + peripheralUUID);

      Peripheral peripheral = peripherals.get(peripheralUUID);
      if (peripheral != null){
         peripheral.disconnect();
         try { callback.invoke(); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
      } else
         try { callback.invoke("Peripheral not found"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   @ReactMethod
   public void startNotification(int userId, String proxyUrl, String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
      Log.d(LOG_TAG, "startNotification");
      Log.d(LOG_TAG, "ssssssssssssssssssssssssssssssssssstartNotification");
      System.out.println(userId);
      System.out.println(proxyUrl);

      Peripheral peripheral = peripherals.get(deviceUUID);
      if (peripheral != null){
         peripheral.registerNotify(userId, proxyUrl, UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
      } else
         try { callback.invoke("Peripheral not found"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   @ReactMethod
   public void stopNotification(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
      Log.d(LOG_TAG, "stopNotification");

      Peripheral peripheral = peripherals.get(deviceUUID);
      if (peripheral != null){
         peripheral.removeNotify(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
      } else
         try { callback.invoke("Peripheral not found"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }



   @ReactMethod
   public void write(String deviceUUID, String serviceUUID, String characteristicUUID, String message, Integer maxByteSize, Callback callback) {
      Log.d(LOG_TAG, "Write to: " + deviceUUID);

      Peripheral peripheral = peripherals.get(deviceUUID);
      if (peripheral != null){
         try {
            byte[] decoded = Base64.decode(message.getBytes(), Base64.DEFAULT);
            Log.d(LOG_TAG, "Message(" + decoded.length + "): " + bytesToHex(decoded));
            peripheral.write(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), decoded, maxByteSize, null, callback, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
         } catch(Exception e) {
            try { callback.invoke("Peripheral not found"); }
            catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         }
      } else
         try { callback.invoke("Peripheral not found"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   @ReactMethod
   public void writeWithoutResponse(String deviceUUID, String serviceUUID, String characteristicUUID, String message, Integer maxByteSize, Integer queueSleepTime, Callback callback) {
      Log.d(LOG_TAG, "Write without response to: " + deviceUUID);

      Peripheral peripheral = peripherals.get(deviceUUID);
      if (peripheral != null){
         byte[] decoded = Base64.decode(message.getBytes(), Base64.DEFAULT);
         Log.d(LOG_TAG, "Message(" + decoded.length + "): " + bytesToHex(decoded));
         peripheral.write(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), decoded, maxByteSize, queueSleepTime, callback, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
      } else
         try { callback.invoke("Peripheral not found"); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   @ReactMethod
   public void read(String deviceUUID, String serviceUUID, String characteristicUUID, Callback callback) {
      Log.d(LOG_TAG, "Read from: " + deviceUUID);
      Peripheral peripheral = peripherals.get(deviceUUID);
      if (peripheral != null){
         try {
            peripheral.read(UUIDHelper.uuidFromString(serviceUUID), UUIDHelper.uuidFromString(characteristicUUID), callback);
         } catch(Exception e) {
            try { callback.invoke("Peripheral not found"); }
            catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         }
      } else
         try { callback.invoke("Peripheral not found", null); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   @ReactMethod
   public void readRSSI(String deviceUUID,  Callback callback) {
      Log.d(LOG_TAG, "Read RSSI from: " + deviceUUID);
      Peripheral peripheral = peripherals.get(deviceUUID);
      if (peripheral != null){
         peripheral.readRSSI(callback);
      } else
         try { callback.invoke("Peripheral not found", null); }
         catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   private BluetoothAdapter.LeScanCallback mLeScanCallback =
         new BluetoothAdapter.LeScanCallback() {


            @Override
            public void onLeScan(final BluetoothDevice device, final int rssi,
                            final byte[] scanRecord) {
               runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                     Log.i(LOG_TAG, "DiscoverPeripheral: " + device.getName());
                     String address = device.getAddress();

                     if (!peripherals.containsKey(address)) {

                        Peripheral peripheral = new Peripheral(device, rssi, scanRecord, reactContext);
                        peripherals.put(device.getAddress(), peripheral);

                        BundleJSONConverter bjc = new BundleJSONConverter();
                        try {
                           Bundle bundle = bjc.convertToBundle(peripheral.asJSONObject());
                           WritableMap map = Arguments.fromBundle(bundle);
                           sendEvent("BleManagerDiscoverPeripheral", map);
                        } catch (JSONException ignored) {

                        }


                     } else {
                        // this isn't necessary
                        Peripheral peripheral = peripherals.get(address);
                        peripheral.updateRssi(rssi);
                     }
                  }
               });
            }


         };

   @ReactMethod
   public void checkState(){
      Log.d(LOG_TAG, "checkState");

      BluetoothAdapter adapter = getBluetoothAdapter();
      String state = "off";
      if (adapter != null) {
         switch (adapter.getState()){
            case BluetoothAdapter.STATE_ON:
               state = "on";
               break;
            case BluetoothAdapter.STATE_OFF:
               state = "off";
         }
      }

      WritableMap map = Arguments.createMap();
      map.putString("state", state);
      Log.d(LOG_TAG, "state:" + state);
      sendEvent("BleManagerDidUpdateState", map);
   }

   private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
         Log.d(LOG_TAG, "onReceive");
         final String action = intent.getAction();

         String stringState = "";
         if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                  BluetoothAdapter.ERROR);
            switch (state) {
               case BluetoothAdapter.STATE_OFF:
                  stringState = "off";
                  break;
               case BluetoothAdapter.STATE_TURNING_OFF:
                  stringState = "turning_off";
                  break;
               case BluetoothAdapter.STATE_ON:
                  stringState = "on";
                  break;
               case BluetoothAdapter.STATE_TURNING_ON:
                  stringState = "turning_on";
                  break;
            }
         }

         WritableMap map = Arguments.createMap();
         map.putString("state", stringState);
         Log.d(LOG_TAG, "state: " + stringState);
         sendEvent("BleManagerDidUpdateState", map);
      }
   };

   @ReactMethod
   public void getDiscoveredPeripherals(Callback callback) {
      Log.d(LOG_TAG, "Get discovered peripherals");
      WritableArray map = Arguments.createArray();
      BundleJSONConverter bjc = new BundleJSONConverter();
      for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
         Map.Entry<String, Peripheral> entry = iterator.next();
         Peripheral peripheral = entry.getValue();
         try {
            Bundle bundle = bjc.convertToBundle(peripheral.asJSONObject());
            WritableMap jsonBundle = Arguments.fromBundle(bundle);
            map.pushMap(jsonBundle);
         } catch (JSONException ignored) {
            try { callback.invoke("Peripheral json conversion error", null); }
            catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         }
      }
      try { callback.invoke(null, map); }
      catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
   }

   @ReactMethod
   public void getConnectedPeripherals(String peripheralId, ReadableArray serviceUUIDs, Callback callback) {
       Log.d(LOG_TAG, "Get connected peripherals");
       WritableArray map = Arguments.createArray();
       BundleJSONConverter bjc = new BundleJSONConverter();
       for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
          Map.Entry<String, Peripheral> entry = iterator.next();
          Peripheral peripheral = entry.getValue();
          Boolean accept = false;

          System.out.println("uuidddddddddddddddddddddddddddddddddd");
          System.out.println(serviceUUIDs);

          if (serviceUUIDs.size() > 0) {
             for (int i = 0; i < serviceUUIDs.size(); i++) {
                accept = peripheral.hasService(UUIDHelper.uuidFromString(serviceUUIDs.getString(i)));
                System.out.println("accepttttttttttttttttttttttttttttttttttttttttttttttttttt");
                System.out.println(accept);
                System.out.println(serviceUUIDs.getString(i));
                System.out.println(UUIDHelper.uuidFromString(serviceUUIDs.getString(i)));
             }
          } else {
             accept = true;
          }

          if (peripheral.isConnected() && accept) {
             try {
                System.out.println("Bundleeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
                System.out.println(peripheral.isConnected());
                Bundle bundle = bjc.convertToBundle(peripheral.asJSONObject());
                WritableMap jsonBundle = Arguments.fromBundle(bundle);
                map.pushMap(jsonBundle);
                System.out.println(map);
             } catch (JSONException ignored) {
                callback.invoke("Peripheral json conversion error", null);
             }
          } else {
              System.out.println("Peripheral is not connecteddddddddddddddddddddddddddddddd " + peripheralId);

              NotificationManager notificationManager=(NotificationManager)
                               context.getSystemService(Context.NOTIFICATION_SERVICE);

             Notification notify=new Notification.Builder(context.getApplicationContext())
                             .setContentTitle("Bluetooth Disconnected")
                             .setContentText("The bluetooth button has been disconnected")
                             .setContentTitle("Device Disconnected").setSmallIcon(R.drawable.notification_icon).build();

               //  Intent notificationIntent = new Intent(context, MainActivity.class);
               //  notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
               //  PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
               //  notify.setLatestEventInfo(context, "Bluetooth Disconnected", "The bluetooth button has been disconnected", intent);

               notify.flags |= Notification.FLAG_AUTO_CANCEL;
               notificationManager.notify(0, notify);






          }
       }
       callback.invoke(null, map);
   }

   @ReactMethod
   public void upgradeFirmware(String firmwareUrl, String deviceUUID, Callback callback) {
      DfuServiceListenerHelper.registerProgressListener(reactContext.getCurrentActivity(), mDfuProgressListener);
      // Temporary filename
      File file;
      String mFilePath = "";
      Uri mFileStreamUri = null;

      try {
         String fileName = Uri.parse(firmwareUrl).getLastPathSegment();
         file = File.createTempFile(fileName, null, context.getCacheDir());
         downloadFile(firmwareUrl, file);

         mFilePath = file.getPath();
         mFileStreamUri = Uri.fromFile(file);
         System.out.println("Total space of file: " + file.getTotalSpace() + " / " + file.getName() + " / " + file.getPath());

      } catch (IOException e) {
        // Error while creating file
            System.out.println(e);
    }
      System.out.println("Initializing starter with id " + deviceUUID);
      final DfuServiceInitiator starter = new DfuServiceInitiator(deviceUUID)
            // .setDeviceName(mSelectedDevice.getName())
            .setDeviceName("Markus")
            .setKeepBond(true)
            .setForceDfu(true)
            .setPacketsReceiptNotificationsEnabled(false)
            .setPacketsReceiptNotificationsValue(DfuServiceInitiator.DEFAULT_PRN_VALUE)
            .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);

      starter.setZip(mFileStreamUri, mFilePath);
      System.out.println("Start!");
      starter.start(context, DfuService.class);
      System.out.println("Finished!");
      callback.invoke(null, "Success");
   }

   private void downloadFile(String firmwareUrl, File file){
      try {
            URL url = new URL(firmwareUrl);

            long startTime = System.currentTimeMillis();
            Log.d("ImageManager", "download begining");
            Log.d("ImageManager", "download url:" + url);
            /* Open a connection to that URL. */
            URLConnection ucon = url.openConnection();

            /*
               * Define InputStreams to read from the URLConnection.
               */
            InputStream inputStream = ucon.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(file);

            byte data[] = new byte[102400];
            long total = 0;
            int count;
            while ((count = inputStream.read(data)) != -1) {
                  outputStream.write(data, 0, count);
            }
            // flushing output
            outputStream.flush();
            // closing streams
            outputStream.close();
            inputStream.close();

            Log.d("ImageManager", "download ready in"
                        + ((System.currentTimeMillis() - startTime) / 1000)
                        + " sec");

      } catch (IOException e) {
                  Log.d("ImageManager", "Error: " + e);
      }
   }

   private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

   public static String bytesToHex(byte[] bytes) {
      char[] hexChars = new char[bytes.length * 2];
      for (int j = 0; j < bytes.length; j++) {
         int v = bytes[j] & 0xFF;
         hexChars[j * 2] = hexArray[v >>> 4];
         hexChars[j * 2 + 1] = hexArray[v & 0x0F];
      }
      return new String(hexChars);
   }

   @Override
   public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
      Log.d(LOG_TAG, "onActivityResult");
      if (requestCode == ENABLE_REQUEST && enableBluetoothCallback != null) {
         if (resultCode == RESULT_OK) {
            try { enableBluetoothCallback.invoke(); }
            catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         } else {
            try { enableBluetoothCallback.invoke("User refused to enable"); }
            catch(Exception invokeExceptionError) { Log.d(LOG_TAG, "Failed to invoke callback! :("); }
         }
         enableBluetoothCallback = null;
      }
   }

   @Override
   public void onNewIntent(Intent intent) {

   }

}