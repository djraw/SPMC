package com.semperpax.spmc17;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.RelativeLayout;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class XBMCVideoView extends SurfaceView implements
    SurfaceHolder.Callback
{
  native void _surfaceChanged(SurfaceHolder holder, int format, int width, int height);
  native void _surfaceCreated(SurfaceHolder holder);
  native void _surfaceDestroyed(SurfaceHolder holder);

  private static final String TAG = "XBMCVideoPlayView";

  public boolean mIsCreated = false;
  private RelativeLayout mVideoLayout = null;

  public static XBMCVideoView createVideoView()
  {
    FutureTask<XBMCVideoView> futureResult = new FutureTask<XBMCVideoView>(new Callable<XBMCVideoView>() {
      @Override
      public XBMCVideoView call() throws Exception
      {
        return new XBMCVideoView(Main.MainActivity);
      }
    });

    try
    {
      Main.MainActivity.runOnUiThread(futureResult);
      XBMCVideoView vw = futureResult.get();
      return vw;
    }
    catch (Exception e)
    {
      e.printStackTrace();
      return null;
    }
  }

  public XBMCVideoView(Context context)
  {
    super(context);
    setZOrderMediaOverlay(true);
    getHolder().addCallback(this);
    getHolder().setFormat(PixelFormat.TRANSLUCENT);

    mVideoLayout = (RelativeLayout) Main.MainActivity.findViewById(R.id.VideoLayout);
  }

  public void add()
  {
    Main.MainActivity.runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        mVideoLayout.addView(XBMCVideoView.this, layoutParams);
        mVideoLayout.setBackgroundColor(Color.BLACK);
      }
    });
  }

  public void release()
  {
    Main.MainActivity.runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        mVideoLayout.removeView(XBMCVideoView.this);
        mVideoLayout.setBackgroundColor(Color.TRANSPARENT);
      }
    });
  }

  public Surface getSurface()
  {
    if (!mIsCreated)
    {
      return null;
    } else
    {
      Log.d(TAG, "getSurface() = " + getHolder().getSurface());
      return getHolder().getSurface();
    }
  }

  public void setSurfaceRect(final int left, final int top, final int right, final int bottom)
  {
    Main.MainActivity.runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          RelativeLayout.LayoutParams mp = new RelativeLayout.LayoutParams(getLayoutParams());
          mp.setMargins(left, top, mVideoLayout.getWidth() - right, mVideoLayout.getHeight() - bottom);
          setLayoutParams(mp);
          requestLayout();
        }
        catch (Exception e)
        {
          e.printStackTrace();
        }
      }
    });
  }


  @Override
  public void surfaceCreated(SurfaceHolder holder)
  {
    Log.d(TAG, "Created");
    mIsCreated = true;
    _surfaceCreated(holder);
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width,
      int height)
  {
    if (holder != getHolder())
      return;

    _surfaceChanged(holder, format, width, height);

    Log.d(TAG, "Changed, format:" + format + ", width:" + width
        + ", height:" + height);
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder)
  {
    Log.d(TAG, "Destroyed");
    mIsCreated = false;
    _surfaceDestroyed(holder);
  }
}
