package com.transvision.test_gpt_printer.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.lvrenyang.io.BTPrinting;
import com.lvrenyang.io.Canvas;
import com.lvrenyang.io.IOCallBack;
import com.lvrenyang.io.Pos;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.transvision.test_gpt_printer.extra.Constants.BLUETOOTH_RESULT;
import static com.transvision.test_gpt_printer.extra.Constants.CONNECTED;
import static com.transvision.test_gpt_printer.extra.Constants.DISCONNECTED;
import static com.transvision.test_gpt_printer.extra.Constants.DEVICE_NOT_PAIRED;
import static com.transvision.test_gpt_printer.extra.Constants.DEVICE_PAIRED;
import static com.transvision.test_gpt_printer.extra.Constants.PRINTER_CONNECTED;
import static com.transvision.test_gpt_printer.extra.Constants.PRINTER_DISCONNECTED;
import static com.transvision.test_gpt_printer.extra.Constants.PRINTER_MSG;

public class BluetoothService extends Service implements IOCallBack {

    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice bluetoothDevice;
    BTPrinting mBt = new BTPrinting();
    public static Canvas mCanvas = new Canvas();
    public static Pos mPos = new Pos();
    public static ExecutorService es = Executors.newScheduledThreadPool(30);
    BluetoothService mActivity;

    public static boolean printerconnected = false;
    String printer_address;
    private boolean broadcast = false, paired = false;

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Intent intent = new Intent();
            switch (msg.what) {
                case PRINTER_CONNECTED:
                    printerconnected = true;
                    printer_address = bluetoothDevice.getAddress();
                    logStatus("Printer Connected");
                    intent.setAction(BLUETOOTH_RESULT);
                    intent.putExtra(PRINTER_MSG, CONNECTED);
                    sendBroadcast(intent);
                    break;

                case PRINTER_DISCONNECTED:
                    printerconnected = false;
                    logStatus("Printer Disconnected");
                    intent.setAction(BLUETOOTH_RESULT);
                    intent.putExtra(PRINTER_MSG, DISCONNECTED);
                    sendBroadcast(intent);
                    break;

                case DEVICE_PAIRED:
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            es.submit(new TaskOpen(mBt, bluetoothDevice.getAddress(), mActivity));
                        }
                    }, 1000);
                    break;

                case DEVICE_NOT_PAIRED:
                    logStatus("Device not Paired.. Starting Broadcast...");
                    startBroadcast();
                    break;

                default:
                    break;
            }
            return false;
        }
    });

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void logStatus(String msg) {
        Log.d("debug", msg);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mActivity = this;
        mPos.Set(mBt);
        mCanvas.Set(mBt);
        mBt.SetCallBack(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        getPairedDevices();

        return START_STICKY;
    }

    private void getPairedDevices() {
        Set<BluetoothDevice> pairedDevice = mBluetoothAdapter.getBondedDevices();
        if (pairedDevice.size() > 0) {
            try {
                for (BluetoothDevice device : pairedDevice) {
                    if (device.getName().equals("BP301-2")) {
                        bluetoothDevice = device;
                        paired = true;
                        break;
                    }
                }
                if (paired)
                    handler.sendEmptyMessage(DEVICE_PAIRED);
                else handler.sendEmptyMessage(DEVICE_NOT_PAIRED);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else handler.sendEmptyMessage(DEVICE_NOT_PAIRED);
    }

    private void startBroadcast() {
        if (!broadcast) {
            broadcast = true;
            mBluetoothAdapter.startDiscovery();
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(mReceiver, filter);
        }
    }

    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    logStatus("ACTION_FOUND_PAIRED: "+device.getName());
                    if (device.getName().equals("BP301-2")) {
                        bluetoothDevice = device;
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                es.submit(new TaskOpen(mBt, bluetoothDevice.getAddress(), mActivity));
                            }
                        }, 500);
                    }
                } else logStatus("ACTION_FOUND_UNPAIRED: "+device.getName());
            }
            else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                logStatus("ACTION_CONNECTED: "+device.getName());
                handler.sendEmptyMessage(PRINTER_CONNECTED);
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                logStatus("ACTION_DISCOVERY_FINISHED");
                if (!printerconnected)
                    mBluetoothAdapter.startDiscovery();
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                logStatus("ACTION_DISCOVERY_STARTED");
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                logStatus("ACTION_DISCONNECTED: "+device.getName());
                handler.sendEmptyMessage(PRINTER_DISCONNECTED);
                mBluetoothAdapter.startDiscovery();
            }
            else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    logStatus("Paired Device: "+device.getName());
                }
            }
        }
    };

    @Override
    public void OnOpen() {
        handler.sendEmptyMessage(PRINTER_CONNECTED);
    }

    @Override
    public void OnOpenFailed() {
        handler.sendEmptyMessage(PRINTER_DISCONNECTED);
    }

    @Override
    public void OnClose() {
        handler.sendEmptyMessage(PRINTER_DISCONNECTED);
    }

    private class TaskOpen implements Runnable {
        BTPrinting bt;
        String address;
        Context context;

        private TaskOpen(BTPrinting bt, String address, Context context) {
            this.bt = bt;
            this.address = address;
            this.context = context;
        }

        @Override
        public void run() {
            bt.Open(address, context);
        }
    }

    private class TaskClose implements Runnable {
        BTPrinting bt;

        private TaskClose(BTPrinting bt) {
            this.bt = bt;
        }

        @Override
        public void run() {
            bt.Close();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        es.submit(new TaskClose(mBt));
        if (broadcast)
            unregisterReceiver(mReceiver);
        handler.removeCallbacksAndMessages(null);
    }
}
