package com.semperpax.spmc17.interfaces;

import android.media.AudioManager.OnAudioFocusChangeListener;
import android.util.Log;

public class XBMCAudioManagerOnAudioFocusChangeListener implements OnAudioFocusChangeListener
{
  native void _onAudioFocusChange(int focusChange);

  @Override
  public void onAudioFocusChange(int focusChange)
  {
    _onAudioFocusChange(focusChange);

  }
}
