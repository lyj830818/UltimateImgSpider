package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.Texture;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLRootView;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/*
Cache Mode:
*************************
*                       *
*      img file         *
*                       *
* ********************  *
* *                  *  *
* *  texture cache   *  *
* *                  *  *
* * ***************  *  *
* * *             *  *  *
* * *  disp area  *  *  *
* * *             *  *  *
* * ***************  *  *
* *                  *  *
* *                  *  *
* ********************  *
*                       *
*                       *
*************************
 */


public  class ThumbnailLoader
{
    private static final String TAG = "ThumbnailLoader";

    private final static int CACHE_SIZE=128;
    //A circle cache
    private SlotTexture[] textureCache=new SlotTexture[CACHE_SIZE];
    private AtomicInteger scrollStep=new AtomicInteger(1);

    private int bestOffsetOfDispInCache;
    private int cacheOffset=0;

    private volatile int dispAreaOffset;
    private volatile boolean isLoaderRunning;
    public volatile int albumTotalImgNum;
    private volatile int imgsInDispArea;

    private GLRootView mGLRootView;

    private TextureLoaderThread mTextureLoaderThread;

    private TiledTexture.Uploader mTextureUploader;

    private SlotView slotView;

    private ThumbnailLoaderHelper loaderHelper;

    protected int labelHeight;
    private final static int INFO_TEXT_COLOR=0xFF00FF00;

    public ThumbnailLoader(GLRootView glRoot, ThumbnailLoaderHelper helper)
    {

        for(int i=0; i<CACHE_SIZE; i++)
        {
            textureCache[i]=new SlotTexture();
            textureCache[i].mainBmp=Bitmap.createBitmap(StaticValue.THUMBNAIL_SIZE,
                    StaticValue.THUMBNAIL_SIZE, Bitmap.Config.RGB_565);

            textureCache[i].imgIndex=new AtomicInteger(i);
            textureCache[i].isReady=new AtomicBoolean(false);
            textureCache[i].hasTried=new AtomicBoolean(false);
        }

        mTextureUploader=new TiledTexture.Uploader(glRoot);
        mGLRootView=glRoot;
        loaderHelper=helper;
        loaderHelper.setLoader(this);
    }

    public void setView(SlotView view)
    {
        slotView=view;
    }

    private void clearCache()
    {
        for (SlotTexture slot : textureCache)
        {
            slot.recycle();
        }

        mTextureUploader.clear();
        TiledTexture.freeResources();
    }

    public void setAlbumTotalImgNum(int totalImgNum)
    {
        //Log.i(TAG, "setAlbumTotalImgNum " + totalImgNum);

        int prevTotalImgNum=albumTotalImgNum;
        if(prevTotalImgNum==totalImgNum)
        {
            return;
        }

        albumTotalImgNum=totalImgNum;

        if(totalImgNum==0)
        {
            if(prevTotalImgNum!=0)
            {
                mGLRootView.lockRenderThread();
                clearCache();
                slotView.scrollAbs(0);
                mGLRootView.unlockRenderThread();
            }
        }
        else if(totalImgNum>prevTotalImgNum)
        {
            if(dispAreaOffset+CACHE_SIZE>prevTotalImgNum)
            {
                mGLRootView.lockRenderThread();
                refreshCacheOffset(dispAreaOffset, true);
                mGLRootView.unlockRenderThread();

                for (SlotTexture slot:textureCache)
                {
                    slot.hasTried.set(false);
                }
            }

        }

        mGLRootView.requestRender();
    }

    private void startLoader()
    {
        TiledTexture.prepareResources();
        isLoaderRunning=true;
        mTextureLoaderThread = new TextureLoaderThread();
        mTextureLoaderThread.setDaemon(true);
        mTextureLoaderThread.start();

    }

    public void stopLoader()
    {
        isLoaderRunning=false;
    }

    public void initAboutView(int slotNum, int paraLabelHeight)
    {
        imgsInDispArea=slotNum;
        bestOffsetOfDispInCache=(CACHE_SIZE-slotNum)/2;
        Log.i(TAG, "slotNum " + slotNum);

        labelHeight=paraLabelHeight;

        if(!isLoaderRunning)
        {
            startLoader();
        }
        else
        {
            clearCache();
        }
    }

    public SlotTexture getTexture(int index)
    {
        if((index>=0)&&(index<albumTotalImgNum))
        {
            //Log.i(TAG, "getTexture "+index);
            return textureCache[index % CACHE_SIZE];
        }
        return null;
    }

