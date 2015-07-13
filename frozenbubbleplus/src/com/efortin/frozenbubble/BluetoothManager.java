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
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;

/**
 * Bluetooth socket class.
 * <code>BLUETOOTH</code>
 * @author Eric Fortin, Wednesday, May 8, 2013
 */
public class BluetoothManager {
  private static final String LOG_TAG = UDPSocket.class.getSimpleName();

  /*
   * BluetoothManager class member variables.
   */
  private boolean                      paused;
  private boolean                      running;
  private ArrayList<byte[]>            txList        = null;
  private ArrayList<BluetoothListener> listenerList  = null;
  private BluetoothAdapter             mAdapter      = null;
  private BluetoothSocket              mSocket       = null;
  private InputStream                  mInputStream  = null;
  private OutputStream                 mOutputStream = null;
  private Set<BluetoothDevice>         pairedDevices = null;
  private String                       remoteName    = "not available";
  private Thread                       mThread       = null;

  /*
   * Listener interface for various UDP socket events.
   *
   * This interface defines the abstract listener method that needs
   * to be instantiated by the registrar, as well as the various
   * events supported by the interface.
   */
  public interface BluetoothListener {
    public abstract void onBluetoothEvent(byte[] buffer,
                                          int length);
  }

  public void setBluetoothListener(BluetoothListener listener) {
    listenerList.add(listener);
  }

  /**
   * Bluetooth socket class constructor.
   */
  public BluetoothManager() {
    mInputStream  = null;
    mOutputStream = null;
    mThread       = null;
    txList        = null;
    configureBluetoothSocket();
    txList        = new ArrayList<byte[]>();
    listenerList  = new ArrayList<BluetoothListener>();
    mThread       = new Thread(new BluetoothThread(), "mBluetoothThread");
    mThread.start();
  }

