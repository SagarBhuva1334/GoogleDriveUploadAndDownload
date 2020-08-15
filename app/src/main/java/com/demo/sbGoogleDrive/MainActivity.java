package com.demo.sbGoogleDrive;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity implements ConnectionCallbacks, OnConnectionFailedListener {

    private Button btnUpload, btnDownload;
    private ProgressBar progressBar;
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_CREATOR = 2;
    private static final int REQUEST_CODE_RESOLUTION = 3;
    private boolean isAPÏConnected;
    private GoogleApiClient mGoogleApiClient;
    private DriveId mSelectedFileDriveId;
    private static final int PICK_FILE_RESULT_CODE = 4;
    private static final int RC_OPENER = 5;
    private Metadata metadata;
    private Uri strUri;
    private static String strFile;
    private static File fileInstance;

    private String[] mimeTypes =
            {"application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .doc & .docx
                    "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .ppt & .pptx
                    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xls & .xlsx
                    "text/plain",
                    "application/pdf",
                    "application/zip", "video/mp4",
                    "image/jpg", "image/jpeg", "image/png",
                    "application/vnd.android.package-archive"};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById();
        isWriteStoragePermissionGranted();
        init();
    }

    private void init() {
        //Initialize Google Drive API Client.
        connectAPIClient();

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Start camera to take a picture
                if (isAPÏConnected) {
                    Intent intent = new Intent();
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FILE_RESULT_CODE);
                } else {
                    Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "Error Google API is disable or permissions are required!", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED);

                    View snackBarView = snackbar.getView();
                    snackBarView.setBackgroundColor(Color.DKGRAY);
                    TextView textView = snackBarView.findViewById(R.id.snackbar_text);
                    textView.setTextColor(Color.RED);
                    snackbar.show();
                }
            }
        });

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isAPÏConnected) {
                    // If there is a selected file, open its contents.
                    if (mSelectedFileDriveId != null) {
                        new DownloadFile().execute();
                        return;
                    }
                    // Let the user pick a file...
                    IntentSender intentSender = Drive.DriveApi
                            .newOpenFileActivityBuilder()
                            .setMimeType(mimeTypes)
                            .build(mGoogleApiClient);
                    try {
                        startIntentSenderForResult(intentSender, RC_OPENER, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                } else {
                    Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "Error Google API is disable or permissions are required!", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED);

                    View snackBarView = snackbar.getView();
                    snackBarView.setBackgroundColor(Color.DKGRAY);
                    TextView textView = snackBarView.findViewById(R.id.snackbar_text);
                    textView.setTextColor(Color.RED);
                    snackbar.show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case PICK_FILE_RESULT_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    strUri = data.getData();
                    try {
                        getFileFromUri(MainActivity.this, strUri);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if(isWriteStoragePermissionGranted()){
                        saveAnyFileToDrive();
                    }
                }
                break;

            case RC_OPENER:
                if (requestCode == RC_OPENER && resultCode == RESULT_OK) {
                    mSelectedFileDriveId = data.getParcelableExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    if(mSelectedFileDriveId != null){
                        new DownloadFile().execute();
                    }
                }
                break;

            case REQUEST_CODE_CREATOR:
                //Called after a file is saved to Drive.
                if (resultCode == RESULT_OK) { //succesfully saved!.
                    Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "File successfully saved to Google Drive!", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED);

                    View snackBarView = snackbar.getView();
                    snackBarView.setBackgroundColor(Color.DKGRAY);
                    TextView textView = snackBarView.findViewById(R.id.snackbar_text);
                    textView.setTextColor(Color.YELLOW);
                    snackbar.show();
                } else {
                    Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), "File Not Uploaded to Google Drive!", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED);

                    View snackBarView = snackbar.getView();
                    snackBarView.setBackgroundColor(Color.DKGRAY);
                    TextView textView = snackBarView.findViewById(R.id.snackbar_text);
                    textView.setTextColor(Color.RED);
                    snackbar.show();
                }
                break;
        }
    }

    private void saveAnyFileToDrive() {
        Drive.DriveApi.newDriveContents(mGoogleApiClient).setResultCallback(new ResultCallback<DriveContentsResult>() {
            @Override
            public void onResult(@NonNull DriveContentsResult result) {
                if (!result.getStatus().isSuccess()) {
                    return;
                }
                OutputStream outputStream = result.getDriveContents().getOutputStream();

                if (fileInstance != null && strFile != null) {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(fileInstance);
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        String mimeType = getMimeType(strFile);

                        MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                .setMimeType(mimeType)
                                .setTitle(fileInstance.getName())
                                .build();

                        IntentSender intentSender = Drive.DriveApi
                                .newCreateFileActivityBuilder()
                                .setInitialMetadata(metadataChangeSet)
                                .setInitialDriveContents(result.getDriveContents())
                                .build(mGoogleApiClient);
                        try {
                            startIntentSenderForResult(intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                        }
                    } catch (IOException e1) {
                        Toast.makeText(getApplicationContext(), "Problem With File", Toast.LENGTH_LONG).show();
                    }

                } else {
                    Toast.makeText(getApplicationContext(), "file instance and str File is Null", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    //Disconnect when the application is closed!
    @Override
    protected void onDestroy() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onDestroy();
    }

    //This function will give MimeType of File
    // url = file path or whatever suitable URL you want.
    public static String getMimeType(String url) {
        String type = null;
        String newUrl = url.replaceAll(" ", "_");
        String extension = MimeTypeMap.getFileExtensionFromUrl(newUrl);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    public static File getFileFromUri(final Context context, final Uri uri) throws Exception {
        // check if file selected from google drive
        if (isGoogleDrive(uri)) {
            return saveFileIntoExternalStorageByUri(context, uri);
        }
        // do your other calculation for the other files and return that file
        strFile = FileUtils.getPath(context, uri);
        fileInstance = new File(strFile);

        return fileInstance;
    }

    public static boolean isGoogleDrive(Uri uri) {
        return "com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());
    }

    public static File saveFileIntoExternalStorageByUri(Context context, Uri uri) throws Exception {

        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        int originalSize = inputStream.available();

        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        strFile = getFileName(context, uri);
        File file = makeEmptyFileIntoExternalStorageWithTitle(strFile);
        bis = new BufferedInputStream(inputStream);
        bos = new BufferedOutputStream(new FileOutputStream(
                file, false));

        byte[] buf = new byte[originalSize];
        bis.read(buf);
        do {
            bos.write(buf);
        } while (bis.read(buf) != -1);

        bos.flush();
        bos.close();
        bis.close();

        strFile = file.getAbsolutePath();

        fileInstance = new File(strFile);

        return file;
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public static File makeEmptyFileIntoExternalStorageWithTitle(String title) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        return new File(root, title);
    }

    private ResultCallback<DriveResource.MetadataResult> metadataRetrievedCallback = new ResultCallback<DriveResource.MetadataResult>() {
        @Override
        public void onResult(DriveResource.MetadataResult result) {
            if (!result.getStatus().isSuccess()) {
                return;
            }
            metadata = result.getMetadata();
        }
    };

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "GoogleApiClient connection failed: " + result.toString());
        isAPÏConnected = false;
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        // Called typically when the app is not yet authorized, and authorization dialog is displayed to the user.
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (SendIntentException e) {
            e.printStackTrace();
            Log.d(TAG, "Exception while starting resolution activity. " + e.getMessage());
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "* API client connected !!!.");
        isAPÏConnected = true;
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "GoogleApiClient connection suspended.");
    }

    private void connectAPIClient() {
        if (mGoogleApiClient == null) {
            Log.i(TAG, "connectAPIClient().");
            // Create the API client and bind it to an instance variable.
            // We use this instance as the callback for connection and connection
            // failures.
            // Since no account name is passed, the user is prompted to choose.
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        // Connect the client. Once connected, the camera is launched.
        mGoogleApiClient.connect();
    }

    private void findViewById() {
        btnUpload = findViewById(R.id.btnUpload);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
    }

    private boolean downloadFileFromGoogleDrive(){

        DriveFile file = Drive.DriveApi.getFile(
                mGoogleApiClient, mSelectedFileDriveId);
        file.getMetadata(mGoogleApiClient)
                .setResultCallback(metadataRetrievedCallback);

        DriveApi.DriveContentsResult driveContentsResult = file.open(
                mGoogleApiClient,
                DriveFile.MODE_READ_ONLY, null).await();

        String orgFile = metadata.getOriginalFilename();

        DriveContents driveContents = driveContentsResult
                .getDriveContents();
        InputStream inputstream = driveContents.getInputStream();

        File driveFile = null;
        try {
            String extStorageDirectory = Environment.getExternalStorageDirectory().toString();
            File folder = new File(extStorageDirectory, "DriveFolder");
            folder.mkdir();
            driveFile = new File(folder, orgFile);
            driveFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            FileOutputStream fileOutput = new FileOutputStream(driveFile);

            byte[] buffer = new byte[1024];
            int bufferLength = 0;
            while ((bufferLength = inputstream.read(buffer)) > 0) {
                fileOutput.write(buffer, 0, bufferLength);
            }
            fileOutput.close();
            inputstream.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public class DownloadFile extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if(isWriteStoragePermissionGranted()){
                return downloadFileFromGoogleDrive();
            } else {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            progressBar.setVisibility(View.GONE);
            mSelectedFileDriveId = null;
            if(result){
                Toast.makeText(getApplicationContext(), "Download Successfully Completed", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getApplicationContext(), "Problem Downloading File", Toast.LENGTH_LONG).show();
            }
        }
    }

    public boolean isWriteStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                return false;
            }
        } else {
            //permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 2:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    //Permission Denied, You cannot use local drive.
                    isWriteStoragePermissionGranted();
                }
                break;
        }
    }
}