    public class SlotTexture
    {
        Bitmap mainBmp;
        TiledTexture texture;
        StringTexture label;
        AtomicInteger imgIndex;
        AtomicBoolean isReady;
        AtomicBoolean hasTried;

        public void recycle()
        {
            if(texture!=null)
            {
                texture.recycle();
                texture = null;

                if(loaderHelper.needLabel())
                {
                    label.recycle();
                    label = null;
                }
            }

            isReady.set(false);
            hasTried.set(false);
        }
    }



    public void dispAreaScrollToIndex(int index)
    {
        refreshCacheOffset(index, false);
    }

    private void refreshCacheOffset(int index, boolean forceRefresh)
    {
        int newCacheOffset=index-bestOffsetOfDispInCache;
        int cacheOffsetMax=albumTotalImgNum-CACHE_SIZE;
        if(cacheOffsetMax<0)
        {
            cacheOffsetMax=0;
        }

        if(newCacheOffset<0)
        {
            newCacheOffset=0;
        }
        else if(newCacheOffset>cacheOffsetMax)
        {
            newCacheOffset=cacheOffsetMax;
        }

        if((newCacheOffset != cacheOffset)||forceRefresh)
        {
            int interval=Math.abs(newCacheOffset-cacheOffset);
            int step;
            int imgIndex;
            if (newCacheOffset >= cacheOffset)
            {
                step = 1;
                imgIndex=cacheOffset+CACHE_SIZE;
            }
            else
            {
                step=-1;
                imgIndex=cacheOffset-1;
            }

            for(int i=0; i<interval; i++)
            {
                //Log.i(TAG, "recycle cacheIndex:" + cacheIndex + " imgIndex:" + imgIndex);
                SlotTexture slot=textureCache[imgIndex % CACHE_SIZE];
                slot.recycle();

                slot.imgIndex.set(imgIndex);
                imgIndex += step;
            }

            scrollStep.set(step);
            cacheOffset=newCacheOffset;
        }

        dispAreaOffset=index;

        //Log.i(TAG, "scrollToIndex "+index+" cacheOffset "+cacheOffset);
    }


    private class TextureLoaderThread extends Thread
    {
        private final static int CHECK_INTERVAL=500;

        private boolean isOffsetChangedInLoading()
        {
            int step=scrollStep.get();
            int dispOffset=dispAreaOffset;
            int imgIndexForLoader=(step==1)?dispOffset:(dispOffset+imgsInDispArea-1);
            for(int i=0; i<CACHE_SIZE; i++)
            {
                int cacheIndex=imgIndexForLoader%CACHE_SIZE;

                SlotTexture slot=textureCache[cacheIndex];
                if(!slot.hasTried.get())
                {
                    Log.i(TAG, "Try "+slot.imgIndex.get());
                    boolean hasTried=true;
                    if(!slot.isReady.get())
                    {
                        mGLRootView.lockRenderThread();
                        int imgIndex = slot.imgIndex.get();
                        Bitmap bmpContainer = slot.mainBmp;
                        mGLRootView.unlockRenderThread();

                        Bitmap bmp = loaderHelper.getThumbnailByIndex(imgIndex, bmpContainer);

                        if (bmp != null)
                        {
                            mGLRootView.lockRenderThread();
                            if (imgIndex == slot.imgIndex.get())
                            {
                                slot.texture = new TiledTexture(bmp);

                                if(loaderHelper.needLabel())
                                {
                                    slot.label = StringTexture.newInstance(
                                            loaderHelper.getLabelString(imgIndex),
                                            labelHeight, INFO_TEXT_COLOR);
                                }

                                slot.isReady.set(true);
                                Log.i(TAG, "load success  cacheIndex:" + cacheIndex + " imgIndex:" +
                                        textureCache[cacheIndex].imgIndex);
                                mTextureUploader.addTexture(slot.texture);

                            }

                            mGLRootView.unlockRenderThread();
                        }
                        hasTried=imgIndex == slot.imgIndex.get();
                    }

                    slot.hasTried.set(hasTried);
                }

                if(dispAreaOffset!=dispOffset)
                {
                    return true;
                }

                imgIndexForLoader+=step;
                if(imgIndexForLoader<0)
                {
                    imgIndexForLoader=CACHE_SIZE-1;
                }

            }

            return false;
        }

        public void run()
        {
            while(isLoaderRunning)
            {
                if(!isOffsetChangedInLoading())
                {
                    try
                    {
                        sleep(CHECK_INTERVAL);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}