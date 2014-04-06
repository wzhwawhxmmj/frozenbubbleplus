/*
 *                 [[ Frozen-Bubble ]]
 *
 * Copyright (c) 2000-2003 Guillaume Cottenceau.
 * Java sourcecode - Copyright (c) 2003 Glenn Sanson.
 * Additional source - Copyright (c) 2013 Eric Fortin.
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
 *    Copyright (c) Google Inc.
 *
 *          [[ http://glenn.sanson.free.fr/fb/ ]]
 *          [[ http://www.frozen-bubble.org/   ]]
 */

package com.efortin.frozenbubble;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * Multicast manager class.
 * <p>To perform multicast networking in Android, the project manifest
 * must have the following permissions added to it:<br>
 * ACCESS_WIFI_STATE<br>
 * CHANGE_WIFI_MULTICAST_STATE<br>
 * INTERNET
 * <p>This class instantiates a thread to send and receive WiFi
 * multicast datagrams via UDP.  UDP multicasting requires a WiFi access
 * point - i.e., a router, so multicasting does not perform like WiFi
 * P2P direct, where no access point is required.
 * <p>In order for the multicast manager to actually send and receive
 * WiFi multicast messages, <code>configureMulticast()</code> must be
 * called to configure the multicast socket settings prior to <br>
 * <code>start()</code>ing the thread.
 * <p>Furthermore, multicast host addresses must be in the IPv4 class D
 * address range, with the first octet being within the 224 to 239
 * range.  For example, <code>"239.168.0.1"</code> is a multicast host
 * address.
 * <p>A typical implementation looks like this:<br>
 * <code>
 * MulticastManager session = <br>
 * new MulticastManager(this.getContext());<br>
 * session.setMulticastListener(this);<br>
 * session.configureMulticast("239.168.0.1", 5500, 20, false, true);<br>
 * session.start();
 * </code>
 * <p>The context will have to be provided when instantiating a
 * MulticastManager object based on the desired context - either via the
 * view context for the current activity with <code>getContext()</code>,
 * or via the application context to ensure the multicast manager
 * lifecycle is tied to the entire application lifecycle with
 * <code>getApplicationContext()</code>, or via
 * <code>getBaseContext()</code> if operating within a nested context.
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
  public static final int EVENT_SOCKET_FAIL    = 5;
  public static final int EVENT_THREAD_STOPPED = 6;

  private static final String LOG_TAG = MulticastManager.class.getSimpleName();
  private static final int PORT = 2624;
  private static final String MCAST_STRING_ADDR = "239.168.0.1";
  private static final byte[] MCAST_BYTE_ADDR =
    { (byte) 239, (byte) 168, 0, 1 };

  public interface MulticastListener {
    public abstract void onMulticastEvent(int type, byte[] buffer, int length);
  }

  private MulticastListener mMulticastListener = null;

  public void setMulticastListener(MulticastListener ml) {
    mMulticastListener = ml;
  }

  /*
   * MulticastManager class member variables.
   */
  private byte[]  mTXBuffer = null;
  private boolean rxRunning;
  InetAddress     myInetAddress;
  MulticastSocket receiveSock;
  DatagramSocket  transmitSock;
  Thread          rxThread;
  private WifiManager.MulticastLock mLock;

  /**
   * Multicast manager class constructor.
   * <p>When created, this class instantiates a thread to send and
   * receive WiFi multicast messages.
   * <p>In order for the multicast manager to actually send and receive
   * WiFi multicast messages, <code>configureMulticast()</code> must be
   * called to configure the multicast socket settings prior to
   * <code>start()</code>ing the thread.
   * <p>Furthermore, multicast host addresses must be in the IPv4 class
   * D address range, with the first octet being within the 224 to 239
   * range.  For example, <code>"239.168.0.1"</code> is an actual
   * multicast host address.
   * <p> A typical implementation looks like this:<br>
   * <code>
   * MulticastManager session = <br>
   * new MulticastManager(this.getContext());<br>
   * session.setMulticastListener(this);<br>
   * session.configureMulticast("239.168.0.1", 5500, 20, false, true);
   * <br>session.start();
   * </code>
   * <p>The context will have to be provided based on the desired
   * context - either the view context for the current activity only via
   * <code>getContext()</code>, the application context to ensure the
   * multicast manager lifecycle is tied to the entire application
   * lifecycle via <code>getApplicationContext()</code>, or via
   * <code>getBaseContext()</code> if operating within a nested context.
   * @param context - the context defining the lifecycle of the
   * multicast manager for the purpose of obtaining WiFi service access.
   */
  public MulticastManager(Context context) {
    mMulticastListener = null;
    mTXBuffer          = null;
    rxRunning          = false;

    try {
      myInetAddress = InetAddress.getByAddress(MCAST_STRING_ADDR,
                                               MCAST_BYTE_ADDR);
      transmitSock = new DatagramSocket();
      WifiManager wifi =
          (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
      mLock = wifi.createMulticastLock("mLock");
      mLock.acquire();
      receiveSock = getMulticastSocket();
      receiveSock.setLoopbackMode(true);
      receiveSock.joinGroup(myInetAddress);
      Thread rxThread = new Thread(new ReceiveTask(), "multicast listener");
      rxThread.start();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

  /**
   * Convert raw IPv4 address to string.
   * @param rawBytes - raw IPv4 address.
   * @return A string representation of the raw IP address.
   */
  protected String getIpv4Address(byte[] rawBytes) {
    int i = 4;
    String ipAddress = "";

    for (byte raw : rawBytes) {
      ipAddress += (raw & 0xFF);
      if (--i > 0) {
        ipAddress += ".";
      }
    }

    return ipAddress;
  }

  private MulticastSocket getMulticastSocket() throws IOException
  {
    return new MulticastSocket(new InetSocketAddress(myInetAddress, PORT));
  }

  private class ReceiveTask implements Runnable {

    /**
     * This is the thread's <code>run()</code> call.
     * <p>Send multicast UDP messages, and read multicast datagrams from
     * other clients.
     * <p>To support being able to send and receive packets in the same
     * thread, a nonzero socket read timeout must be set, because
     * <code>MulticastSocket.receive()</code> blocks until a packet is
     * received or the socket times out.  Thus, if a timeout of zero is
     * set (which is the default, and denotes that the socket will never
     * time out), a datagram will never be sent unless one has just been
     * received.
     * <p>Thus the maximum time between datagram transmissions is the
     * socket timeout if no datagrams are being recieved.  If messages
     * are being received, available TX throughput will be increased.
     */
    @Override
    public void run() {
      rxRunning = true;
      byte[] buffer = new byte[256];
      DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
  
      while (rxRunning) {
        try {
          receiveSock.receive(pkt);
  
          if (pkt.getLength() != 0) {
            if (mMulticastListener != null) {
              mMulticastListener.onMulticastEvent(EVENT_PACKET_RX,
                                                  pkt.getData(),
                                                  pkt.getLength());
            }
          }
          Log.d(LOG_TAG, "received "+pkt.getLength()+" bytes");
        } catch (IOException ioe) {
          Log.w(LOG_TAG, "multicast receive thread malfunction", ioe);
        } catch (Exception e) {
          Log.w(LOG_TAG, "multicast receive thread malfunction", e);
        }
      }
    }
  }

  /**
   * This stops the receive task.
   * @see <code>ReceiveTask.run()</code>
   */
  public void stopRxThread() {
    rxRunning = false;
    boolean retry = true;
    /*
     * Now join() the receive thread.
     */
    while (retry && (rxThread != null)) {
      try {
        rxThread.join();
        retry = false;
      } catch (InterruptedException ie) {
        /*
         * Keep trying to close the receive thread.
         */
      }
    }
  }

  /**
   * Send the desired string as a multicast datagram packet.
   * <p>If a transmission is already pending when this method is called,
   * the prior message will be superceded by the current message.  An
   * EVENT_TX_FLOOD event is posted if this occurs.
   * @param string - the string to transmit.
   */
  public void transmit(byte[] buffer) {
    mTXBuffer = buffer;
    transmitPacket();
  }

  public void transmitPacket() {
    Runnable r = new Runnable() {

      public void run() {
        try {
          byte[] bytes = mTXBuffer.clone();
          transmitSock.send(new DatagramPacket(bytes,
                                               bytes.length,
                                               myInetAddress,
                                               PORT));
          Log.d(LOG_TAG, "transmitted "+bytes.length+" bytes");
        } catch (IOException ioe) {
          Log.w(LOG_TAG, "", ioe);
        }
      }
    };

    new Thread(r, "transmit").start();
  }

  public void cleanUp() {
    stopRxThread();
    receiveSock.close();
    transmitSock.close();
    mLock.release();
  }
}
