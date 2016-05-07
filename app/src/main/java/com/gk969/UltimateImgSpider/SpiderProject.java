package com.gk969.UltimateImgSpider;

import android.util.Log;

import com.gk969.Utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by songjian on 2016/5/7.
 */
public class SpiderProject
{
    private final static String TAG="SpiderProject";

    public final static int INVALID_INDEX=-1;

    public native void jniGetProjectInfoOnStart(String path, long[] imgInfo, long[] pageInfo);
    static
    {
        System.loadLibrary("UltimateImgSpider");
    }

    static public class ProjectInfo
    {
        public String site;
        public long[] imgInfo;
        public long[] pageInfo;
        public int albumScrollDistance;


        public ProjectInfo(String siteHost, long[] paramImgInfo, long[] paramPageInfo, int scrollDistance)
        {
            site=siteHost;
            imgInfo=paramImgInfo;
            pageInfo=paramPageInfo;
            albumScrollDistance=scrollDistance;
        }

        public ProjectInfo(String siteHost, long[] paramImgInfo, long[] paramPageInfo)
        {
            site=siteHost;
            imgInfo=paramImgInfo;
            pageInfo=paramPageInfo;
            albumScrollDistance=0;
        }
    }

    public ArrayList<ProjectInfo> projectList = new ArrayList<ProjectInfo>();

    private String mAppPath;

    public SpiderProject(String AppPath)
    {
        mAppPath=AppPath;
    }

    public int findIndexBySite(String site)
    {
        int index=INVALID_INDEX;

        for(ProjectInfo project:projectList)
        {
            if(project.site.equals(site))
            {
                break;
            }
        }

        return index;
    }


    public void refreshProjectList()
    {
        projectList.clear();

        File appDir = new File(mAppPath);

        Log.i(TAG, "refreshProjectList path:" + mAppPath);

        File[] fileList = appDir.listFiles();

        for(File file : fileList)
        {
            if(file.isDirectory())
            {
                Log.i(TAG, "project dir:" + file.getPath());

                String dataDirPath=file.getPath() + StaticValue.PROJECT_DATA_DIR;
                if(WatchdogService.projectDataIsSafe(dataDirPath))
                {
                    long[] imgInfo=new long[StaticValue.IMG_PARA_NUM];
                    long[] pageInfo=new long[StaticValue.PAGE_PARA_NUM];
                    jniGetProjectInfoOnStart(dataDirPath + StaticValue.PROJECT_DATA_NAME, imgInfo, pageInfo);

                    int scrollDistance=0;
                    try
                    {
                        String param=Utils.fileToString(dataDirPath + StaticValue.PROJECT_PARAM_NAME);
                        if(param!=null)
                        {
                            JSONObject jsonParam = new JSONObject(param);
                            scrollDistance = jsonParam.getInt("scrollDistance");
                        }
                    } catch(JSONException e)
                    {
                        e.printStackTrace();
                    }

                    projectList.add(new ProjectInfo(file.getName(), imgInfo, pageInfo, scrollDistance));
                }
            }
        }

        Log.i(TAG, "projectList.size " + projectList.size());
    }

    public void saveProjectParam()
    {
        for(ProjectInfo project:projectList)
        {
            File dataDir=new File(mAppPath+"/"+project.site+StaticValue.PROJECT_DATA_DIR);
            if(!dataDir.exists())
            {
                dataDir.mkdirs();
            }

            Utils.stringToFile("{\"scrollDistance\":"+project.albumScrollDistance+"}",
                    dataDir.getPath()+StaticValue.PROJECT_PARAM_NAME);
        }
    }
}
