package pe.gob.onpe.sea.android.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
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

import pe.gob.onpe.sea.android.R;

import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED;
import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "pe.gob.onpe.sea.USB_PERMISSION";
    private static final int WRONG_VOLUME_INDEX = -1;

    private UsbManager usbManager;
    private PendingIntent permissionIntent;

    private EditText etInput;
    private EditText etVolume;
    private TextView tvLoad;
    private String fileName = "";
    private String fileContent = "";
    private int index = 0;
    private final StringBuilder str = new StringBuilder();

    /**
     * If the permission is granted, it detects the events related to the usb drive
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            FL.i(TAG, "BroadcastReceiver");
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            FL.i(TAG, "The user granted the permission");
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
            } else {
                FL.i(TAG, "ACTION_USB_PERMISSION is not equal to action");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FL.i(TAG, "onCreate");
        FL.i(TAG, "App version: " + getAppVersion());
        FL.i(TAG, "Equipment info: " + getInfoFromEquipment());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btnLoad = findViewById(R.id.btnLoad);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnExternal = findViewById(R.id.btnExternal);
        etInput = findViewById(R.id.etInput);
        etVolume = findViewById(R.id.etVolume);
        tvLoad = findViewById(R.id.tvLoad);
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(getAppVersion());
        fileName = "myFile.txt";
        if (!isExternalStorageAvailableForRW()) {
            btnSave.setEnabled(false);
        }

//        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
//        checkPermission(permissions, REQUEST_PERMISSION_RW_USB);

        registerBroadcastReceiver(mUsbReceiver);

        String externalStorageVolume = getExternalVolume(Integer.parseInt(etVolume.getText().toString()));
        FL.i(TAG, "Default selected volume: " + externalStorageVolume);

        btnExternal.setOnClickListener(v -> manageUsbDevice());
        btnSave.setOnClickListener(v -> saveInSDCard());
        btnLoad.setOnClickListener(v -> loadFromSDCard());
    }

    /**
     * Verify EditText objects aren't empty and get the index from de EditText
     * @return True if the verification is ok, otherwise false
     */
    private Boolean verifyInputText() {
        index = WRONG_VOLUME_INDEX;
        String sIndex = etVolume.getText().toString().trim();
        fileContent = etInput.getText().toString().trim();
        if (sIndex.isEmpty() || fileContent.isEmpty()) {
            Toast.makeText(MainActivity.this, getString(R.string.message_data_error), Toast.LENGTH_LONG).show();
            FL.e(TAG, "At least, one EditText object is empty");
            return false;
        }
        if (0 <= Integer.parseInt(sIndex)) {
            index = Integer.parseInt(sIndex);
            FL.i(TAG, "Volume index: " + index);
            return true;
        }
        else {
            FL.e(TAG, "Invalid volume index !!!");
            return false;
        }
    }

    /**
     * This function detects the mounted external storages
     * @return True if the external storages is mounted, otherwise false
     */
    private boolean isExternalStorageAvailableForRW() {
        String externalStorageState = Environment.getExternalStorageState();
        FL.i(TAG, "External storage state: " + externalStorageState);
        return externalStorageState.equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * Function to check and request permission
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

    /**
     * https://developer.android.com/training/data-storage/app-specific#java
     * @param i index of external volume list
     * @return path of the volume at the selected index
     */
    private String getExternalVolume(int i) {
        if (i == -1) return "";
        File[] externalStorageVolumes = ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        StringBuilder list = new StringBuilder();
        list.append("Detected volumes:\n");
        for(int k = 0; k < externalStorageVolumes.length; k++) {
            list.append(k).append(": ").append(externalStorageVolumes[k].getAbsolutePath()).append("\n");
        }
        FL.i(TAG, list.toString());
        tvLoad.setText(list.toString());
        if (i < externalStorageVolumes.length) {
            String currentVolume = externalStorageVolumes[i].getAbsolutePath() + File.separator + Environment.DIRECTORY_DOCUMENTS;
            FL.i(TAG, "Current selected volume: " + currentVolume);
            return currentVolume;
        } else {
            Toast.makeText(MainActivity.this, getString(R.string.message_index_error), Toast.LENGTH_LONG).show();
            return "";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull @NotNull String[] permissions, @NonNull @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        FL.i(TAG, "requestCode: " + requestCode + ", permissions: " + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        int REQUEST_PERMISSION_RW_USB = 100;
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

    /**
     * Save the data from EditText object into a file in the selected volume
     */
    private void saveInSDCard() {
        if (!verifyInputText()) { return; }

        String externalVolume = getExternalVolume(index);
        File myExternalFolder = new File(externalVolume);
        if (!myExternalFolder.exists()) {
            FL.w(TAG, "External folder doesn't exists: " + myExternalFolder.getAbsolutePath());
            if(!myExternalFolder.mkdir()) {
                FL.e(TAG, "External folder wasn't created: " + myExternalFolder.getAbsolutePath());
                return;
            }
            FL.i(TAG, "Created folder: " + myExternalFolder.getAbsolutePath());
        }
        File myExternalFile = new File(myExternalFolder, fileName);
        FL.i(TAG, "File to be written: " + myExternalFile.getAbsolutePath());
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(myExternalFile);
            fos.write(fileContent.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        tvLoad.setText("");
        etInput.setText("");

        Toast.makeText(MainActivity.this, getString(R.string.message_data_saved), Toast.LENGTH_LONG).show();
    }

    /**
     * Read the data from the selected volume
     */
    private void loadFromSDCard() {

        String externalStorageVolume = getExternalVolume(index);
        File myExternalFolder = new File(externalStorageVolume);
        if (!myExternalFolder.exists()) {
            FL.e(TAG, "Folder doesn't exists: " + myExternalFolder.getAbsolutePath());
            return;
        }
        FileReader fr;
        File myExternalFile = new File(myExternalFolder, fileName);
        FL.i(TAG, "File to be read: " + myExternalFile.getAbsolutePath());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("File contents:\n");
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
            FL.i(TAG, stringBuilder.toString());
            tvLoad.setText(stringBuilder.toString());
        }
    }

    /**
     * If the function detects a plugged usb drive, it sends a permission request
     */
    private void manageUsbDevice() {
        FL.i(TAG, "manageUsbDevice");
        UsbDevice[] devices = enumerateUsbDevices();
        if (devices == null || devices.length == 0) {
            FL.w(TAG, "No usb devices detected, permission request wasn't sent");
            return;
        }
        // select which device we choose and request the permission to communicate
        FL.i(TAG, "At least, one usb device was detected");
        usbManager.requestPermission(devices[0], permissionIntent);
        FL.i(TAG, "Permission request is sent to the user");
    }

    /**
     * https://developer.android.com/guide/topics/connectivity/usb/host
     * This one must be used before of the permission request will be sent
     * The device used here is the last one identified inside the loop
     */
    private UsbDevice[] enumerateUsbDevices() {
        FL.i(TAG, "enumerateUsbDevices");
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        if (deviceList.size() == 0) {
            FL.e(TAG, "Device list zero");
            return null;
        }
        UsbDevice[] devices = new UsbDevice[deviceList.size()];
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        StringBuilder str = new StringBuilder();
        int i = 0;
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            devices[i++] = device;
            String out =    "\nDeviceName: " + device.getDeviceName() +
                            "\nDeviceId: " + device.getDeviceId() +
                            "\nManufacturerName: " + device.getManufacturerName() +
                            "\nProductName: " + device.getProductName() +
                            "\nProductID: " + device.getProductId() +
                            "\nVendorID: " + device.getVendorId() +
                            "\nVersion: " + device.getVersion() +
                            "\nSerialNumber: " + device.getSerialNumber();
            FL.i(TAG, "Device[" + i + "]:" + out);
            str.append(out);
        }
        tvLoad.setText(str.toString());
        FL.i(TAG, "End enumerateUsbDevices");
        return devices;
    }

    /**
     * To register the intents or events in the broadcast receiver
     * @param usbReceiver This class manage the events around the usb device like plugged, unplugged or even, detect the granted permission to communicate
     */
    private void registerBroadcastReceiver(BroadcastReceiver usbReceiver) {
        FL.i(TAG, "Begin registerBroadcastReceiver");
        //USB Manager
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            FL.e(TAG, "getSystemService return null");
            return;
        }

        permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        // Register the broadcast, monitor USB plug and pull out
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, intentFilter);
        FL.i(TAG, "Register intent was sent");
    }

    private void test() {
        try {
            UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);
            for (UsbMassStorageDevice storageDevice : storageDevices) { //The general mobile phone has only one USB device
                // Apply for USB permissions
                if (!usbManager.hasPermission(storageDevice.getUsbDevice())) {
                    usbManager.requestPermission(storageDevice.getUsbDevice(), permissionIntent);
                    FL.e(TAG, "Not permission for usb manage");
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
                str.append("Volume Label: ").append(fileSystem.getVolumeLabel()).append("\n");
                str.append("Capacity: ").append(fSize(fileSystem.getCapacity())).append("\n");
                str.append("Occupied Space: ").append(fSize(fileSystem.getOccupiedSpace())).append("\n");
                str.append("Free Space: ").append(fSize(fileSystem.getFreeSpace())).append("\n");
                str.append("Chunk size: ").append(fSize(fileSystem.getChunkSize())).append("\n");

                UsbFile root = fileSystem.getRootDirectory();
                str.append("root directory: ").append(root.getAbsolutePath()).append("\n");
                UsbFile[] files = root.listFiles();
                int i = 0;
                for (UsbFile file : files)
                    str.append("file ").append(++i).append(": ").append(file.getName()).append("\n");

                // create a new file
                UsbFile newFile = root.createFile("usb_file_" + System.currentTimeMillis() + ".txt");
                str.append("New file: ").append(newFile.getAbsolutePath()).append("\n");

                // write the file
                // OutputStream os = new UsbFileOutputStream(newFile);
                OutputStream os = UsbFileStreamFactory.createBufferedOutputStream(newFile, fileSystem);
                os.write(("hi_" + System.currentTimeMillis()).getBytes());
                os.close();
                str.append("Written file: ").append(newFile.getAbsolutePath()).append("\n");

                // read the written file recently
                InputStream is = UsbFileStreamFactory.createBufferedInputStream(newFile, fileSystem);
                byte[] buffer = new byte[fileSystem.getChunkSize()];

                // Create a new file into the internal storage location, then, writing the content of the read file recently in this new file
                File sdFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), newFile.getName());
                str.append("Content of the read file was copied to:\n").append(sdFile.getAbsoluteFile().getPath()).append("\n");
                FileOutputStream sdOut = new FileOutputStream(sdFile);

                int len;
                while ((len = is.read(buffer)) != -1) {
                    sdOut.write(buffer, 0, len);
                }
                is.close();
                sdOut.close();
                storageDevice.close();
            }
        } catch (Exception e) {
            str.append("Error: ").append(e);
        }
        FL.i(TAG, "End of test:\n" + str.toString());
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

    /**
     * Get the info about the equipment characteristics and OS
     * @return Info equipment like a string
     */
    private String getInfoFromEquipment() {
        String info = "";
        info += "VERSION CODE {" + Build.VERSION_CODES.class.getFields()[android.os.Build.VERSION.SDK_INT].getName() + "} ";
        info += "VERSION.RELEASE {" + Build.VERSION.RELEASE + "} ";
        info += "VERSION.SDK {" + Build.VERSION.SDK_INT + "} ";
        info += "VERSION.INCREMENTAL {" + Build.VERSION.INCREMENTAL + "} ";
        info += "VERSION.CODENAME {" + Build.VERSION.CODENAME + "} ";
        info += "BRAND {" + Build.BRAND + "} ";
        info += "MANUFACTURER {" + Build.MANUFACTURER + "} ";
        info += "BOARD {" + Build.BOARD + "} ";
        info += "MODEL {" + Build.MODEL + "} ";
        info += "HARDWARE {" + Build.HARDWARE + "} ";
        info += "PRODUCT {" + Build.PRODUCT + "} ";
        info += "DEVICE {" + Build.DEVICE + "} ";
        info += "HOST {" + Build.HOST + "} ";
        info += "ID {" + Build.ID + "} ";
        return info;
    }

}