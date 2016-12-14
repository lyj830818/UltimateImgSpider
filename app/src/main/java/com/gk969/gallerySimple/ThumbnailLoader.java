package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.Utils.Utils;
import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.UploadedTexture;
import com.gk969.gallery.gallery3d.ui.GLRoot;
import com.gk969.gallery.gallery3d.ui.GLRootView;

import java.util.ArrayDeque;

/*
Cache Mode:
***********************
*                     *
*      img file       *
*                     *
* ******************* *
* *                 * *
* *  texture cache  * *
* *                 * *
* * *************** * *
* * *             * * *
* * *             * * *
* * *   visible   * * *
* * *   window    * * *
* * *             * * *
* * *             * * *
* * *************** * *
* *                 * *
* *                 * *
* ******************* *
*                     *
*                     *
***********************
 */


public class ThumbnailLoader {
    private static final String TAG = "ThumbnailLoader";

    private int cacheSize;
    //A circle cache
    private SlotTexture[] textureCache;
    private volatile int scrollStep = 1;

    private int bestOffsetOfVisibleInCache;
    private int cacheOffset = 0;

    public volatile int visibleAreaOffset;
    public volatile int albumTotalImgNum;
    private volatile int imgNumInView;

    private GLRootView mGLRootView;

    private Uploader uploader = new Uploader();

    private ThumbnailLoaderThreadPool mThumbnailLoaderThreadPool;
    private SlotView slotView;

    private ThumbnailLoaderHelper loaderHelper;

    private int labelTextSize;
    private final static int LABEL_NAME_COLOR = 0xFF00F000;
    private final static int LABEL_INFO_ACTIVE_COLOR = 0xFF00F000;
    private final static int LABEL_INFO_INACTIVE_COLOR = 0xFFFFFFFF;

    private int activeSlotIndex = StaticValue.INDEX_INVALID;

    private volatile boolean needLabel;

    private int labelNameLimit;

    public ThumbnailLoader(GLRootView glRoot, ThumbnailLoaderHelper helper) {
        cacheSize = StaticValue.getThumbnailCacheSize();
        textureCache = new SlotTexture[cacheSize];
        for(int i = 0; i < cacheSize; i++) {
            textureCache[i] = new SlotTexture();
            textureCache[i].mainBmp = Bitmap.createBitmap(StaticValue.THUMBNAIL_SIZE,
                    StaticValue.THUMBNAIL_SIZE, StaticValue.BITMAP_TYPE);
            textureCache[i].imgIndex = i;
            textureCache[i].isReady = false;
            textureCache[i].hasTried = false;
            textureCache[i].isLoading = false;
        }

        mGLRootView = glRoot;
        loaderHelper = helper;
        helper.setLoader(this);
        needLabel = helper.needLabel();
        Log.i(TAG, "ThumbnailLoader cache size " + cacheSize);
    }

    public void refreshSlotInfo(int slotIndex, String infoStr, boolean isActive) {
        mGLRootView.lockRenderThread();
        activeSlotIndex = isActive ? slotIndex : StaticValue.INDEX_INVALID;

        if(infoStr != null) {
            if((slotIndex >= cacheOffset) && (slotIndex < (cacheOffset + cacheSize))) {
                textureCache[slotIndex % cacheSize].labelInfo = StringTexture.newInstance(infoStr,
                        labelTextSize, isActive ? LABEL_INFO_ACTIVE_COLOR : LABEL_INFO_INACTIVE_COLOR);
            }
        }

        mGLRootView.unlockRenderThread();
    }

    public void setView(SlotView view) {
        slotView = view;
    }

    public void setHelper(ThumbnailLoaderHelper helper, int totalImgNum) {
        loaderHelper = helper;
        needLabel = helper.needLabel();

        slotView.onChangeView();

        setAlbumTotalImgNum(0);
        setAlbumTotalImgNum(totalImgNum);
        mThumbnailLoaderThreadPool.wakeup();
    }

    public void setHelper(ThumbnailLoaderHelper helper, int totalImgNum, int scrollDistance) {
        setHelper(helper, totalImgNum);

        mGLRootView.lockRenderThread();
        slotView.scrollAbs(scrollDistance);
        mGLRootView.unlockRenderThread();
    }

    private void clearCache() {
        for(SlotTexture slot : textureCache) {
            slot.recycle();
        }
    }

    public void setAlbumTotalImgNum(int totalImgNum) {
        Log.i(TAG, "setAlbumTotalImgNum " + totalImgNum);

        int prevTotalImgNum = albumTotalImgNum;
        if(prevTotalImgNum == totalImgNum) {
            return;
        }

        mGLRootView.lockRenderThread();
        albumTotalImgNum = totalImgNum;
        if(totalImgNum == 0) {
            clearCache();
            slotView.scrollAbs(0);
        } else if(totalImgNum > prevTotalImgNum) {
            if(visibleAreaOffset + cacheSize > prevTotalImgNum) {
                for(SlotTexture slot : textureCache) {
                    slot.hasTried = false;
                }
                refreshCacheOffset(visibleAreaOffset, true);

            }

            if(prevTotalImgNum != 0) {
                slotView.onNewImgReceived(prevTotalImgNum);
            }
        }
        mGLRootView.unlockRenderThread();

        mGLRootView.requestRender();
    }

