package com.semperpax.spmc17;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.Build;
import android.app.NativeActivity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.Image;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.media.tv.TvContractCompat;
import android.text.Html;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.semperpax.spmc17.channels.model.Subscription;
import com.semperpax.spmc17.channels.model.XBMCDatabase;
import com.semperpax.spmc17.channels.util.TvUtil;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

public class Main extends NativeActivity implements Choreographer.FrameCallback
{
  private static final String TAG = "SPMC";
  private static final int INTENT_RESULT_PROJECT_CODE = 4658;
  private static final int INTENT_MAKE_BROWSABLE_REQUEST_CODE = 9001;

  private static final int XBMC_EXITCODE_POWERDOWN = 64;
  private static final int XBMC_EXITCODE_RESTARTAPP= 65;
  private static final int XBMC_EXITCODE_REBOOT    = 66;

  public static Main MainActivity = null;
  public XBMCMainView mMainView = null;

  private XBMCSettingsContentObserver mSettingsContentObserver;
  private XBMCInputDeviceListener mInputDeviceListener;
  private XBMCJsonRPC mJsonRPC = null;
  private View mDecorView = null;
  private RelativeLayout mVideoLayout = null;
  private Handler handler = new Handler();
  private Intent mNewIntent = null;
  private XBMCProjection mCaptureProjection = null;
  private XBMCProjection mScreenshotProjection = null;
  private MediaProjection mMediaProjection;
  private int mProjectionIntentId = 0;

  public int mExitCode = 0;
  native void _onNewIntent(Intent intent);
  native void _onActivityResult(int requestCode, int resultCode, Intent resultData);
  native void _doFrame(long frameTimeNanos);
  native void _onCaptureAvailable(Image img);
  native void _onScreenshotAvailable(Image img);
  native void _onVisibleBehindCanceled();
  native void _onMultiWindowModeChanged(boolean isInMultiWindowMode);
  native void _onPictureInPictureModeChanged(boolean isInPictureInPictureMode);
  native void _onAudioDeviceAdded(AudioDeviceInfo[] addedDevices);
  native void _onAudioDeviceRemoved(AudioDeviceInfo[] removedDevices);

