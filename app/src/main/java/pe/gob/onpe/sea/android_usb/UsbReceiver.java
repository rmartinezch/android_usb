package pe.gob.onpe.sea.android_usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

import com.bosphere.filelogger.FL;

public class UsbReceiver extends BroadcastReceiver {
    private static final String TAG = UsbReceiver.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.demo.otgusb.USB_PERMISSION";

    @Override
    public void onReceive(Context context, Intent intent) {
        FL.i(TAG, "onReceive");
        String action = intent.getAction();
        if (action == null)
            return;
        switch (action) {
            case ACTION_USB_PERMISSION://User Authorized Broadcast
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) { //Allow permission to apply
                        FL.i(TAG, "intent ok!");
//                        test();
                    } else {
                        FL.e(TAG, "User is not authorized, access to USB device failed");
                    }
                }
                break;
            case UsbManager.ACTION_USB_DEVICE_ATTACHED://USB device plugged into the broadcast
                FL.i(TAG, "USB device plugin");
                break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED://USB device unplugs the broadcast
                FL.i(TAG, "USB device unplugged");
                break;
        }
    }
}