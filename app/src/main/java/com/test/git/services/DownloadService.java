package com.test.git.services;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.test.git.model.FileInfo;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by likuan on 16/9/8.
 */
public class DownloadService extends Service {

    public static final String DOWNLOAD_PATH =
            Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/ResumeBrokenDownloads/";
    //启动服务相关(start, stop)
    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";

    //更新相关
    public static final String ACTION_UPDATE = "ACTION_UPDATE";
    public static final int MSG_INIT = 0;

    private DownloadTask mDownloadTask = null;

    private static final String TAG = "DownloadService";


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: ");
        if(intent != null) {
            if (ACTION_START.equals(intent.getAction())) {
                FileInfo fileInfo = (FileInfo) intent.getSerializableExtra("fileInfo");
                Log.i(TAG, "onStartCommand: start:" + fileInfo.toString());
                //启动初始化线程
                new InitThread(fileInfo).start();
            } else if (ACTION_STOP.equals(intent.getAction())) {
                FileInfo fileInfo = (FileInfo) intent.getSerializableExtra("fileInfo");
                Log.i(TAG, "onStartCommand: stop:" + fileInfo.toString());
                if (mDownloadTask != null) {
                    mDownloadTask.isPause = true;
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public ComponentName startService(Intent service) {
        Log.i(TAG, "startService: ");
        return super.startService(service);
    }

    @Override
    public boolean stopService(Intent name) {
        Log.i(TAG, "stopService: ");
        return super.stopService(name);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case MSG_INIT:
                    FileInfo fileInfo = (FileInfo) msg.obj;
                    Log.i(TAG, "handleMessage: init:" + fileInfo.toString());
                    //启动下载任务
                    mDownloadTask = new DownloadTask(DownloadService.this, fileInfo);
                    mDownloadTask.download();
                    break;
            }
        }
    };

    /**
     * 初始化子线程,
     * 此线程的作用是:
     * 1.获取文件基本信息
     * 2.创建文件
     * 3.发送handler(handler负责启动下载任务)
     */
    class InitThread extends Thread{
        private FileInfo mFileInfo = null;
        private HttpURLConnection conn;
        private RandomAccessFile raf;

        public InitThread(FileInfo mFileInfo) {
            this.mFileInfo = mFileInfo;
        }

        @Override
        public void run() {
            try {
                //连接网络文件
                URL url = new URL(mFileInfo.getUrl());
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setRequestMethod("GET");
                int length = -1;
                if(conn.getResponseCode() == HttpURLConnection.HTTP_OK){
                    //获取文件长度
                    length = conn.getContentLength();
                }
                if(length <= 0)return;
                File dir = new File(DOWNLOAD_PATH);
                if(!dir.exists()){
                    dir.mkdir();
                }
                //在本地创建文件
                File file = new File(dir, mFileInfo.getFileName());
                raf = new RandomAccessFile(file, "rwd");
                //设置文件长度
                raf.setLength(length);
                mFileInfo.setLength(length);
                handler.obtainMessage(MSG_INIT, mFileInfo).sendToTarget();
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                try {
                    if(raf != null) raf.close();
                    if(conn != null)conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