    public void stopLoader() {
        mThumbnailLoaderThreadPool.shutdown();
    }

    public void initAboutView(int slotNumInView, int paraLabelTextSize, int paraLabelNameLimit) {
        imgNumInView = slotNumInView;
        bestOffsetOfVisibleInCache = (cacheSize - slotNumInView) / 2;
        Log.i(TAG, "initAboutView slotNumInView " + slotNumInView);

        labelTextSize = paraLabelTextSize;
        labelNameLimit = paraLabelNameLimit;

        if(mThumbnailLoaderThreadPool == null) {
            mThumbnailLoaderThreadPool = new ThumbnailLoaderThreadPool();
        } else {
            mThumbnailLoaderThreadPool.wakeup();
        }
    }

    public SlotTexture getTexture(int index) {
        if((index >= 0) && (index < albumTotalImgNum)) {
            //Log.i(TAG, "getTexture "+index);
            return textureCache[index % cacheSize];
        }
        return null;
    }

    public class SlotTexture extends UploadedTexture {
        public Bitmap mainBmp;
        StringTexture labelName;
        StringTexture labelInfo;
        volatile int imgIndex;
        volatile boolean isReady;
        volatile boolean hasTried;
        boolean isLoading;

        @Override
        protected Bitmap onGetBitmap() {
            return mainBmp;
        }

        @Override
        protected void onFreeBitmap(Bitmap bitmap) {

        }

        @Override
        public void recycle() {
            super.recycle();

            if(labelInfo != null) {
                labelInfo.recycle();
                labelInfo = null;
            }

            if(labelName != null) {
                labelName.recycle();
                labelName = null;
            }

            isReady = false;
            hasTried = false;
        }
    }


    public void onViewScrollOverLine(int index) {
        refreshCacheOffset(index, false);
    }

    private void refreshCacheOffset(int firstSlotInView, boolean forceRefresh) {
        //Log.i(TAG, "refreshCacheOffset "+firstSlotInView+" "+forceRefresh);

        int newCacheOffset = firstSlotInView - bestOffsetOfVisibleInCache;
        int cacheOffsetMax = albumTotalImgNum - cacheSize;
        if(cacheOffsetMax < 0) {
            cacheOffsetMax = 0;
        }

        if(newCacheOffset < 0) {
            newCacheOffset = 0;
        } else if(newCacheOffset > cacheOffsetMax) {
            newCacheOffset = cacheOffsetMax;
        }

        if((newCacheOffset != cacheOffset) || forceRefresh) {
            int interval = Math.abs(newCacheOffset - cacheOffset);
            int step;
            int imgIndex;
            if(newCacheOffset >= cacheOffset) {
                step = 1;
                imgIndex = cacheOffset + cacheSize;
            } else {
                step = -1;
                imgIndex = cacheOffset - 1;
            }

            for(int i = 0; i < interval; i++) {
                //Log.i(TAG, "recycle cacheIndex:" + cacheIndex + " imgIndex:" + imgIndex);
                SlotTexture slot = textureCache[imgIndex % cacheSize];
                slot.recycle();

                slot.imgIndex = imgIndex;
                imgIndex += step;
            }

            scrollStep = step;
            cacheOffset = newCacheOffset;

            mThumbnailLoaderThreadPool.wakeup();
        }

        visibleAreaOffset = firstSlotInView;

        //Log.i(TAG, "scrollToIndex "+index+" cacheOffset "+cacheOffset);
    }

    private class Uploader implements GLRoot.OnGLIdleListener {
        // We are targeting at 60fps, so we have 16ms for each frame.
        // In this 16ms, we use about 4~8 ms to upload tiles.
        private static final long UPLOAD_TILE_LIMIT = 4; // ms
        private boolean isQueued;
        private ArrayDeque<SlotTexture> textureDeque=new ArrayDeque<>(3);

        /**
         * Must be synchronized with GL-thread
         *
         * @param texture
         */
        void add(SlotTexture texture) {
            if(textureDeque.size()>cacheSize){
                textureDeque.removeFirst();
            }
            textureDeque.addLast(texture);

            if(!isQueued) {
                isQueued = true;
                mGLRootView.addOnGLIdleListener(this);
            }
        }

        @Override
        public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
            //Log.i(TAG, "onGLIdle");

