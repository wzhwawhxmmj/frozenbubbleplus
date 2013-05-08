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

/**
 * Multicast manager class.
 * <p>
 * This class instantiates a thread to send and receive WiFi multicast
 * datagrams via UDP.
 * <p>
 * In order for the multicast manager to actually send and receive
 * WiFi multicast messages, <code>configureMulticast()</code> must be
 * called to configure the multicast socket settings prior to
 * <code>start()</code>ing the thread.
 * <p>
 * Furthermore, multicast host addresses must be in the IPv4 class
 * D address range, with the leftmost octect being within the 224 to
 * 239 range.  For example, <code>"239.168.0.1"</code> is an actual
 * multicast host address.
 * <p>
 * A typical implementation looks like this:<br>
 * <code>
 * MulticastManager session = <br>
 * new MulticastManager(this.getContext());<br>
 * session.setMulticastListener(this);<br>
 * session.configureMulticast("239.168.0.1", 5500, 20, false, true);
 * <br>session.start();
 * </code>
 * <p>
 * The context will have to be provided when instantiating a
 * MulticastManager object based on the desired context - either via the
 * view context for the current activity with <code>getContext()</code>,
 * or via the application context to ensure the multicast manager
 * lifecycle is tied to the entire application lifecycle with
 * <code>getApplicationContext()</code>, or via
 * <code>getBaseContext()</code> if operating within a nested context.
 *
 * @author Eric Fortin, Wednesday, May 8, 2013
 * 
 */
public class MulticastManager {
  /*
   * Listener interface for various multicast management events.
   *
   * This interface defines the abstract listener method that needs
   * to be instantiated by the registrar, as well as the various
   * events supported by the interface.
   */
  public static final int EVENT_PACKET_RX      = 1;
  public static final int EVENT_RX_FAIL        = 2;
  public static final int EVENT_TX_FAIL        = 3;
  public static final int EVENT_TX_FLOOD       = 4;
  public static final int EVENT_THREAD_STOPPED = 5;

  public interface MulticastListener {
    public abstract void onMulticastEvent(int type, String string);
  }

  private MulticastListener mMulticastListener = null;

  public void setMulticastListener(MulticastListener sl) {
    mMulticastListener = sl;
  }
  /*
   * MulticastManager class member variables.
   */
  private boolean mPaused    = false;
  private boolean mStopped   = false;
  private boolean requestTX  = false;
  private byte[]  mRXBuffer  = null;
  private byte[]  mTXBuffer  = null;
  private int     mPort      = 0;
  private Context mContext   = null;
  private InetAddress mInetAddress = null;
  private MulticastSocket mMulticastSocket = null;
  private WifiManager.MulticastLock multicastLock;
  private MulticastThread mMulticastThread = null;