  final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes, int length) {
    char[] hexChars = new char[length * 2];
    for ( int j = 0; j < length; j++ ) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2    ] = hexArray[v >>>  4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }

  /**
   * Clean up the UDP socket by stopping the thread, closing the UDP
   * socket and freeing resources.
   */
  public void cleanUp() {
    if (listenerList != null) {
      listenerList.clear();
    }
    listenerList = null;
    stopThread();
    if (mSocket != null) {
      try {
        mSocket.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    mSocket = null;
    if (mInputStream != null) {
      try {
        mInputStream.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    mInputStream = null;
    if (mOutputStream != null) {
      try {
        mOutputStream.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    mOutputStream = null;
    if (txList != null) {
      txList.clear();
    }
    txList = null;
  }

  /**
   * Configure the Bluetooth socket settings.
   */
  private void configureBluetoothSocket() {
    boolean fallback = false;

    mSocket  = null;
    mAdapter = BluetoothAdapter.getDefaultAdapter();

    if (mAdapter != null) {
      if (!mAdapter.isEnabled())
      {
        mAdapter.enable();
      }

      pairedDevices = mAdapter.getBondedDevices();

      for(BluetoothDevice device : pairedDevices) {
        Method       method;
        ParcelUuid[] uuids = null;

        remoteName = device.getName();

        try {
          method = device.getClass().getMethod("getUuids",  (Class<?>[])null);
          uuids  = (ParcelUuid[]) method.invoke(device, (Object[])null);
        } catch (SecurityException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        } catch (NoSuchMethodException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        } catch (IllegalArgumentException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

        for (ParcelUuid parcelUuid : uuids) {
          try {
            mSocket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(parcelUuid.toString()));
            mSocket.connect();
            fallback = false;
            break;
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            mSocket  = null;
            fallback = true;
          }
        }

        if (fallback) {
          try {
            method  = device.getClass().getMethod("createInsecureRfcommSocket", new Class[] {int.class});
            mSocket = (BluetoothSocket) method.invoke(device, 1);
            mSocket.connect();
          } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            mSocket = null;
          } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            mSocket = null;
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            mSocket = null;
          } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            mSocket = null;
          } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            mSocket = null;
          } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            mSocket = null;
          }
        }

        if (mSocket != null) {
          try {
            mInputStream  = mSocket.getInputStream();
            mOutputStream = mSocket.getOutputStream();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
        break;
      }
    }
  }

  public String getLocalName() {
    String name = "not available";

    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

    if (adapter != null) {
      if (!adapter.isEnabled()) {
        name = "bluetooth disabled";
      }
      else {
        name = adapter.getName();
      }
    }

    return name;
  }

  public String getRemoteName() {
    return remoteName;
  }

  /**
   * This is the UDP socket receive and transmit thread declaration.
   * <p>To support being able to send and receive packets in the same
   * thread, a nonzero socket read timeout must be set, because
   * <code>MulticastSocket.receive()</code> blocks until a packet is
   * received or the socket times out.  Thus, if a timeout of zero is
   * set (which is the default, and denotes that the socket will never
   * time out), a datagram will never be sent unless one has just been
   * received.
   * @author Eric Fortin, Wednesday, May 8, 2013
   * @see <code>configureUDPSocket()</code>
   */
  private class BluetoothThread implements Runnable {
    private byte[] rxBuffer = new byte[256];

    /**
     * Receive a UDP datagram.
     * <p>Given a nonzero socket timeout, it is expected behavior for
     * this method to catch an <code>InterruptedIOException</code>.
     * This method posts an <code>EVENT_PACKET_RX</code> event to the
     * registered listener upon datagram receipt.
     */
    private void receiveDatagram() {
      if (!paused && running) try {
        if (mInputStream != null) {
          mInputStream.read(rxBuffer, 0, rxBuffer.length);
          byte[] buffer  = rxBuffer.clone();
          int    length  = rxBuffer.length;
  
          if (!paused && running && (length != 0) && (listenerList != null)) {
            int size = listenerList.size();
            while (--size >= 0) {
              listenerList.get(size).onBluetoothEvent(buffer, length);
            }
            Log.d(LOG_TAG, "received "+length+" bytes: 0x" +
                bytesToHex(buffer, length));
          }
        }
      } catch (NullPointerException npe) {
        npe.printStackTrace();
      } catch (InterruptedIOException iioe) {
        /*
         * Receive timeout.  This is expected behavior.
         */
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }

    /**
     * This is the thread's <code>run()</code> call.
     * <p>Send and receive Bluetooth data.
     */
    @Override
    public void run() {
      paused  = false;
      running = true;

      while (running) {
        if (paused) try {
          synchronized(this) {
            wait();
          }
        } catch (InterruptedException ie) {
          /*
           * Interrupted.  This is expected behavior.
           */
        }

        if (!paused && running) {
          sendDatagram();
          receiveDatagram();
        }
      }
    }

    /**
     * Extract the next buffer from the FIFO transmit list and send it
     * as a UDP datagram packet.
     */
    private void sendDatagram() {
      if (!paused && running && (txList != null) && txList.size() > 0) try {
        byte[] bytes;
        synchronized(txList) {
          bytes = txList.get(0);
        }
        if (mOutputStream != null) {
          mOutputStream.write(bytes, 0, bytes.length);
          Log.d(LOG_TAG, "transmitted "+bytes.length+" bytes: 0x" +
              bytesToHex(bytes, bytes.length));
          synchronized(txList) {
            txList.remove(0);
          }
        }
      } catch (NullPointerException npe) {
        npe.printStackTrace();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }

  public void pause() {
    if (running) {
      paused = true;
    }
  }

  /**
   * Stop and <code>join()</code> the UDP thread.
   */
  private void stopThread() {
    paused  = false;
    running = false;
    if (mThread != null) {
      synchronized(mThread) {
        mThread.interrupt();
      }
    }
    /*
     * Close and join() the Bluetooth thread.
     */
    boolean retry = true;
    while (retry && (mThread != null)) {
      try {
        mThread.join();
        retry = false;
      } catch (InterruptedException e) {
        /*
         * Keep trying to close the Bluetooth thread.
         */
      }
    }
  }

  /**
   * Send the desired byte buffer as a UDP datagram packet.
   * @param buffer - the byte buffer to transmit.
   * @return <code>true</code> if the buffer was successfully added to
   * the outgoing datagram transmit list, <code>false</code> if the the
   * buffer was unable to be added to the transmit list.
   */
  public boolean transmit(byte[] buffer) {
    if ((mThread != null) && running) {
      synchronized(txList) {
        txList.add(buffer);
      }
      return true;
    }
    return false;
  }

  public void unPause() {
    paused = false;
    if (mThread != null) {
      synchronized(mThread) {
        mThread.interrupt();
      }
    }
  }
};
