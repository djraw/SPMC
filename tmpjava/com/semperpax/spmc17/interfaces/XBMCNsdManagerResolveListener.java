package com.semperpax.spmc17.interfaces;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public class XBMCNsdManagerResolveListener implements NsdManager.ResolveListener
{
  native void _onResolveFailed(NsdServiceInfo serviceInfo, int errorCode);
  native void _onServiceResolved(NsdServiceInfo serviceInfo);

  @Override
  public void onResolveFailed (NsdServiceInfo serviceInfo, int errorCode)
  {
    _onResolveFailed(serviceInfo, errorCode);

  }

  @Override
  public void onServiceResolved  (NsdServiceInfo serviceInfo)
  {
    _onServiceResolved (serviceInfo);

  }
}