  /**
   * Multicast manager class constructor.
   * <p>
   * When created, this class instantiates a thread to send and receive
   * WiFi multicast messages.
   * <p>
   * In order for the multicast manager to actually send and receive
   * WiFi multicast messages, <code>configureMulticast()</code> must be
   * called to configure the multicast socket settings prior to
   * <code>start()</code>ing the thread.
   * <p>
   * Furthermore, multicast host addresses must be in the IPv4 class
   * D address range, with the leftmost octect being within the 224 to
   * 239 range.  For example, <code>"239.168.0.1"</code> is an actual
   * multicast host address.
   * <p>
   * A typical implementation looks like this:<br>
   * <code>
   * MulticastManager session = <br>
   * new MulticastManager(this.getContext());<br>
   * session.setMulticastListener(this);<br>
   * session.configureMulticast("239.168.0.1", 5500, 20, false, true);
   * <br>session.start();
   * </code>
   * <p>
   * The context will have to be provided based on the desired context -
   * either the view context for the current activity only via
   * <code>getContext()</code>, the application context to ensure the
   * multicast manager lifecycle is tied to the entire application
   * lifecycle via <code>getApplicationContext()</code>, or via
   * <code>getBaseContext()</code> if operating within a nested context.
   * 
   * @param context
   *        - the context defining the lifecycle of the multicast
   *        manager for the purpose of obtaining WiFi service access.
   */
  public MulticastManager(Context context) {
    mPaused            = false;
    mStopped           = false;
    requestTX          = false;
    mMulticastListener = null;
    mRXBuffer          = new byte[256];
    mTXBuffer          = null;
    mPort              = 0;
    mContext           = context;
    mInetAddress       = null;
    WifiManager wm =
      (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
    multicastLock    = wm.createMulticastLock("myMulticastLock");
    mMulticastSocket = null;
    mMulticastThread = new MulticastThread();
  }

  /**
   * Clean up the multicast manager by closing the multicast socket.
   */
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

  /**
   * Configure the multicast socket settings.
   * 
   * <p>
   * This must be called before <code>start()</code>ing the thread.
   * 
   * @param hostName
   *        - the host string name given by either the machine name or
   *        IP dotted string address.  Multicast addresses must be in
   *        the IPv4 class D address range, with the leftmost octect
   *        being within the 224 to 239 range.
   * 
   * @param port
   *        - the port on the host to bind the multicast socket to.
   * 
   * @param timeout
   *        - the receive blocking timeout.  If zero, receive() blocks
   *        the rest of the thread from executing forever (or until a
   *        datagram is received).
   * 
   * @param broadcast
   *        - if true, then transmitted messages are sent to every peer
   *        on the network, instead of just to the multicast group.
   * 
   * @param loopbackDisable
   *        - if false, locally transmitted messages will be received on
   *        the local socket.
   */
  public void configureMulticast(String hostName,
                                 int port,
                                 int timeout,
                                 boolean broadcast,
                                 boolean loopbackDisable) {
    mPort = port;

    try {
      mInetAddress = InetAddress.getByName(hostName);
    } catch (UnknownHostException uhe) {
      uhe.printStackTrace();
      mInetAddress = null;
    }

    if (mMulticastSocket == null)
    {
      try {
        mMulticastSocket = new MulticastSocket(port);
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }

    if (mMulticastSocket != null)
    {
      try {
        mMulticastSocket.setSoTimeout(timeout);
        mMulticastSocket.setBroadcast(broadcast);
        mMulticastSocket.setLoopbackMode(loopbackDisable);
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
  }

  /**
   * This is the multicast thread declaration.
   * <p>
   * To support being able to send and receive packets in the same
   * thread, a nonzero socket read timeout must be set, because
   * <code>MulticastSocket.receive()</code> blocks until a packet is
   * received or the socket times out.  Thus, if a timeout of zero is
   * set (which is the default, and denotes that the socket will never
   * time out), a datagram will never be sent unless one has just been
   * received.
   * 
   * @author Eric Fortin, Wednesday, May 8, 2013
   * 
   * @see <code>MulticastManager.configureMulticast()</code>
   */
  class MulticastThread extends Thread {
    /**
     * This pauses the multicast thread.
     * <p>
     * The thread must have been initially started with
     * <code>Thread.start()</code>.
     * 
     * @see <code>MulticastThread.run()</code>
     */
    public void pauseThread() {
      synchronized(this) {
        mPaused = true;
      }
    }

    private void receiveDatagram() {
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
    }

    /**
     * This resumes the multicast thread after it has been paused.
     * <p>
     * The thread must have been initially started with
     * <code>Thread.start()</code>.
     * 
     * @see <code>MulticastThread.run()</code>
     */
    public void resumeThread() {
      synchronized(this) {
        if (mPaused) {
          mPaused = false;
          this.notify();
        }
      }
    }

    /**
     * This is the thread's <code>run()</code> call.
     * <p>
     * Send multicast UDP messages, and read multicast datagrams from
     * other clients.
     * <p>
     * To support being able to send and receive packets in the same
     * thread, a nonzero socket read timeout must be set, because
     * <code>MulticastSocket.receive()</code> blocks until a packet is
     * received or the socket times out.  Thus, if a timeout of zero is
     * set (which is the default, and denotes that the socket will never
     * time out), a datagram will never be sent unless one has just been
     * received.
     * <p>
     * Thus the maximum time between datagram transmissions is the
     * socket timeout if no datagrams are being recieved.  If messages
     * are being received, available TX throughput will be increased.
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
          } catch (NullPointerException npe) {
            // Notify was called from within this thread.
            npe.printStackTrace();
          }
        }

        if ((mMulticastSocket != null) && !mPaused) {
          multicastLock.acquire();
          sendDatagram();
          receiveDatagram();
          multicastLock.release();
        }
      }

      if (mMulticastListener != null) {
        mMulticastListener.onMulticastEvent(EVENT_THREAD_STOPPED, null);
      }
    }

    private void sendDatagram() {
      if (requestTX)
      {
        try {
          DatagramPacket dpTX = new DatagramPacket(mTXBuffer,
                                                   mTXBuffer.length,
                                                   mInetAddress, mPort);
          mMulticastSocket.send(dpTX);
          mTXBuffer = null;
          requestTX = false;
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
      }
    }

    /**
     * This stops the thread.
     * 
     * @see <code>MulticastThread.run()</code>
     */
    public void stopThread() {
      mStopped = true;
      // Notify the thread to wake it up if paused.
      synchronized(this) {
        this.notify();
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
   * Start the thread.  This must only be called once per instance.
   */
  public void start() {
    if (mMulticastThread != null) {
      mMulticastThread.start();
    }
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

  /**
   * Send the desired string as a multicast datagram packet.
   * 
   * @param string
   *        - the string to transmit.
   */
  public void transmit(String string) {
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
}
