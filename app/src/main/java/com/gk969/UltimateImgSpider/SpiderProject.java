package com.gk969.UltimateImgSpider;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import com.gk969.Utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by songjian on 2016/5/7.
 */
public class SpiderProject {
    private final static String TAG = "SpiderProject";

    public final static int INVALID_INDEX = -1;

    private Runnable runOnFindProject;
    private Runnable runOnProjectLoad;

    static public class ProjectInfo {
        public String host;
        public File dir;
        public String dirTotalSpace;
        public volatile int imgDownloadNum;
        public volatile int imgProcessedNum;
        public volatile int imgTotalNum;
        public long imgTotalSize;
        public volatile int imgTreeHeight;

        public volatile int pageProcessedNum;
        public volatile int pageTotalNum;
        public volatile int pageTreeHeight;

        public volatile int albumScrollDistance;

        private void init(String siteHost, String sitePath, long[] paramImgInfo, long[] paramPageInfo, int scrollDistance) {
            host = siteHost;
            dir = new File(sitePath);

            if(!dir.exists()) {
                Log.i(TAG, "make new project dir "+dir.getPath());
                dir.mkdirs();
            }

            dirTotalSpace = Utils.byteSizeToString(dir.getTotalSpace());

            imgDownloadNum = (int) paramImgInfo[StaticValue.PARA_DOWNLOAD];
            imgProcessedNum = (int) paramImgInfo[StaticValue.PARA_PROCESSED];
            imgTotalNum = (int) paramImgInfo[StaticValue.PARA_TOTAL];
            imgTotalSize = paramImgInfo[StaticValue.PARA_TOTAL_SIZE];
            imgTreeHeight = (int) paramImgInfo[StaticValue.PARA_HEIGHT];

            pageProcessedNum = (int) paramPageInfo[StaticValue.PARA_PROCESSED];
            pageTotalNum = (int) paramPageInfo[StaticValue.PARA_TOTAL];
            pageTreeHeight = (int) paramPageInfo[StaticValue.PARA_HEIGHT];

            albumScrollDistance = scrollDistance;
        }

        public ProjectInfo(String siteHost, String sitePath, long[] paramImgInfo, long[] paramPageInfo, int scrollDistance) {
            init(siteHost, sitePath, paramImgInfo, paramPageInfo, scrollDistance);
        }

        public ProjectInfo(String siteHost, String sitePath, long[] paramImgInfo, long[] paramPageInfo) {
            init(siteHost, sitePath, paramImgInfo, paramPageInfo, 0);
        }

        public void saveParam() {
            File dataDir = new File(dir.getPath() + "/" + StaticValue.PROJECT_DATA_DIR);
            if(!dataDir.exists()) {
                dataDir.mkdirs();
            }

            String param = "{\"scrollDistance\":" + albumScrollDistance + "}";
            Utils.stringToFile(param, dataDir.getPath() + StaticValue.PROJECT_PARAM_NAME);
            Log.i(TAG, "saveProjectParam " + host + " " + param);
        }
    }

    public ArrayList<ProjectInfo> projectList = new ArrayList<ProjectInfo>();

    public SpiderProject(Runnable pRunOnFindProject, Runnable pRunOnProjectLoad) {
        runOnFindProject = pRunOnFindProject;
        runOnProjectLoad = pRunOnProjectLoad;
    }

    public int findIndexBySite(String host) {
        int index = INVALID_INDEX;

        for(int i = 0; i < projectList.size(); i++) {
            if(projectList.get(i).host.equals(host)) {
                index = i;
                break;

            }
        }

        return index;
    }


    public void refreshProjectList(File[] storageDir, String appName) {
        projectList.clear();

        for(File stoDir : storageDir) {
            Log.i(TAG, "refreshProjectList path:" + stoDir.getPath());

            File appDir=new File(stoDir.getPath()+"/"+appName);
            if((!appDir.isDirectory())||(!appDir.exists())){
                continue;
            }

            File[] fileList = appDir.listFiles();

            for(File file : fileList) {
                if(file.isDirectory()) {
                    Log.i(TAG, "project dir:" + file.getPath());

                    String dataDirPath = file.getPath() + StaticValue.PROJECT_DATA_DIR;
                    long[] imgInfo = new long[StaticValue.IMG_PARA_NUM];
                    long[] pageInfo = new long[StaticValue.PAGE_PARA_NUM];
                    String srcUrl = WatchdogService.getProjectInfo(dataDirPath, imgInfo, pageInfo);
                    if(srcUrl != null) {
                        try {
                            String host = new URL(srcUrl).getHost();

                            int scrollDistance = 0;
                            try {
                                String param = Utils.fileToString(dataDirPath + StaticValue.PROJECT_PARAM_NAME);
                                if(param != null) {
                                    JSONObject jsonParam = new JSONObject(param);
                                    scrollDistance = jsonParam.getInt("scrollDistance");
                                }
                            } catch(JSONException e) {
                                e.printStackTrace();
                            }

                            projectList.add(new ProjectInfo(host, file.getPath(), imgInfo, pageInfo, scrollDistance));
                            runOnFindProject.run();

                        } catch(MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        runOnProjectLoad.run();
        Log.i(TAG, "projectList.size " + projectList.size());
    }
}
