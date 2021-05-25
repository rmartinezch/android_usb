package pe.gob.onpe.sea.android_usb;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bosphere.filelogger.FL;
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileStreamFactory;
import com.github.mjdev.libaums.partition.Partition;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "pe.gob.onpe.sea.USB_PERMISSION";
    private final int REQUEST_PERMISSION_RW_USB = 100;
    private UsbManager usbManager;
    private PendingIntent permissionIntent;

    Button btnSave, btnLoad;
    EditText etInput;
    TextView tvLoad;
    String fileName = "";
    String filePath = "";
    String fileContent = "";
    StringBuilder str = new StringBuilder();

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            FL.i(TAG, "|-----onReceive");
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            FL.i(TAG, "Here the user already granted the permission");
                            test();
                        }
                    }
                    else {
                        FL.i(TAG, "permission denied for device " + device);
                    }
                }
            } else if (ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                FL.i(TAG, "USB device plugin");
            } else if (ACTION_USB_DEVICE_DETACHED.equals(action)) {
                FL.i(TAG, "USB device unplugged");
            }
            FL.i(TAG, "onReceive-----|");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FL.i(TAG, "onCreate");
        FL.i(TAG, "App version: " + getAppVersion());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnLoad = findViewById(R.id.btnLoad);
        btnSave = findViewById(R.id.btnSave);
        etInput = findViewById(R.id.etInput);
        tvLoad = findViewById(R.id.tvLoad);
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(getAppVersion());
        fileName = "myFile.txt";
        filePath = "MyFileDir";
        if (!isExternalStorageAvailableForRW()) {
            btnSave.setEnabled(false);
        }

        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        checkPermission(permissions, REQUEST_PERMISSION_RW_USB);

        registerBroadcastReceiver(mUsbReceiver);
        FL.i(TAG, "after init");

        // https://developer.android.com/training/data-storage/app-specific#java
        File[] externalStorageVolumes = ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        FL.i(TAG, Arrays.toString(externalStorageVolumes));
        tvLoad.setText(Arrays.toString(externalStorageVolumes));
        // Memory of the smartphone by default and could have a sd card memory
        String externalStorageVolume;
        if (externalStorageVolumes.length == 2) {
            externalStorageVolume = externalStorageVolumes[1].getAbsolutePath();
        } else {
            externalStorageVolume = externalStorageVolumes[0].getAbsolutePath();
        }

        btnSave.setOnClickListener(v -> manageUsbDevice());
