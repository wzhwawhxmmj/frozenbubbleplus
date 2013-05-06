/*
 *                 [[ Frozen-Bubble ]]
 *
 * Copyright © 2000-2003 Guillaume Cottenceau.
 * Java sourcecode - Copyright © 2003 Glenn Sanson.
 * Additional source - Copyright © 2013 Eric Fortin.
 *
 * This code is distributed under the GNU General Public License
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 or 3, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to:
 * Free Software Foundation, Inc.
 * 675 Mass Ave
 * Cambridge, MA 02139, USA
 *
 * Artwork:
 *    Alexis Younes <73lab at free.fr>
 *      (everything but the bubbles)
 *    Amaury Amblard-Ladurantie <amaury at linuxfr.org>
 *      (the bubbles)
 *
 * Soundtrack:
 *    Matthias Le Bidan <matthias.le_bidan at caramail.com>
 *      (the three musics and all the sound effects)
 *
 * Design & Programming:
 *    Guillaume Cottenceau <guillaume.cottenceau at free.fr>
 *      (design and manage the project, whole Perl sourcecode)
 *
 * Java version:
 *    Glenn Sanson <glenn.sanson at free.fr>
 *      (whole Java sourcecode, including JIGA classes
 *             http://glenn.sanson.free.fr/jiga/)
 *
 * Android port:
 *    Pawel Aleksander Fedorynski <pfedor@fuw.edu.pl>
 *    Eric Fortin <videogameboy76 at yahoo.com>
 *    Copyright © Google Inc.
 *
 *          [[ http://glenn.sanson.free.fr/fb/ ]]
 *          [[ http://www.frozen-bubble.org/   ]]
 */

package com.efortin.frozenbubble;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.content.Context;
import android.net.wifi.WifiManager;
 
public class MulticastManager {
  /*
   * Listener interface for various multicast management events.
   */
  // Event types.
  public static final int EVENT_PACKET_RX      = 1;
  public static final int EVENT_RX_FAIL        = 2;
  public static final int EVENT_TX_FAIL        = 3;
  public static final int EVENT_TX_FLOOD       = 4;
  public static final int EVENT_THREAD_STOPPED = 5;
  // Listener user set.
  public interface MulticastListener {
    public abstract void onMulticastEvent(int type, String string);
  }

  private MulticastListener mMulticastListener = null;

  public void setMulticastListener(MulticastListener sl) {
    mMulticastListener = sl;
  }

  private boolean mBroadcast = false;
  private boolean mPaused    = false;
  private boolean mStopped   = false;
  private boolean requestTX  = false;
  private byte[]  mRXBuffer  = null;
  private byte[]  mTXBuffer  = null;
  private int     mPort      = 5500;
  private int     mTimeout   = 10;
  private String  mHostName  = "192.168.0.1";
  private Context mContext   = null;
  private InetAddress mInetAddress = null;
  private MulticastSocket mMulticastSocket = null;
  private WifiManager.MulticastLock multicastLock;
  private MulticastThread mMulticastThread = null;

  /**
   * Multicast manager class constructor.
   * 
   * <p>When created, this class constructs and starts a thread to send
   * and receive WiFi multicast messages.
   * 
   * <p>In order for the multicast manager to actually send and receive
   * WiFi multicast messages, <code>configureMulticast()</code> must be
   * called to configure the multicast socket settings.
   * 
   * @param context
   *        - the application context for the purpose of obtaining WiFi
   *        service access.
   */
  public MulticastManager(Context context) {
    mBroadcast         = false;
    mPaused            = false;
    mStopped           = false;
    requestTX          = false;
    mMulticastListener = null;
    mRXBuffer          = new byte[256];
    mTXBuffer          = null;
    mPort              = 5500;
    mTimeout           = 10;
    mHostName          = "192.168.0.1";
    mContext           = context;
    mInetAddress       = null;
    WifiManager wm = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
    multicastLock = wm.createMulticastLock("myMulticastLock");
    mMulticastSocket = null;
    mMulticastThread = new MulticastThread();
    mMulticastThread.start();
  }

