package com.android.wujiahui.flickrview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by 武家辉 on 2015/12/17.
 */
public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PRELOAD = 1;

    private Handler mRequestHandler;
    private ConcurrentMap<T,String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;

    private LruCache<String, Bitmap> mLruCache;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory()/1024);

        final int cacheSize = maxMemory/8;

        mLruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount()/1024;
            }
        };

    }

    public void addBitmapToLruCache(String key, Bitmap bitmap) {

        if (getBitmapFromLruCache(key) == null) {
            mLruCache.put(key, bitmap);
        }

    }

    public void preloadBitmapIntoCache(String key) {
        if (getBitmapFromLruCache(key) == null) {
            mRequestHandler.obtainMessage(MESSAGE_PRELOAD, key)
                    .sendToTarget();
        }
    }


    private  void downloadBitmapIntoCache(String key) {
        try {
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(key);
            final Bitmap bitmapLoaded = BitmapFactory
                    .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            addBitmapToLruCache(key, bitmapLoaded);
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }

    }

    public Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);
    }

    public void queueThumnail(T target, String url) {
        //Log.i(TAG, "Got a URL: " + url);

        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target)
                    .sendToTarget();
        }
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestHandler.removeMessages(MESSAGE_PRELOAD);
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    //Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                } else if (msg.what == MESSAGE_PRELOAD) {
                    String url = (String)msg.obj;
                    handlePreload(url);
                }
            }
        };
    }

    private void handlePreload(String key) {
        if (getBitmapFromLruCache(key) == null) {
            downloadBitmapIntoCache(key);
        }
        //Log.i(TAG, "PreLoad a url from :" +key);
    }

    private void handleRequest(final T target) {

        final String url = mRequestMap.get(target);

        if (url == null) {
            return;
        }

        if (getBitmapFromLruCache(url) == null) {
            Log.i(TAG, "it's download now");
            downloadBitmapIntoCache(url);
        } else {
            Log.i(TAG, "it's preloaded");
        }

        final Bitmap bitmap = getBitmapFromLruCache(url);

        //Log.i(TAG, "Bitmap created");

        mResponseHandler.post(new Runnable() {
            @Override
            public void run() {


                if (mRequestMap.get(target) != url) {
                    return;
                }
                //Log.i(TAG, "Bitmap start to pass");
                mRequestMap.remove(target);
                mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                Log.i(TAG, "Bitmap passed to the main thread");
            }
        });



    }
}