//        btnSave.setOnClickListener(v -> writeSDCard(externalStorageVolume));
        String finalExternalStorageVolume = externalStorageVolume;
        btnLoad.setOnClickListener(v -> readSDCard(finalExternalStorageVolume));
    }

    private boolean isExternalStorageAvailableForRW() {
        String externalStorageState = Environment.getExternalStorageState();
        FL.i(TAG, "externalStorageState: " + externalStorageState);
        return externalStorageState.equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * Function to check and request permission
     * https://www.geeksforgeeks.org/android-how-to-request-permissions-in-android-application/
     * @param permission Array of strings which represents to the permissions for manage external storage
     * @param requestCode It represents the code linked to the permissions request to verification
     */
    public void checkPermission(String[] permission, int requestCode) {
        // Checking if permission is not granted
        if (    (ContextCompat.checkSelfPermission(MainActivity.this, permission[0]) == PackageManager.PERMISSION_DENIED) ||
                (ContextCompat.checkSelfPermission(MainActivity.this, permission[1]) == PackageManager.PERMISSION_DENIED) ) {
            requestPermissions(permission, requestCode);
        }
        else {
            FL.i(TAG, "Permission already granted: " + Arrays.toString(permission));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull @NotNull String[] permissions, @NonNull @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        FL.i(TAG, "requestCode: " + requestCode + ", permissions: " + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        if (requestCode == REQUEST_PERMISSION_RW_USB) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                FL.i(TAG, "Read/Write usb permission granted...");
            } else {
                FL.w(TAG, "Read/Write usb permission denied...");
            }
        } else {
            FL.w(TAG, "Do nothing");
        }
    }

    private void writeSDCard(String externalStorageVolume) {
        tvLoad.setText("");
        fileContent = etInput.getText().toString().trim();
        if (!fileContent.equals("")) {
            String directory = externalStorageVolume + File.separator + filePath;
            File myExternalFolder = new File(directory);
            if (!myExternalFolder.exists()) {
                if(!myExternalFolder.mkdir()) {
                    FL.e(TAG, "Directory wasn't created: " + directory);
                    return;
                }
                FL.w(TAG, "Created directory: " + myExternalFolder.getAbsolutePath());
            }
            File myExternalFile = new File(directory, fileName);
            FL.i(TAG, "File to be written: " + myExternalFile.getAbsolutePath());
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(myExternalFile);
                fos.write(fileContent.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            etInput.setText("");
            Toast.makeText(MainActivity.this, getString(R.string.message_data_saved), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(MainActivity.this, getString(R.string.message_data_error), Toast.LENGTH_LONG).show();
        }
    }

    private void readSDCard(String externalStorageVolume) {
        FileReader fr;
        String myExternalFolder = externalStorageVolume + File.separator + filePath;
        File myExternalFile = new File(myExternalFolder, fileName);
        FL.i(TAG, "File to be read: " + myExternalFile.getAbsolutePath());
        StringBuilder stringBuilder = new StringBuilder();
        try {
            fr = new FileReader(myExternalFile);
            BufferedReader br = new BufferedReader(fr);
            String line = br.readLine();
            while (line != null) {
                stringBuilder.append(line).append("\n");
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            String fileContents = "El archivo contiene:\n" + stringBuilder.toString();
            tvLoad.setText(fileContents);
        }
    }

    private void manageUsbDevice() {
        FL.i(TAG, "|-----manageUsbDevice");
        UsbDevice[] devices = enumerateUsbDevices();
        if (devices == null || devices.length == 0) {
            FL.w(TAG, "No usb devices detected");
            return;
        }
        // select which device we choose and request the permission to communicate
        usbManager.requestPermission(devices[0], permissionIntent);
        FL.i(TAG, "manageUsbDevice-----|");
    }

    /**
     * This function can be used before or after init function
     * But this one must be used before of the App request permission to communicate with the usb device, which one is previously identified
     * The device used here is the last one identified
     */
    private UsbDevice[] enumerateUsbDevices() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        if (deviceList.size() == 0) { return null; }
        UsbDevice[] devices = new UsbDevice[deviceList.size()];
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        StringBuilder str = new StringBuilder();
        int i = 0;
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            devices[i] = device;
            String out =    "DeviceName: " + device.getDeviceName() + "\n" +
                            "DeviceId: " + device.getDeviceId() + "\n" +
                            "ManufacturerName: " + device.getManufacturerName() + "\n" +
                            "ProductName: " + device.getProductName() + "\n" +
                            "SerialNumber: " + device.getSerialNumber();
            FL.i(TAG, out);
            str.append(out).append("\n\n");
        }
        tvLoad.setText(str.toString());
        return devices;
    }

    /**
     * To register the intents or events in the broadcast receiver
     * @param usbReceiver This class manage the events around the usb device like plugged, unplugged or even, detect the granted permission to communicate
     */
    private void registerBroadcastReceiver(BroadcastReceiver usbReceiver) {
        FL.i(TAG, "|-----init");
        //USB Manager
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        // Register the broadcast, monitor USB plug and pull out
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, intentFilter);
        FL.i(TAG, "init-----|");
    }

    private void test() {
        try {
            UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);
            for (UsbMassStorageDevice storageDevice : storageDevices) { //The general mobile phone has only one USB device
                // Apply for USB permissions
                if (!usbManager.hasPermission(storageDevice.getUsbDevice())) {
                    usbManager.requestPermission(storageDevice.getUsbDevice(), permissionIntent);
                    break;
                }
                // Initialize
                storageDevice.init();
                // Get the partition
                List<Partition> partitions = storageDevice.getPartitions();
                if (partitions.size() == 0) {
                    str.append("Error: Failed to read partition");
                    return;
                }
                // use only the first partition
                FileSystem fileSystem = partitions.get(0).getFileSystem();
                str.append("Volume Label: ").append(fileSystem.getVolumeLabel());
                str.append("Capacity: ").append(fSize(fileSystem.getCapacity()));
                str.append("Occupied Space: ").append(fSize(fileSystem.getOccupiedSpace()));
                str.append("Free Space: ").append(fSize(fileSystem.getFreeSpace()));
                str.append("Chunk size: ").append(fSize(fileSystem.getChunkSize()));

                UsbFile root = fileSystem.getRootDirectory();
                UsbFile[] files = root.listFiles();
                for (UsbFile file : files)
                    str.append("file: ").append(file.getName());

                // create a new file
                UsbFile newFile = root.createFile("hello_" + System.currentTimeMillis() + ".txt");
                str.append("New file: ").append(newFile.getName());

                // write the file
                // OutputStream os = new UsbFileOutputStream(newFile);
                OutputStream os = UsbFileStreamFactory.createBufferedOutputStream(newFile, fileSystem);
                os.write(("hi_" + System.currentTimeMillis()).getBytes());
                os.close();
                str.append("write file: ").append(newFile.getName());

                // read the file
                // InputStream is = new UsbFileInputStream(newFile);
                InputStream is = UsbFileStreamFactory.createBufferedInputStream(newFile, fileSystem);
                byte[] buffer = new byte[fileSystem.getChunkSize()];
                int len;
//                File sdFile = new File("/sdcard/111");
                File sdFile = new File(Environment.getExternalStorageDirectory().getPath() + "/111");
//                sdFile.mkdirs();
                FileOutputStream sdOut = new FileOutputStream(sdFile.getAbsolutePath() + "/" + newFile.getName());
                while ((len = is.read(buffer)) != -1) {
                    sdOut.write(buffer, 0, len);
                }
                is.close();
                sdOut.close();
                str.append("Read file: ").append(newFile.getName()).append(" -> copy to /sdcard/111/");

                storageDevice.close();
            }
        } catch (Exception e) {
            str.append("Error: ").append(e);
        }
        tvLoad.setText(str.toString());
    }

    public static String fSize(long sizeInByte) {
        if (sizeInByte < 1024)
            return String.format("%s", sizeInByte);
        else if (sizeInByte < 1024 * 1024)
            return String.format(Locale.CANADA, "%.2fKB", sizeInByte / 1024.);
        else if (sizeInByte < 1024 * 1024 * 1024)
            return String.format(Locale.CANADA, "%.2fMB", sizeInByte / 1024. / 1024);
        else
            return String.format(Locale.CANADA, "%.2fGB", sizeInByte / 1024. / 1024 / 1024);
    }

    /**
     * Get the App version from the current App
     * @return App version in string format
     */
    private String getAppVersion() {
        String version = "0.0.0";
        try {
            PackageInfo pInfo = getBaseContext().getPackageManager().getPackageInfo(getBaseContext().getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            FL.e(TAG, "Can't get app version: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        return version;
    }
}