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

import java.util.ArrayList;

import android.content.Context;

import com.efortin.frozenbubble.MulticastManager.MulticastListener;

public class NetworkGameManager implements MulticastListener, Runnable {
  /*
   * Following are variables used to keep track of game actions.
   */
  private short actionID;
  private int actionIndex;
  /* Thread running flag */
  private boolean running;
  private MulticastManager session = null;
  private ArrayList<PlayerAction> actionList = null;
  private NetworkListener mNetworkListener = null;

  public interface NetworkListener {
    public abstract void onNetworkEvent(PlayerAction action);
  }

  public void setNetworkListener(NetworkListener ml) {
    mNetworkListener = ml;
  }

  /**
   * This class encapsulates variables used to identify all possible
   * player actions.
   * @author Eric Fortin
   */
  public class PlayerAction {
    public byte  playerID;  // the player ID associated with this action.
    public short actionID;  // the ID of this particular action 
    /*
     * The following are flags associated with player actions.
     * 
     * launchBubble - this flag indicates that the player desires a bubble
     *                launch to occur.  This flag must be set with a valid
     *                aimPosition value.
     * 
     * swapBubble   - this flag indicates that the player desires that the
     *                current launch bubble be swapped with the next
     *                launch bubble.
     */
    public boolean launchBubble;
    public boolean swapBubble;
    /*
     * The following are distance values associated with player actions.
     * 
     * aimPosition - this is the bubble launch position.
     */
    public double aimPosition;

    /**
     * Class constructor.
     * @param playerID - the player ID associated with this action.
     */
    public PlayerAction(byte playerID, short actionID) {
      this.playerID = playerID;
      this.actionID = actionID;
      initialize();
    }

    private void initialize() {
      launchBubble = false;
      swapBubble   = false;
      aimPosition  = 0.0f;
    }
  }

  @Override
  public void onMulticastEvent(int type, String string) {
    /*
     * TODO: process the multicast message.
     */
    /*
     * Wake up the thread.
     */
    synchronized (this) {
      notify();
    }
  }

  public NetworkGameManager(Context myContext) {
    init();
    /*
     * Create the remote player action array.  The actions are inserted
     * chronologically based on message receipt order, but are extracted
     * based on consecutive action ID.
     */
    actionList = new ArrayList<PlayerAction>();
    /*
     * Start an internet multicast session.
     */
    session = new MulticastManager(myContext.getApplicationContext());
    session.setMulticastListener(this);
    session.configureMulticast("239.168.0.1", 5500, 20, false, true);
    session.start();
    /*
     * Start the network game manager thread.
     */
    new Thread(this).start();
  }

  /**
   * Initialize all variables to game start values.
   */
  public void init() {
    running = true;
    actionID = 0;
    actionIndex = -1;
  }

  private void cleanUp() {
    running = false;
    mNetworkListener = null;

    if (actionList != null)
      actionList.clear();
    actionList = null;

    if (session != null)
      session.stopMulticast();
    session = null;
  }

  public synchronized void addAction(PlayerAction newAction) {
    if (running)
      actionList.add(newAction);
  }

  public synchronized PlayerAction getCurrentAction() {
    int listSize = actionList.size();

    for (actionIndex = 0; actionIndex < listSize; actionIndex++) {
      if (actionList.get(actionIndex).actionID == actionID) {
        return actionList.get(actionIndex);
      }
    }
    /*
     * TODO: if the current actionID is not in the list and there exists
     * an action with an ID greater than the current action ID, request
     * a re-issue of the appropriate action.  This can only occur upon
     * message loss from a remote player.
     */
    /*
     * Reset the current action index.
     */
    actionIndex = -1;
    return null;
  }

  /**
   * This must be called after getCurrentAction(), in order to
   * properly initialize the current action index.
   */
  public synchronized void removeCurrentAction() {
    if (actionIndex == -1) {
      return;
    }

    if (actionList.size() > actionIndex) {
      try {
          actionList.remove(actionIndex);
      } catch (IndexOutOfBoundsException ioobe) {
        // TODO - auto-generated exception handler stub.
        //e.printStackTrace();
      }
      actionID++;
      actionIndex = -1;
    }
  }

  public void run() {
    while (running) {
      try {
        synchronized(this) {
          wait(100);
        }
      } catch (InterruptedException e) {
        // TODO - auto-generated exception handler stub.
        //e.printStackTrace();
      }
    }

    if (running) {
      /*
       * Extract the current action in the action queue.
       */
      PlayerAction currentAction = getCurrentAction();
      if (currentAction != null) {
        mNetworkListener.onNetworkEvent(currentAction);
        removeCurrentAction();
      }
    }
  }

  /**
   * Stop the thread <code>run()</code> execution.
   * <p>
   * Interrupt the thread when it is suspended via <code>wait()</code>.
   */
  public void stopThread() {
    cleanUp();

    synchronized(this) {
      this.notify();
    }
  }
}
