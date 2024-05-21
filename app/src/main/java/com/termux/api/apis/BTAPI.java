package com.termux.api.apis;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.JsonWriter;

import androidx.annotation.Nullable;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BTAPI {

    private static final String LOG_TAG = "BTAPI";
    static List < BluetoothDevice > devices = new ArrayList<>();

    public static String getDeviceType(BluetoothDevice device) {
        BluetoothClass bluetoothClass = device.getBluetoothClass();
        if (bluetoothClass != null) {
            switch (bluetoothClass.getMajorDeviceClass()) {
                case BluetoothClass.Device.Major.AUDIO_VIDEO:
                    return "av_device";
                case BluetoothClass.Device.Major.COMPUTER:
                    return "computer";
                case BluetoothClass.Device.Major.PHONE:
                    return "phone";
                case BluetoothClass.Device.Major.IMAGING:
                    return "imaging";
                case BluetoothClass.Device.Major.NETWORKING:
                    return "networking";
                case BluetoothClass.Device.Major.PERIPHERAL:
                    switch (bluetoothClass.getDeviceClass()) {
                        case BluetoothClass.Device.COMPUTER_DESKTOP:
                            return "computer_desktop";
                        case BluetoothClass.Device.COMPUTER_LAPTOP:
                            return "laptop";
                        case BluetoothClass.Device.PHONE_CELLULAR:
                            return "phone_cellular";
                        case BluetoothClass.Device.PHONE_SMART:
                            return "smart_phone";
                        case 0x540:
                            return "keyboard";
                        case 0x580:
                            return "mouse";
                        case 0x5c0:
                            return "keyboard/mouse";
                        case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                            return "headphones";
                        case BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE:
                            return "microphone";
                        case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                            return "loudspeaker";
                        case BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED:
                            return "av_device";
                        default:
                            return "peripheral_device_" + String.valueOf(bluetoothClass.getDeviceClass());
                    }
                default:
                    return "unknown_device_" + String.valueOf(bluetoothClass.getMajorDeviceClass());
            }
        }
        return "unknown";
    }

    public static class BTScanService extends Service {
        private static final String LOG_TAG = "BTScanService";
        private BroadcastReceiver receiver;
        private BluetoothAdapter bluetoothAdapter;
        private int waitTime = 12000;

        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

            if (intent != null && intent.hasExtra("wt")) {
                waitTime = intent.getIntExtra("wt", 12000);
            }

            devices.clear();

            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            registerReceiver();
            startDiscovery();

            return Service.START_STICKY;
        }

        private void registerReceiver() {
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        devices.add(device);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(receiver, filter);
        }

        private void startDiscovery() {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.startDiscovery();
                new Handler().postDelayed(() -> {
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    stopSelf();
                }, waitTime);
            }
        }

        @Override
        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");
            if (receiver != null) {
                unregisterReceiver(receiver);
            }
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    public static void onReceiveBTConnect(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveBluetoothConnect");
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                BluetoothManager manager = (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                final BluetoothAdapter bluetoothAdapter = manager.getAdapter();
                BluetoothDevice device;;

                if (bluetoothAdapter == null) {
                  out.beginObject().name("API_ERROR").value("Device does not support Bluetooth").endObject();
                }

                if (bluetoothAdapter.isDiscovering()){
                    bluetoothAdapter.cancelDiscovery();
                }

                if (!bluetoothAdapter.isEnabled()) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        out.beginObject().name("API_ERROR").value("Enable bluetooth to connect to a device").endObject(); // we can't use the prompt to enable bluetooth because the user may cancel it
                    } else {
                        bluetoothAdapter.enable();
                    }
                }

                String deviceAddress = intent.getStringExtra("addr");

                try {
                    device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                } catch (IllegalArgumentException e) {
                    out.beginObject().name("API_ERROR").value(deviceAddress + " is not a valid Bluetooth address").endObject();
                }

                if (device == null) {
                    out.beginObject().name("API_ERROR").value(deviceAddress + " does not exist").endObject();
                } else {
                    ParcelUuid[] uuids = device.getUuids();
                    List <BluetoothSocket> sockets = new ArrayList<>();

                    if (uuids != null) {
                        for (ParcelUuid uuid_ : uuids) {
                            UUID uuid = uuid_.getUuid();
                            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid);
                            if (socket != null) {
                                sockets.add(socket);
                            }
                        }
                    }
                      
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        device.createBond();
                    }

                    for (BluetoothSocket socket : sockets) {
                        try {
                            socket.connect();
                        } catch (IOException e) {
                            Logger.logStackTraceWithMessage(LOG_TAG, "Error posting result", e);
                        }
                    }
                }
            }
        });
    }
    
    public static void onReceiveBTScanInfo(final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveBluetoothScanInfo");
        ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                BluetoothManager manager = (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                final BluetoothAdapter bluetoothAdapter = manager.getAdapter();

                int waitTime = intent.getIntExtra("wt", 12000);

                if (bluetoothAdapter == null) {
                    out.beginObject().name("API_ERROR").value("Device does not support Bluetooth").endObject();
                } else {
                    if (bluetoothAdapter.isEnabled()) {
                        Intent newIntent = new Intent(context, BTScanService.class);
                        Bundle extras = intent.getExtras();
                        if (extras != null) {
                            newIntent.putExtras(extras);
                        }

                        context.startService(newIntent);
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException e) {
                            Logger.logStackTraceWithMessage(LOG_TAG, "Error posting result", e);
                        }
                        out.beginArray();
                        for (BluetoothDevice device: devices) {
                            out.beginObject();
                            out.name("name").value(device.getName());
                            out.name("address").value(device.getAddress());
                            out.name("alias").value(device.getAlias());
                            out.name("device_type").value(getDeviceType(device));
                            out.endObject();
                        }
                        out.endArray();
                    } else {
                        out.beginArray().endArray();
                    }
                }
            }
        });
    }

    public static void onReceiveBTConnectionInfo(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveBluetoothConnectionInfo");
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                BluetoothManager manager = (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                final BluetoothAdapter bluetoothAdapter = manager.getAdapter();
                List < BluetoothDevice > connectedDevices = new ArrayList < > ();
                if (bluetoothAdapter == null) {
                    out.beginObject().name("API_ERROR").value("Device does not support Bluetooth").endObject();
                }

                for (int profile: new int[] {
                        BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.GATT
                    }) {
                    bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                        @Override
                        public void onServiceConnected(int profile, BluetoothProfile proxy) {
                            List < BluetoothDevice > devices = proxy.getConnectedDevices();
                            connectedDevices.addAll(devices);
                        }

                        @Override
                        public void onServiceDisconnected(int profile) {

                        }
                    }, profile);
                }

                out.beginArray();
                for (BluetoothDevice device: connectedDevices) {
                    out.beginObject();
                    out.name("name").value(device.getName());
                    out.name("address").value(device.getAddress());
                    out.name("alias").value(device.getAlias());
                    out.name("device_type").value(getDeviceType(device));
                    out.endObject();
                }
                out.endArray();
            }
        });
    }

    public static void onReceiveBTListPairedDevicesInfo(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveBluetoothListPairedDevicesInfo");
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                BluetoothManager manager = (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                final BluetoothAdapter bluetoothAdapter = manager.getAdapter();
                if (bluetoothAdapter == null) {
                    out.beginObject().name("API_ERROR").value("Device does not support Bluetooth").endObject();
                }

                if (!bluetoothAdapter.isEnabled()) {
                    out.beginObject().name("API_ERROR").value("Bluetooth needs to be enabled on this device").endObject();
                } else {
                    Set < BluetoothDevice > pairedDevices = bluetoothAdapter.getBondedDevices();
                    out.beginArray();
                    for (BluetoothDevice device: pairedDevices) {
                        out.beginObject();
                        out.name("name").value(device.getName());
                        out.name("address").value(device.getAddress());
                        out.name("alias").value(device.getAlias());
                        out.name("device_type").value(getDeviceType(device));
                        out.endObject();
                    }
                    out.endArray();
                }
            }
        });
    }

    public static void onReceiveBTEnable(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveBluetoothEnable");
        boolean enabled = intent.getBooleanExtra("enabled", false);
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                BluetoothManager manager = (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                final BluetoothAdapter bluetoothAdapter = manager.getAdapter();

                if (bluetoothAdapter == null) {
                    out.beginObject().name("API_ERROR").value("Device does not support Bluetooth").endObject();
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    if (enabled) {
                        bluetoothAdapter.enable();
                    } else {
                        bluetoothAdapter.disable();
                    }
                } else {
                    if (enabled) {
                        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        context.startActivity(intent);
                    } else {
                        // TODO: no possible way to disable bluetooth on Android 13+ (https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#disable() deprecation on API level 33)
                    }
                }
            }
        });
    }
}
