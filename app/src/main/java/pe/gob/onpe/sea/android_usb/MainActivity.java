package pe.gob.onpe.sea.android_usb;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bosphere.filelogger.FL;
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileStreamFactory;
import com.github.mjdev.libaums.partition.Partition;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.demo.otgusb.USB_PERMISSION";
    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;

    Button btnSave, btnLoad;
    EditText etInput;
    TextView tvLoad;
    String fileName = "";
    String filePath = "";
    String fileContent = "";
    StringBuilder str = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnLoad = findViewById(R.id.btnLoad);
        btnSave = findViewById(R.id.btnSave);
        etInput = findViewById(R.id.etInput);
        tvLoad = findViewById(R.id.tvLoad);
        fileName = "myFile.txt";
        filePath = "MyFileDir";
        if (!isExternalStorageAvailableForRW()) {
            btnSave.setEnabled(false);
        }

//        init();
//        test();
//        tvLoad.setText(str.toString());

//        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
//        usbManager.requestPermission(device.getUsbDevice(), permissionIntent);
/*
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this );

        StringBuilder str = new StringBuilder();
        for(UsbMassStorageDevice device: devices) {

            // before interacting with a device you need to call init()!
            try {
                device.init();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Only uses the first partition on the device
            FileSystem currentFs = device.getPartitions().get(0).getFileSystem();
            str.append(currentFs.getCapacity());
            str.append(currentFs.getOccupiedSpace());
            str.append(currentFs.getFreeSpace());
            str.append(currentFs.getChunkSize());
        }
        tvLoad.setText(str.toString());
*/
        // https://developer.android.com/training/data-storage/app-specific#java
        File[] externalStorageVolumes = ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
//        for (int i = 0; i < externalStorageVolumes.length; i++) {
//            System.out.println("Volumes:" + externalStorageVolumes[i].getAbsolutePath());
//        }
///*
        FL.i(TAG, Arrays.toString(externalStorageVolumes));
        tvLoad.setText(Arrays.toString(externalStorageVolumes));
//        tvLoad.setText(getUsbPaths(getApplicationContext()).toString());
//*/
        btnSave.setOnClickListener(v -> {
            tvLoad.setText("");
            fileContent = etInput.getText().toString().trim();
            if (!fileContent.equals("")) {
                String directory = externalStorageVolumes[1].getAbsolutePath()  + File.separator + filePath;
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
        });

        btnLoad.setOnClickListener(v -> {
            FileReader fr;
            String myExternalFolder = externalStorageVolumes[1] + File.separator + filePath;
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
        });
    }

    private boolean isExternalStorageAvailableForRW() {
        String externalStorageState = Environment.getExternalStorageState();
        FL.i(TAG, "externalStorageState: " + externalStorageState);
        return externalStorageState.equals(Environment.MEDIA_MOUNTED);
    }

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            System.out.println("onReceive: " + intent);
            String action = intent.getAction();
            if (action == null)
                return;
            switch (action) {
                case ACTION_USB_PERMISSION://User Authorized Broadcast
                    synchronized (this) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) { //Allow permission to apply
                            test();
                        } else {
                            System.out.println("User is not authorized, access to USB device failed");
                        }
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED://USB device plugged into the broadcast
                    System.out.println("USB device plugin");
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED://USB device unplugs the broadcast
                    System.out.println("USB device unplugged");
                    break;
            }
        }
    };
    private void init() {
        //USB Manager
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        // Register the broadcast, monitor USB plug and pull out
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, intentFilter);

        //Read and write permissions
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE}, 111);
    }

    private void test() {
        try {
            UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);
            for (UsbMassStorageDevice storageDevice : storageDevices) { //The general mobile phone has only one USB device
                // Apply for USB permissions
                if (!mUsbManager.hasPermission(storageDevice.getUsbDevice())) {
                    mUsbManager.requestPermission(storageDevice.getUsbDevice(), mPermissionIntent);
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

}