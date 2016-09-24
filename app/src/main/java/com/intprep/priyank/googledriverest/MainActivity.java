package com.intprep.priyank.googledriverest;

import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Manifest;

import pub.devrel.easypermissions.*;
/**
 * Created by priyank on 9/1/16.
 */
public class MainActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {

    GoogleAccountCredential mCredential;
                private TextView mOutputText;
                private Button mCallapiButton;
                ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002 ;
    static final int REQUEST_PERMISSION_GET_ACCOUNT = 1003;

    static final String Buttontxt = "Call the API";
    static final String PREF_ACC_NAME = "account";
    private static final String[] SCOPES = {DriveScopes.DRIVE_METADATA_READONLY};


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout activitylayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT);
        activitylayout.setLayoutParams(lp);
        activitylayout.setOrientation(LinearLayout.VERTICAL);
        activitylayout.setPadding(16,16,16,16);

        ViewGroup.LayoutParams vlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallapiButton = new Button(this);
        mCallapiButton.setText(Buttontxt);
        mCallapiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCallapiButton.setEnabled(false);
                mCallapiButton.setText("");
                getResultsfromApi();
                mCallapiButton.setEnabled(true);
            }
        });
        activitylayout.addView(mCallapiButton);

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(vlp);
        mOutputText.setPadding(16,16,16,16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText("Click the" + Buttontxt +"Button to fetch the API");
        activitylayout.addView(mOutputText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling the API");

        setContentView(activitylayout);


        //Initialize Service and Object

        mCredential = GoogleAccountCredential.usingOAuth2(getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    /* Check for the Pre-requirements,
        1.Google Serivce are installed
        2.Device is Online
        3.Account is Seleccted
            ; if any of the condition is not satisfied then application will prompt the user.
     */
    private void getResultsfromApi() {
        if(! isGooglePlayServicesAvailable()){
            acquireGooglePlayServices();
        }
        else if(! isDeviceOnline()){
            mOutputText.setText("Network Connection is not available");
        }
        else if(mCredential.getSelectedAccount() == null){
            chooseAccount();
        }
        else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(this, android.Manifest.permission.GET_ACCOUNTS)) {
            String accname = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACC_NAME, null);
            if (accname != null) {
                mCredential.setSelectedAccountName(accname);
                getResultsfromApi();
            }
         else {
            startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            } }
        else {
            EasyPermissions.requestPermissions(this,"Application requires access to your google account",REQUEST_PERMISSION_GET_ACCOUNT, android.Manifest.permission.GET_ACCOUNTS);
        }

    }

    protected void onActivityResult(int requestcode,int resultcode,Intent data){
        super.onActivityResult(requestcode,resultcode,data);
        switch (requestcode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultcode != RESULT_OK) {
                    mOutputText.setText("This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsfromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultcode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accname = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accname != null) {
                        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor edit = settings.edit();
                        edit.putString(PREF_ACC_NAME, accname);
                        edit.apply();
                        mCredential.setSelectedAccountName(accname);
                        getResultsfromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultcode == RESULT_OK) {
                    getResultsfromApi();
                }
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionResult(int requestcode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestcode,permissions,grantResults);
        EasyPermissions.onRequestPermissionsResult(requestcode,permissions,grantResults,this);
    }


    private boolean isDeviceOnline() {
        ConnectivityManager connmanager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nwinfo = connmanager.getActiveNetworkInfo();
        return (nwinfo != null && nwinfo.isConnected());
        }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiavail = GoogleApiAvailability.getInstance();
        final int connstatcode = apiavail.isGooglePlayServicesAvailable(this);
        return  connstatcode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiavail = GoogleApiAvailability.getInstance();
        final int statcode = apiavail.isGooglePlayServicesAvailable(this);
        if (apiavail.isUserResolvableError(statcode)){
            showGooglePlayServicesAvailabilityErrorDialog(statcode);

        }

    }

    private void showGooglePlayServicesAvailabilityErrorDialog(int statcode) {
        GoogleApiAvailability apiavail = GoogleApiAvailability.getInstance();
        Dialog dialog = apiavail.getErrorDialog(MainActivity.this,statcode,REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

    }


    private class MakeRequestTask extends AsyncTask<Void,Void,List<String>>{
        private com.google.api.services.drive.Drive mService = null;
        Exception excperror = null;

        public MakeRequestTask(GoogleAccountCredential credential){
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonfact = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(transport,jsonfact,credential)
                    .setApplicationName("Drive Quick Start")
                    .build();
        }


        @Override
        protected List<String> doInBackground(Void... voids) {
         try{
             return getDatafromApi();
         }
         catch (Exception e){
             excperror = e;
             cancel(true);
             return null;
         }

        }
        private List<String> getDatafromApi() throws IOException {
            List<String> fileInfo = new ArrayList<String>();
            FileList result = mService.files().list()
                    .setPageSize(10)
                    .setFields("nextPageToken, items(id, name)")
                    .execute();
            List<File> files = result.getFiles();
            if (files != null) {
                for (File file : files) {
                    fileInfo.add(String.format("%s (%s)\n",
                            file.getName(), file.getId()));
                }
            }
            return fileInfo;
        }
        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                output.add(0, "Data retrieved using the Drive API:");
                mOutputText.setText(TextUtils.join("\n", output));
            }
        }
        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (excperror != null) {
                if (excperror instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) excperror)
                                    .getConnectionStatusCode());
                } else if (excperror instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) excperror).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + excperror.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }

    }

}