            long now = SystemClock.uptimeMillis();
            long dueTime = now + UPLOAD_TILE_LIMIT;
            //Log.i(TAG, "onGLIdle");
            while (now < dueTime && !textureDeque.isEmpty()) {
                SlotTexture curSlotTexture = textureDeque.removeFirst();

                if(!curSlotTexture.isReady) {
                    //Log.i(TAG, "upload slot " + curSlotTexture.imgIndex);
                    curSlotTexture.updateContent(canvas);
                    curSlotTexture.isReady = true;
                    mGLRootView.requestRender();
                }
                now = SystemClock.uptimeMillis();
            }
            isQueued=!textureDeque.isEmpty();
            return isQueued;
        }
    }

    private int getNextIndexInCache(int curIndex, int step) {
        curIndex += step;
        if(curIndex < 0) {
            curIndex = cacheSize - 1;
        } else if(curIndex >= cacheSize) {
            curIndex = 0;
        }
        return curIndex;
    }

    private class ThumbnailLoaderThreadPool {
        private volatile boolean isRunning;
        private static final int THREAD_POOL_SIZE_MAX = 4;
        
        private ThumbnailLoaderThread[] threads;
        
        ThumbnailLoaderThreadPool() {
            int poolSize = Utils.getCpuCoresNum();
            if(poolSize > THREAD_POOL_SIZE_MAX) {
                poolSize = 4;
            }

            Log.i(TAG, "loader thread pool size " + poolSize);
            isRunning = true;
            threads = new ThumbnailLoaderThread[poolSize];
            for(int i = 0; i < poolSize; i++) {
                threads[i] = new ThumbnailLoaderThread(i);
            }
        }

        
        void wakeup() {
            //Log.i(TAG, "pool wakeup");
            for(ThumbnailLoaderThread thread : threads) {
                thread.wakeup();
            }
        }
        
        void shutdown() {
            isRunning = false;
            wakeup();
        }
        
        private class ThumbnailLoaderThread extends Thread {
            private final static long SLEEP_INTERVAL = 365 * 24 * 3600 * 1000;
            private int index;
            volatile boolean isWorking;

            private byte[] bitmapInTempStorage=new byte[16*1024];
            private BitmapFactory.Options bmpOpts;

            ThumbnailLoaderThread(int i) {
                super("ThumbnailLoaderThread");
                index = i;

                bmpOpts = new BitmapFactory.Options();
                bmpOpts.inPreferredConfig = StaticValue.BITMAP_TYPE;
                bmpOpts.inTempStorage=bitmapInTempStorage;

                setDaemon(true);
                start();
            }

            private boolean isOffsetChangedInLoading() {
                int step = scrollStep;
                int visibleOffset = visibleAreaOffset;
                int loaderStart = (step == 1) ? visibleOffset : (visibleOffset + imgNumInView - 1);
                int imgIndexForLoader = loaderStart % cacheSize;
                for(int i = 0; i < cacheSize; i++) {
                    if(!isRunning) {
                        break;
                    }

                    SlotTexture slot = textureCache[imgIndexForLoader];

                    if(!slot.hasTried) {
                        mGLRootView.lockRenderThread();

                        if(!slot.isLoading) {
                            slot.isLoading = true;

                            boolean hasTried = true;
                            if(!slot.isReady) {
                                int imgIndex = slot.imgIndex;
                                //Log.i(TAG, "Try " + imgIndex);
                                bmpOpts.inBitmap = slot.mainBmp;
                                mGLRootView.unlockRenderThread();
                                Bitmap bmp = loaderHelper.getThumbnailByIndex(imgIndex, bmpOpts);
                                mGLRootView.lockRenderThread();
                                if(bmp != null) {
                                    if(imgIndex == slot.imgIndex) {
                                        uploader.add(slot);
                                    }
                                }

                                if(needLabel) {
                                    //Log.i(TAG, "Load Label " + imgIndex + " " + loaderHelper.getLabelString(imgIndex));

                                    String[] labelStr = loaderHelper.getLabelString(imgIndex).split(" ");

                                    slot.labelName = StringTexture.newInstance(labelStr[0], labelTextSize,
                                            LABEL_NAME_COLOR, labelNameLimit, false);
                                    if(labelStr.length > 1) {
                                        slot.labelInfo = StringTexture.newInstance(labelStr[1],
                                                labelTextSize, (imgIndex == activeSlotIndex) ?
                                                        LABEL_INFO_ACTIVE_COLOR : LABEL_INFO_INACTIVE_COLOR);
                                    }
                                }

                                mGLRootView.requestRender();

                                hasTried = imgIndex == slot.imgIndex;
                            }

                            slot.hasTried = hasTried;
                            slot.isLoading = false;
                        }
                        mGLRootView.unlockRenderThread();
                    }

                    if(visibleAreaOffset != visibleOffset) {
                        return true;
                    }

                    imgIndexForLoader = getNextIndexInCache(imgIndexForLoader, step);
                }

                return false;
            }

            void wakeup() {
                if(!isWorking) {
                    //Log.i(TAG, "thread "+index+" wakeup");
                    interrupt();
                }
            }

            public void run() {
                while(isRunning) {
                    isWorking = true;
                    //Log.i(TAG, "thread "+index+" work");
                    while(isOffsetChangedInLoading()) ;
                    isWorking = false;

                    if(isRunning) {
                        try {
                            sleep(SLEEP_INTERVAL);
                        } catch(InterruptedException e) {
                            //Log.i(TAG, "Loader Interrupted");
                        }
                    }
                }
            }
        }
    }
}