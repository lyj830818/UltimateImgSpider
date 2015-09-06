package com.gk969.Utils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

public class Utils
{
    static String TAG = "Utils";
    
    static public int strSimilarity(String s1, String s2)
    {
        int len = (s1.length() < s2.length()) ? s1.length() : s2.length();
        
        int i;
        for (i = 0; i < len; i++)
        {
            if (s1.charAt(i) != s2.charAt(i))
            {
                break;
            }
        }
        
        return i;
    }
    
    public static File getDirInExtSto(String path)
    {
        File dir = null;
        
        if (Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED))
        {
            String[] dirs=path.split("/");
            String step=Environment.getExternalStorageDirectory().getPath();
            
            for(String mdir:dirs)
            {
                if(!mdir.isEmpty())
                {
                    step+=("/"+mdir);
                    dir = new File(step);
                    if (!dir.exists())
                    {
                        Log.i(TAG, "Dir:" + dir.toString() + " Not Exist!");
                        dir.mkdir();
                        if (!dir.exists())
                        {
                            return null;
                        }
                    }
                    else
                    {
                        Log.i(TAG, "Dir:" + dir.toString() + " Already Exist!");
                    }
                }
            }
        }
        else
        {
            return null;
        }
        
        return dir;
    }
    

    public static String getSDKVersion()
    {
        return android.os.Build.VERSION.RELEASE;
    }
    
    public static String getPhoneType()
    {
        return android.os.Build.MODEL;
    }
    
    public static boolean isNetworkEffective()
    {
        String stableWebUrl[] = { "http://www.baidu.com", "http://www.qq.com" };
        
        for (String webUrl : stableWebUrl)
        {
            try
            {
                URL url = new URL(webUrl);
                
                HttpURLConnection urlConn = (HttpURLConnection) url
                        .openConnection();
                
                try
                {
                    urlConn.setConnectTimeout(10000);
                    urlConn.setReadTimeout(5000);
                    
                    if (urlConn.getResponseCode() == 200)
                    {
                        urlConn.disconnect();
                        Log.i(TAG, "isNetworkEffective " + webUrl);
                        return true;
                    }
                }
                finally
                {
                    if (urlConn != null)
                        urlConn.disconnect();
                }
            }
            catch (MalformedURLException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return false;
    }
    
    /**
     * dp、sp 转换为 px 的工具类
     * 
     * 
     */
    public static class DisplayUtil
    {
        /**
         * 将px值转换为dip或dp值，保证尺寸大小不变
         * 
         * @param pxValue
         * @param scale
         *            （DisplayMetrics类中属性density）
         * @return
         */
        public static int pxToDip(Context context, float pxValue)
        {
            final float scale = context.getResources().getDisplayMetrics().density;
            return (int) (pxValue / scale + 0.5f);
        }
        
        /**
         * 将dip或dp值转换为px值，保证尺寸大小不变
         * 
         * @param dipValue
         * @param scale
         *            （DisplayMetrics类中属性density）
         * @return
         */
        public static int dipToPx(Context context, float dipValue)
        {
            final float scale = context.getResources().getDisplayMetrics().density;
            return (int) (dipValue * scale + 0.5f);
        }
        
        /**
         * 将px值转换为sp值，保证文字大小不变
         * 
         * @param pxValue
         * @param fontScale
         *            （DisplayMetrics类中属性scaledDensity）
         * @return
         */
        public static int pxToSp(Context context, float pxValue)
        {
            final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
            return (int) (pxValue / fontScale + 0.5f);
        }
        
        /**
         * 将sp值转换为px值，保证文字大小不变
         * 
         * @param spValue
         * @param fontScale
         *            （DisplayMetrics类中属性scaledDensity）
         * @return
         */
        public static int spToPx(Context context, float spValue)
        {
            final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
            return (int) (spValue * fontScale + 0.5f);
        }
        
        public static int attrToPx(Context context, String attr)
        {
            int px = 0;
            try
            {
                int attrVal = Integer.parseInt(attr.substring(0,
                        attr.length() - 2));
                if (attr.endsWith("px"))
                {
                    px = attrVal;
                }
                else if (attr.endsWith("dp"))
                {
                    px = dipToPx(context, attrVal);
                }
                else if (attr.endsWith("sp"))
                {
                    px = spToPx(context, attrVal);
                }
            }
            catch (NumberFormatException e)
            {
                
            }
            return px;
        }
    }
    
}