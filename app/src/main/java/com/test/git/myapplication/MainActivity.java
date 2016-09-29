package com.test.git.myapplication;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.test.git.model.FileInfo;
import com.test.git.services.DownloadService;

public class MainActivity extends AppCompatActivity {

    private ProgressBar mProgressBar;
    private Button btStart;
    private Button btStop;
    private boolean downLoad;
    private NotificationManager nfManager;
    private Notification notification;
    private RemoteViews remoteView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mProgressBar = (ProgressBar)findViewById(R.id.mProgressBar);
        btStart = (Button)findViewById(R.id.btStart);
        btStop = (Button)findViewById(R.id.btStop);

        mProgressBar.setMax(100);

        //创建文件信息对象
        final String downloadurl = "http://www.imooc.com/mobile/mukewang.apk";
        String filename = "muke";
        final FileInfo fileInfo = new FileInfo(
                0,
                downloadurl,
                filename,
                0,
                0
        );


        btStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!downLoad) {
                    Intent intent = new Intent(MainActivity.this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_START);
                    intent.putExtra("fileInfo", fileInfo);
                    startService(intent);
                }else {
                    Toast.makeText(MainActivity.this, "正在下载中...", Toast.LENGTH_SHORT).show();
                }
            }
        });


        btStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(downLoad) {
                    Intent intent = new Intent(MainActivity.this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_STOP);
                    intent.putExtra("fileInfo", fileInfo);
                    startService(intent);
                }else {
                    Toast.makeText(MainActivity.this, "已暂停下载", Toast.LENGTH_SHORT).show();
                }
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_UPDATE);
        registerReceiver(mReciver, filter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReciver);
        Intent intent = new Intent(MainActivity.this, DownloadService.class);
        stopService(intent);

        nfManager.cancel(R.string.app_name);
    }

    private static final String TAG = "DownloadService-";
    /**
     * 更新UI广播接收器
     */
    BroadcastReceiver mReciver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(DownloadService.ACTION_UPDATE.equals(intent.getAction())){
                boolean isPause = intent.getBooleanExtra("pause", false);
                boolean error = intent.getBooleanExtra("error", false);
                Log.i(TAG, "onReceive: " + isPause);
                if(error){
                    mProgressBar.setProgress(0);
                    updateNotification(0);
                    Toast.makeText(MainActivity.this, "下载失败, 请重试", Toast.LENGTH_SHORT).show();
                    downLoad = false;
                    nfManager.cancel(R.string.app_name);
                }else {
                    if (!isPause) {
                        int finished = intent.getIntExtra("finished", 0);
                        downLoad = true;
                        if (finished == 100) {
                            downLoad = false;
                        }
                        mProgressBar.setProgress(finished);
                        updateNotification(finished);
                    } else {
                        downLoad = false;
                    }
                }
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void createNotification(){
        nfManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setTicker("下载");
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setWhen(System.currentTimeMillis());
        builder.setAutoCancel(true);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(Intent.ACTION_DELETE), 0));
        notification = builder.build();
        notification.flags = Notification.FLAG_NO_CLEAR;


        remoteView = new RemoteViews(this.getPackageName(), R.layout.item_notification);
        remoteView.setProgressBar(R.id.mProgressBar, 100, 0, false);
        remoteView.setTextViewText(R.id.mFraction, "0.0%");

        notification.contentView = remoteView;
    }

    /**
     * 更新notification
     * @param num
     */
    private void updateNotification(int num){
        if(remoteView == null)createNotification();
        remoteView.setProgressBar(R.id.mProgressBar, 100, num, false);
        remoteView.setTextViewText(R.id.mFraction, num + "%");
        notification.contentView = remoteView;
        nfManager.notify(R.string.app_name, notification);
        if(num >= 100){
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    nfManager.cancel(R.string.app_name);
                }
            }, 500);
        }
    }

}