  private class MyAudioDeviceCallback extends AudioDeviceCallback
  {
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices)
    {
      if (addedDevices.length != 0)
        _onAudioDeviceAdded(addedDevices);
    }

    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices)
    {
      if (removedDevices.length != 0)
        _onAudioDeviceRemoved(removedDevices);
    }
  }

  private Runnable leanbackUpdateRunnable = new Runnable()
  {
    @Override
    public void run()
    {
      Log.d(TAG, "Updating recommendations");
      new Thread()
      {
        public void run()
        {
          mJsonRPC.updateLeanback(Main.this);
        }
      }.start();
      handler.postDelayed(this, XBMCProperties.getIntProperty("xbmc.leanbackrefresh", 60*60) * 1000);
    }
  };

  /** A signal handler in native code has been triggered. As our last gasp,
   * launch the crash handler (in its own process), because when we return
   * from this function the process will soon exit. */
  void startCrashHandler()
  {
    Log.i("Main", "Launching crash handler");
    startActivity(new Intent(this, XBMCCrashHandler.class));
  }

  void uploadLog()
  {
    Log.i("Main", "Uploading log");

    runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        final ProgressDialog progress = new ProgressDialog(Main.this);
        progress.setMessage(getString(R.string.pushing_log));
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        final AsyncTask task = new AsyncTask<Void, String, String>()
        {
          @Override
          protected String doInBackground(Void... v)
          {
            return XBMCCrashHandler.postLog(Main.this, false);
          }

          @Override
          protected void onProgressUpdate(String... sProgress)
          {
            progress.setMessage(sProgress[0]);
          }

          @Override
          protected void onPostExecute(String id)
          {
            progress.dismiss();
            if (!id.isEmpty())
            {
              AlertDialog.Builder builder = new AlertDialog.Builder(Main.this)
                  .setMessage(Html.fromHtml(MessageFormat.format(getString(R.string.push_log_success), id, getString(R.string.sticky_url) + "/" + id)))
                  .setCancelable(true)
                  .setIcon(android.R.drawable.ic_dialog_info)
                  .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                  {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                      dialog.dismiss();
                    }
                  });

              AlertDialog dialog = builder.show();

              TextView messageView = (TextView) dialog.findViewById(android.R.id.message);
              messageView.setGravity(Gravity.CENTER);
            } else
            {
              new AlertDialog.Builder(Main.this)
                  .setMessage(MessageFormat.format(getString(R.string.get_log_failed), getString(R.string.crash_email)))
                  .setCancelable(true)
                  .setIcon(android.R.drawable.ic_dialog_alert)
                  .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                  {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                      dialog.dismiss();
                    }
                  })
                  .show();
            }
          }
        }.execute();
      }
    });
  }

  public Main()
  {
    super();
    MainActivity = this;
  }

  public void registerMediaButtonEventReceiver()
  {
    AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
    manager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(), XBMCBroadcastReceiver.class.getName()));
  }

  public void unregisterMediaButtonEventReceiver()
  {
    AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
    manager.unregisterMediaButtonEventReceiver(new ComponentName(getPackageName(), XBMCBroadcastReceiver.class.getName()));
  }

  public void takeScreenshot()
  {
    if (mMediaProjection == null)
      return;
    mScreenshotProjection.takeScreenshot();
  }

  public void startProjection()
  {
    final MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    startActivityForResult(projectionManager.createScreenCaptureIntent(), INTENT_RESULT_PROJECT_CODE);
    mProjectionIntentId = INTENT_RESULT_PROJECT_CODE;
  }

  public void Initialize(int resultCode, Intent resultData)
  {
    final MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    mMediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
    mCaptureProjection = new XBMCProjection(this, mMediaProjection);
    mScreenshotProjection = new XBMCProjection(this, mMediaProjection);
  }

  public void startCapture(int width, int height)
  {
    if (mMediaProjection == null)
      return;
    mCaptureProjection.startCapture(width, height);
  }

  public void stopCapture()
  {
    mCaptureProjection.stopCapture();
  }

  private void hideSystemUI()
  {
    mVideoLayout.setFitsSystemWindows(false);

    // Set the IMMERSIVE flag.
    // Set the content to appear under the system bars so that the content
    // doesn't resize when the system bars hide and show.
    mDecorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  private void showSystemUI()
  {

    mVideoLayout.setFitsSystemWindows(true);

    mDecorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    System.loadLibrary("spmc");

    super.onCreate(savedInstanceState);

    setVolumeControlStream(AudioManager.STREAM_MUSIC);

    mSettingsContentObserver = new XBMCSettingsContentObserver(this, handler);
    getApplicationContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, mSettingsContentObserver );

    // Cancel all exising notif
    NotificationManager nMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nMgr.cancelAll();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
    {
      AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
      audioManager.registerAudioDeviceCallback(new MyAudioDeviceCallback(), null);
    }

    if (getPackageManager().hasSystemFeature("android.software.leanback"))
    {
      if (Build.VERSION.SDK_INT >= VERSION_CODES.O)
        TvUtil.scheduleSyncingChannel(this);
      else
      {
        // Leanback
        mJsonRPC = new XBMCJsonRPC();
        handler.removeCallbacks(leanbackUpdateRunnable);
        handler.postDelayed(leanbackUpdateRunnable, 30 * 1000);
      }
    }

    // register the InputDeviceListener implementation
    mInputDeviceListener = new XBMCInputDeviceListener();
    InputManager manager = (InputManager) getSystemService(INPUT_SERVICE);
    manager.registerInputDeviceListener(mInputDeviceListener, handler);

    mDecorView = getWindow().getDecorView();
    mDecorView.setBackground(null);
    getWindow().takeSurface(null);
    setContentView(R.layout.activity_main);
    mVideoLayout = (RelativeLayout)findViewById(R.id.VideoLayout);

    mMainView = new XBMCMainView(this);
    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
    mMainView.setElevation(1);  // Always on Top
    mVideoLayout.addView(mMainView, layoutParams);

    hideSystemUI();
}

  @Override
  protected void onNewIntent(Intent intent)
  {
    super.onNewIntent(intent);
    // Delay until after Resume
    mNewIntent = intent;
  }

  @Override
  public void onStart()
  {
    super.onStart();

    Choreographer.getInstance().removeFrameCallback(this);
    Choreographer.getInstance().postFrameCallback(this);
  }

  @Override
  public void onResume()
  {
    super.onResume();

    if (android.os.Build.VERSION.SDK_INT >= 24)
    {
      if (!isInMultiWindowMode())
        hideSystemUI();
    }
    else
      hideSystemUI();

    // New intent ?
    if (mNewIntent != null)
    {
      handler.postDelayed(new Runnable()
      {
        @Override
        public void run()
        {
          try {
            if (mNewIntent != null)  // Still valid?
              _onNewIntent(mNewIntent);
          } catch (UnsatisfiedLinkError e) {
            Log.e("Main", "Native not registered");
          } finally {
            mNewIntent = null;
          }
        }
      }, 500);
    }
  }

  @Override
  public void onPause()
  {
    super.onPause();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode,
      Intent resultData)
  {
    super.onActivityResult(requestCode, resultCode, resultData);

    if (requestCode == mProjectionIntentId)
    {
      if (resultCode == RESULT_OK)
        Initialize(resultCode, resultData);
      return;
    }
    else if (requestCode == INTENT_MAKE_BROWSABLE_REQUEST_CODE)
    {
      if (resultCode == RESULT_OK) {
        Toast.makeText(this, R.string.channel_added, Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(this, R.string.channel_not_added, Toast.LENGTH_LONG).show();
      }
    }

    _onActivityResult(requestCode, resultCode, resultData);
  }

  @Override
  public void onDestroy()
  {
    // unregister the InputDeviceListener implementation
    InputManager manager = (InputManager) getSystemService(INPUT_SERVICE);
    manager.unregisterInputDeviceListener(mInputDeviceListener);

    getApplicationContext().getContentResolver().unregisterContentObserver(mSettingsContentObserver);

    Log.i(TAG, "onDestroy: exit code = " + mExitCode);
    if (mExitCode == XBMC_EXITCODE_RESTARTAPP)
    {
      Intent mStartActivity = new Intent(this, Main.class);
      int mPendingIntentId = 123456;
      PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
      AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
      mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
    }

    super.onDestroy();
  }

  @Override
  public void onVisibleBehindCanceled()
  {
    _onVisibleBehindCanceled();
    super.onVisibleBehindCanceled();
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode)
  {
    if (android.os.Build.VERSION.SDK_INT >= 24)
    {
      if (isInMultiWindowMode)
        showSystemUI();
      else
        hideSystemUI();
      _onMultiWindowModeChanged(isInMultiWindowMode);
      super.onMultiWindowModeChanged(isInMultiWindowMode);
    }
  }

  @Override
  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode)
  {
    if (android.os.Build.VERSION.SDK_INT >= 24)
    {
      _onPictureInPictureModeChanged(isInPictureInPictureMode);
      super.onPictureInPictureModeChanged(isInPictureInPictureMode);
    }
  }

  @Override
  public void doFrame(long frameTimeNanos)
  {
    Choreographer.getInstance().postFrameCallback(this);
    _doFrame(frameTimeNanos);
  }

  private native void _callNative(long funcAddr, long variantAddr);

  private void runNativeOnUiThread(final long funcAddr, final long variantAddr)
  {
    runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        _callNative(funcAddr, variantAddr);
      }
    });
  }

  // Oreo

  public void addChannel(String title, String url)
  {
    Log.d(TAG, "addChannel: title = " + title + "; url = " + url);
    Subscription subscription = XBMCDatabase.getSubscription(getApplicationContext(), title, url);
    new AddChannelTask(getApplicationContext()).execute(subscription);
  }

  private class AddChannelTask extends AsyncTask<Subscription, Void, Long>
  {

    private final Context mContext;

    AddChannelTask(Context context)
    {
      this.mContext = context;
    }

    @Override
    protected Long doInBackground(Subscription... varArgs)
    {
      List<Subscription> subscriptions = Arrays.asList(varArgs);
      if (subscriptions.size() != 1)
      {
        return -1L;
      }
      Subscription subscription = subscriptions.get(0);
      long channelId = TvUtil.createChannel(mContext, subscription);
      subscription.setChannelId(channelId);
      TvContractCompat.requestChannelBrowsable(mContext, channelId);

      XBMCDatabase.saveSubscription(mContext, subscription);
      // Scheduler listen on channel's uri. Updates after the user interacts with the system
      // dialog.
      TvUtil.scheduleSyncingProgramsForChannel(getApplicationContext(), channelId);
      return channelId;
    }

    @Override
    protected void onPostExecute(Long channelId) {
      super.onPostExecute(channelId);
      promptUserToDisplayChannel(channelId);
    }
  }

  private void promptUserToDisplayChannel(long channelId)
  {
    Intent intent = new Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE);
    intent.putExtra(TvContractCompat.EXTRA_CHANNEL_ID, channelId);
    try
    {
      this.startActivityForResult(intent, INTENT_MAKE_BROWSABLE_REQUEST_CODE);
    }
    catch (ActivityNotFoundException e)
    {
      Log.e(TAG, "Could not start activity: " + intent.getAction(), e);
    }
  }

}
