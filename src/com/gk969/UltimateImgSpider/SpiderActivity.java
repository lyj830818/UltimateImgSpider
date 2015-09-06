package com.gk969.UltimateImgSpider;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.gk969.Utils.MemoryInfo;
import com.gk969.Utils.Utils;
import com.gk969.View.ImageTextButton;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class SpiderActivity extends Activity
{
	private final String LOG_TAG = "SpiderActivity";
	public final static int REQUST_SRC_URL = 0;
	
	final static String SOURCE_URL_BUNDLE_KEY = "SourceUrl";
	final static String CMD_BUNDLE_KEY = "cmd";
	
	public final static int CMD_NOTHING = 0;
	public final static int CMD_CLEAR = 1;
	public final static int CMD_PAUSE = 2;
	public final static int CMD_CONTINUE = 3;
	public final static int CMD_STOP=4;
	
	private final int STATE_IDLE=0;
	private final int STATE_CONNECTED=1;
	private final int STATE_WAIT_DISCONNECT=2;
	private final int STATE_WAIT_CONNECT=3;
	private final int STATE_DISCONNECTED=4;
	
	private int serviceState=STATE_IDLE;
	
	private ImageTextButton btPauseOrContinue;
	private ImageTextButton btSelSrc;
	private ImageTextButton btClear;
	
	String srcUrl = "http://www.umei.cc/";
	
	private TextView spiderLog;
	
	private File appDir;

	private MessageHandler mHandler=new MessageHandler(this);
	
	private static final int BUMP_MSG = 1;
	private static class MessageHandler extends Handler
	{
		WeakReference<SpiderActivity> mActivity;
		
		MessageHandler(SpiderActivity activity)
		{
			mActivity = new WeakReference<SpiderActivity>(activity);
		}
		
		@Override
		public void handleMessage(Message msg)
		{
			SpiderActivity theActivity=mActivity.get();
			switch (msg.what)
			{
				case BUMP_MSG:
					String msgStr=(String) msg.obj;
					long freeMem=MemoryInfo.getFreeMemInMb(theActivity);
					int memUsedBySpider=Integer.parseInt(msgStr.substring(msgStr.indexOf("Native:")+7, msgStr.indexOf("M pic:")));
					//Log.i(theActivity.LOG_TAG, "mem:"+freeMem+" "+memUsedBySpider);
					
					theActivity.spiderLog.setText("Total:" + MemoryInfo.getTotalMemInMb()
					        + "M Free:" + freeMem
					        + "M\r\n" + msgStr);
					if(msgStr.contains("siteScanCompleted"))
					{
						theActivity.btPauseOrContinue.changeView(R.drawable.start, R.string.start);
					}
					else if(freeMem<50||memUsedBySpider>100)
					{
						theActivity.serviceState=theActivity.STATE_WAIT_DISCONNECT;
						theActivity.sendCmdToSpiderService(CMD_STOP);
					}
				break;
				default:
					super.handleMessage(msg);
			}
		}
		
	};
	

    public Dialog sysFaultAlert(String title, String desc, final boolean exit)
    {
        return new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(desc)
                .setPositiveButton(exit?R.string.exit:R.string.OK,
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog,
                                    int whichButton)
                            {
                                if (exit)
                                {
                                    SpiderActivity.this.finish();
                                }
                            }
                        }).create();
    }

	
	private final static int DLG_NETWORK_PROMPT=0;
    private final static int DLG_STORAGE_ERROR=1;
	
	protected Dialog onCreateDialog(int dlgId)
    {
        switch(dlgId)
        {
            case DLG_NETWORK_PROMPT:
            {
                return sysFaultAlert(getString(R.string.prompt), 
                        getString(R.string.uneffectiveNetworkPrompt), true);
            }
            
            case DLG_STORAGE_ERROR:
            {
                return sysFaultAlert(getString(R.string.prompt), 
                        getString(R.string.badExternalStoragePrompt), true);
            }
        }
        
        return null;
    }
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_spider_crawl);
		
		Log.i(LOG_TAG, "onCreate");
		
		spiderLog = (TextView) findViewById(R.id.tvSpiderLog);
		projBarInit();
		
		firstRunOperat();
		
		appDir=Utils.getDirInExtSto(getString(R.string.appPackageName)+"/download");
		if(appDir==null)
		{
		    showDialog(DLG_STORAGE_ERROR);
		    return;
		}
		
		checkNetwork();
	}
	
	private void checkNetwork()
	{
		new Thread(new Runnable()
		{
			
			@Override
			public void run()
			{
				if(Utils.isNetworkEffective())
				{
					mHandler.post(new Runnable()
					{
						@Override
						public void run()
						{
							startAndBindSpiderService(srcUrl);
						}
					});
				}
				else
				{
					mHandler.post(new Runnable()
					{
						@Override
						public void run()
						{
							showDialog(DLG_NETWORK_PROMPT);
						}
					});
				}
			}
		}).start();
	}
	
	private void firstRunOperat()
	{
		if(ParaConfig.isFirstRun(this))
		{
			Toast.makeText(this, "first run", Toast.LENGTH_SHORT).show();
			ParaConfig.setFirstRun(this);
		}
	}
	
	protected void onStart()
	{
		super.onStart();
		Log.i(LOG_TAG, "onStart");
	}
	
	protected void onResume()
	{
		super.onResume();
		Log.i(LOG_TAG, "onResume");
		
	}
	
	protected void onPause()
	{
		super.onPause();
		Log.i(LOG_TAG, "onPause");
		
	}
	
	protected void onStop()
	{
		super.onStop();
		Log.i(LOG_TAG, "onStop");
		
	}
	
	protected void onDestroy()
	{
		super.onDestroy();
		Log.i(LOG_TAG, "onDestroy");
		
		if(serviceState!=STATE_DISCONNECTED)
		{
	        Log.i(LOG_TAG, "CMD_CLEAR");
    		sendCmdToSpiderService(CMD_CLEAR);
    		unboundSpiderService();
		}
	}
	
	
	//返回至SelSrcActivity
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		
		if (requestCode == REQUST_SRC_URL)
		{
			if (resultCode == RESULT_CANCELED)
			{
				Log.i(LOG_TAG, "REQUST_SRC_URL cancelled!");
			}
			else
			{
				if (data != null)
				{
					srcUrl = data.getAction();
					Log.i(LOG_TAG, "REQUST_SRC_URL " + srcUrl);
					
					if(serviceState==STATE_CONNECTED||serviceState==STATE_WAIT_CONNECT)
					{
						sendCmdToSpiderService(CMD_CLEAR);
						serviceState=STATE_WAIT_DISCONNECT;
					}
					else if(serviceState==STATE_DISCONNECTED||serviceState==STATE_WAIT_DISCONNECT)
					{
						startAndBindSpiderService(srcUrl);
						btPauseOrContinue.changeView(R.drawable.pause, R.string.pause);
						serviceState=STATE_WAIT_CONNECT;
					}
				}
			}
		}
	}
	
	private void projBarInit()
	{
		btPauseOrContinue = (ImageTextButton) findViewById(R.id.buttonPauseOrContinue);
		btPauseOrContinue.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				String cmd = btPauseOrContinue.textView.getText().toString();
				
				if (cmd.equals(getString(R.string.pause)))
				{
					btPauseOrContinue.changeView(R.drawable.start, R.string.goOn);
					
					sendCmdToSpiderService(CMD_PAUSE);
				}
				else
				{
					if (srcUrl != null)
					{
						btPauseOrContinue.changeView(R.drawable.pause, R.string.pause);
						
						startAndBindSpiderService(srcUrl);
						
						if(cmd.equals(getString(R.string.goOn)))
						{
							sendCmdToSpiderService(CMD_CONTINUE);
						}
					}
				}
			}
		});
		
		btSelSrc = (ImageTextButton) findViewById(R.id.buttonSelSrc);
		btSelSrc.setOnClickListener(new View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				Intent intent = new Intent(SpiderActivity.this,
				        SelSrcActivity.class);
				startActivityForResult(intent, REQUST_SRC_URL);
			}
		});
		
		btClear = (ImageTextButton) findViewById(R.id.buttonClear);
		btClear.setOnClickListener(new View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				sendCmdToSpiderService(CMD_CLEAR);
				btPauseOrContinue.changeView(R.drawable.start, R.string.start);
			}
		});
		
	}
	
	/** The primary interface we will be calling on the service. */
	IRemoteSpiderService mService = null;
	
	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			mService = IRemoteSpiderService.Stub.asInterface(service);
			
			// We want to monitor the service for as long as we are
			// connected to it.
			try
			{
				mService.registerCallback(mCallback);
			}
			catch (RemoteException e)
			{
				// In this case the service has crashed before we could even
				// do anything with it; we can count on soon being
				// disconnected (and then reconnected if it can be restarted)
				// so there is no need to do anything here.
			}
			
			serviceState=STATE_CONNECTED;
			Log.i(LOG_TAG, "onServiceConnected");
		}
		
		public void onServiceDisconnected(ComponentName className)
		{
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mService = null;
			
			Log.i(LOG_TAG, "onServiceDisconnected");
			
			if(serviceState==STATE_WAIT_DISCONNECT)
			{
				mHandler.postDelayed(new Runnable()
				{
					
					@Override
					public void run()
					{
						serviceState=STATE_WAIT_CONNECT;
						startAndBindSpiderService(srcUrl);
					}
				}, 500);
			}
			else
			{
				serviceState=STATE_DISCONNECTED;
			}
		}
	};
	
	private void unboundSpiderService()
	{
		// If we have received the service, and hence registered with
		// it, then now is the time to unregister.
		if (mService != null)
		{
			try
			{
				mService.unregisterCallback(mCallback);
			}
			catch (RemoteException e)
			{
				// There is nothing special we need to do if the service
				// has crashed.
			}
			
			// Detach our existing connection.
			unbindService(mConnection);
			Log.i(LOG_TAG, "unbound SpiderService");
		}
		
	}
	
	private Intent sendUrlToSpiderService(String url)
	{
		Log.i(LOG_TAG, "startAndBindSpiderService src:" + url);
		
		Intent spiderIntent = new Intent(IRemoteSpiderService.class.getName());
		spiderIntent.setPackage(IRemoteSpiderService.class.getPackage()
		        .getName());
		
		Bundle bundle = new Bundle();
		bundle.putString(SOURCE_URL_BUNDLE_KEY, url);
		spiderIntent.putExtras(bundle);
		startService(spiderIntent);
		
		return spiderIntent;
	}
	
	private void startAndBindSpiderService(String src)
	{
		bindService(sendUrlToSpiderService(src), mConnection, BIND_ABOVE_CLIENT);
	}
	
	private void sendCmdToSpiderService(int cmd)
	{
		Intent spiderIntent = new Intent(IRemoteSpiderService.class.getName());
		spiderIntent.setPackage(IRemoteSpiderService.class.getPackage()
		        .getName());
		
		Bundle bundle = new Bundle();
		bundle.putInt(CMD_BUNDLE_KEY, cmd);
		spiderIntent.putExtras(bundle);
		startService(spiderIntent);
	}
	
	private IRemoteSpiderServiceCallback mCallback = new IRemoteSpiderServiceCallback.Stub()
	{
		/**
		 * Note that IPC calls are dispatched through a thread pool running in
		 * each process, so the code executing here will NOT be running in our
		 * main thread like most other things -- so, to update the UI, we need
		 * to use a Handler to hop over there.
		 */
		public void reportStatus(String value)
		{
			mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, value));
		}
	};
	
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		
		Log.i(LOG_TAG,
		        (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ? "Landscape"
		                : "Portrait");
	}
	
	private long exitTim = 0;
	
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		Log.i(LOG_TAG, "onKeyDown " + keyCode);
		
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			if (SystemClock.uptimeMillis() - exitTim > 2000)
			{
				Toast.makeText(this, getString(R.string.keyBackExitConfirm)+getString(R.string.app_name)+"。",
				        Toast.LENGTH_SHORT).show();
				;
				exitTim = SystemClock.uptimeMillis();
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
}