  /**
   * Configure the multicast socket settings.
   * 
   * @param hostAddress
   *        - the host string name given by either the machine name or
   *        IP dotted string address.
   * 
   * @param port
   *        - the port on the host to bind the multicast socket to.
   * 
   * @param broadcast
   *        - if true, then transmitted messages are sent to every peer
   *        on the network, instead of just to the multicast group.
   */
  public void configureMulticast(String hostAddress,
                                 int port,
                                 int timeout,
                                 boolean broadcast) {
    pauseMulticast();
    mBroadcast = broadcast;
    mHostName  = hostAddress;
    mPort      = port;
    mTimeout   = timeout;

    try {
      mInetAddress = InetAddress.getByName(mHostName);
    } catch (UnknownHostException uhe) {
      uhe.printStackTrace();
      mInetAddress = null;
    }

    if (mMulticastSocket == null)
    {
      try {
        mMulticastSocket = new MulticastSocket(mPort);
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }

    if (mMulticastSocket != null)
    {
      try {
        mMulticastSocket.setSoTimeout(mTimeout);
        mMulticastSocket.setBroadcast(mBroadcast);
        mMulticastSocket.joinGroup(mInetAddress);
      } catch (UnknownHostException uhe) {
        uhe.printStackTrace();
      } catch (NullPointerException npe) {
        npe.printStackTrace();
      } catch (SocketException se) {
        se.printStackTrace();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
    resumeMulticast();
  }

  class MulticastThread extends Thread {
    /**
     * This is the thread's <code>run()</code> call.
     * 
     * <p>Send multicast broadcast messages, and read multicast packets
     * from other clients.
     * 
     * <p>To support being able to send and receive packets in the same
     * thread, a socket read timeout must be set, because
     * <code>MulticastSocket.receive()</code> blocks until a packet is
     * received or it times out.  Thus, the timeout is set to 10
     * milliseconds to allow sending of data in a timely manner.
     * 
     * <p>Thus the maximum time between sends is a maximum 10 mSec.  The
     * more messages are getting received, the faster the TX throughput.
     */
    @Override
    public void run() {
      while (!mStopped)
      {
        if (mPaused)
        {
          try {
            synchronized (this) {
              wait();
            }
          } catch (IllegalMonitorStateException imse) {
            // Object must be locked by thread before wait().
            imse.printStackTrace();
          } catch (InterruptedException ie) {
            // wait() was interrupted.  This is expected behavior.
            //Log.i("com.efortin.frozenbubble", ie.getMessage());
          } catch (NullPointerException npe) {
            // Notify was called from within this thread.
            npe.printStackTrace();
          }
        }

        if ((mMulticastSocket != null) && !mPaused) {
          multicastLock.acquire();
          try {
            if (requestTX) {
              DatagramPacket dpTX = new DatagramPacket(mTXBuffer,
                                                       mTXBuffer.length,
                                                       mInetAddress, mPort);
              mMulticastSocket.send(dpTX);
              mTXBuffer = null;
              requestTX = false;
            }
          } catch (NullPointerException npe) {
            npe.printStackTrace();
            if (mMulticastListener != null) {
              mMulticastListener.onMulticastEvent(EVENT_TX_FAIL, null);
            }
          } catch (IOException ioe) {
            ioe.printStackTrace();
            if (mMulticastListener != null) {
              mMulticastListener.onMulticastEvent(EVENT_TX_FAIL, null);
            }
          }

          try {
            DatagramPacket dpRX = new DatagramPacket(mRXBuffer,
                                                     mRXBuffer.length,
                                                     mInetAddress, mPort);
            mMulticastSocket.receive(dpRX);
            String str = new String(dpRX.getData(),0,dpRX.getLength());
            
            if ((str != null) && (mMulticastListener != null)) {
              mMulticastListener.onMulticastEvent(EVENT_PACKET_RX, str);
            }
          } catch (InterruptedIOException iioe) {
            // Receive timeout.  This is expected behavior.
            //Log.i("com.efortin.frozenbubble", iioe.getMessage());
          } catch (NullPointerException npe) {
            npe.printStackTrace();
            if (mMulticastListener != null) {
              mMulticastListener.onMulticastEvent(EVENT_RX_FAIL, null);
            }
          } catch (IOException ioe) {
            ioe.printStackTrace();
            if (mMulticastListener != null) {
              mMulticastListener.onMulticastEvent(EVENT_RX_FAIL, null);
            }
          }
          multicastLock.release();
        }
      }

      if (mMulticastListener != null) {
        mMulticastListener.onMulticastEvent(EVENT_THREAD_STOPPED, null);
      }
    }

    /**
     * This pauses the multicast thread.
     * 
     * <p>The thread must have been initially started with
     * <code>Thread.start()</code>.
     */
    public void pauseThread() {
      // Pauses the thread (see run() above).
      synchronized(this) {
        mPaused = true;
      }
    }

    /**
     * This resumes the multicast thread after it has been paused.
     * 
     * <p>The thread must have been initially started with
     * <code>Thread.start()</code>.
     */
    public void resumeThread() {
      // Starts the thread (see run() above).
      synchronized(this) {
        if (mPaused) {
          mPaused = false;
          this.notify();
        }
      }
    }

    /**
     * This stops the thread.
     */
    public void stopThread() {
      // Stops the thread (see run() above).
      mStopped = true;
      // Notify the thread to wake it up if paused.
      synchronized(this) {
        this.notify();
      }
    }
  }

  private void cleanUp() {
    synchronized(this) {
      try {
        if (mMulticastSocket != null) {
          mMulticastSocket.leaveGroup(mInetAddress);
          mMulticastSocket.close();
          mMulticastSocket = null;
        }
        mStopped = true;
      } catch (NullPointerException npe) {
        npe.printStackTrace();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
    mMulticastListener = null;
    mMulticastThread = null;
  }

  public void sendBroadcast(String string) {
    synchronized(this) {
      if (mTXBuffer == null) {
        mTXBuffer = string.getBytes();
        requestTX = true;
      }
      else {
        if (mMulticastListener != null) {
          mMulticastListener.onMulticastEvent(EVENT_TX_FLOOD, null);
        }
      }
    }
  }

  /**
   * This pauses the multicast manager.
   */
  public void pauseMulticast() {
    mMulticastThread.pauseThread();
  }

  /**
   * This resumes the multicast manager after it has been paused.
   */
  public void resumeMulticast() {
    mMulticastThread.resumeThread();
  }

  /**
   * Stop and <code>join()</code> the multicast thread.
   */
  public void stopMulticast() {
    boolean retry = true;
    // Close and join() the multicast thread.
    mMulticastThread.stopThread();
    while (retry) {
      try {
        mMulticastThread.join();
        retry = false;
      } catch (InterruptedException e) {
        // Keep trying to close the multicast thread.
      }
    }
    cleanUp();
  }
}
