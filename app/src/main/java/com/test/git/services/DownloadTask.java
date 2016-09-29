package com.test.git.services;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.test.git.db.ThreadDAOImpl;
import com.test.git.model.FileInfo;
import com.test.git.model.ThreadInfo;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * 下载任务类
 */
public class DownloadTask {
    private ThreadDAOImpl mDao = null;
    private Context mContext = null;
    private FileInfo mFileInfo = null;
    private int mFinished = 0;
    public boolean isPause = false;

    public DownloadTask(Context mContext, FileInfo mFileInfo) {
        this.mContext = mContext;
        this.mFileInfo = mFileInfo;
        mDao = new ThreadDAOImpl(mContext);
    }


    public void download(){
        //读取数据库的线程信息
        List<ThreadInfo> list = mDao.getThreads(mFileInfo.getUrl());
        ThreadInfo threadInfo = null;
        if(list.size() == 0){
            //初始化线程信息对象
            threadInfo = new ThreadInfo(0, mFileInfo.getUrl(), 0, mFileInfo.getLength(), 0);
        }else {
            //继续下载
            threadInfo = list.get(0);
        }

        //创建子线程进行下载
        new DownloadThread(threadInfo).start();
    }


    /**
     * 下载线程
     */
    class DownloadThread extends Thread{
        private ThreadInfo mThreadInfo = null;
        private HttpURLConnection conn;
        private RandomAccessFile raf;
        private InputStream input;

        public DownloadThread(ThreadInfo mThreadInfo){
            this.mThreadInfo = mThreadInfo;
        }

        @Override
        public void run() {
            //向数据库插入线程信息
            if(!mDao.ifExists(mThreadInfo.getUrl(), mThreadInfo.getId())){
                mDao.insertThread(mThreadInfo);
            }
            try{
                URL url = new URL(mThreadInfo.getUrl());
                conn = (HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setRequestMethod("GET");
                //设置下载位置
                int start = mThreadInfo.getStart() + mThreadInfo.getFinished();
                conn.setRequestProperty("Range", "bytes=" + start + "-" + mThreadInfo.getEnd());
                //设置文件写入位置
                File file = new File(DownloadService.DOWNLOAD_PATH, mFileInfo.getFileName());
                raf = new RandomAccessFile(file, "rwd");
                raf.seek(start);
                Intent intent = new Intent(DownloadService.ACTION_UPDATE);
                mFinished += mThreadInfo.getFinished();
                //开始下载
                if(conn.getResponseCode() == HttpURLConnection.HTTP_PARTIAL){
                    //读取数据
                    input = conn.getInputStream();
                    byte[] buffer = new byte[1024 * 4];
                    int len, percent = 0;
                    long time = System.currentTimeMillis();

                    intent.putExtra("finished", 0);
                    intent.putExtra("pause", isPause);
                    mContext.sendBroadcast(intent);

                    while ((len = input.read(buffer))!= -1){
                        //写入文件
                        raf.write(buffer, 0, len);
                        //把下载进度发送广播给Activity
                        mFinished += len;
                        if(System.currentTimeMillis() - time > 500 || mFinished == mFileInfo.getLength()) {
                            time = System.currentTimeMillis();
                            percent = mFinished * 100 / mFileInfo.getLength();
                            intent.putExtra("finished", percent);
                            intent.putExtra("pause", isPause);
//                            Log.i("DownloadService", "下载进度: " + percent + "%"  + "-total:" + mFileInfo.getLength() + "-now:" + mFinished);
                            mContext.sendBroadcast(intent);
                        }
                        Log.i("DownloadService", "下载进度: " + percent + "%"  + "-total:" + mFileInfo.getLength() + "-now:" + mFinished);
                        //下载暂停
                        if(isPause){
                            mDao.updateThread(mThreadInfo.getUrl(), mThreadInfo.getId(), mFinished);
                            intent.putExtra("pause", isPause);
                            mContext.sendBroadcast(intent);
                            return;
                        }
                    }

                    //此处可以用MD5进行检验
                    if(percent != 100){
                        Log.i("DownloadService", "write-----error----reload");
                        intent.putExtra("finished", 0);
                        intent.putExtra("error", true);
                        mContext.sendBroadcast(intent);
                    }else {
                        Log.i("DownloadService", "write-----ok----can_load");
                    }
                    //删除线程信息
                    mDao.deleteThread(mThreadInfo.getUrl(), mThreadInfo.getId());
                }
            }catch (Exception e){
//                e.printStackTrace();
                Log.i("DownloadService", "write-----error:" + e.getMessage());
                Intent intent = new Intent(DownloadService.ACTION_UPDATE);
                intent.putExtra("finished", 0);
                intent.putExtra("error", true);
                mContext.sendBroadcast(intent);
            }finally {
                try {
                    if(raf != null)raf.close();
                    if(input != null)input.close();
                    if(conn != null)conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }
    }

}
