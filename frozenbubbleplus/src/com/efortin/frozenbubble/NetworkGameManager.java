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

/**
 * This class manages the actions in a network multiplayer game, by
 * sending the local actions to the remote player, and queueing the
 * incoming remote player actions for enactment on the local machine.
 * <p>
 * The thread created by this class runs automatically upon object
 * creation.  <code>VirtualInput</code> objects must be subsequently
 * attached via <code>registerPlayers()</code> for all the local and
 * remote players.
 * @author Eric Fortin
 *
 */
public class NetworkGameManager implements MulticastListener, Runnable {
  private VirtualInput localPlayer = null;
  private VirtualInput remotePlayer = null;
  /*
   * Following are variables used to keep track of game actions.
   */
  private short localActionID;
  private short remoteActionID;
  /* Thread running flag */
  private boolean running;
  private MulticastManager session = null;
  /*
   * Keep action lists for action retransmission requests and game
   * access.
   */
  private ArrayList<PlayerAction> localActionList = null;
  private ArrayList<PlayerAction> remoteActionList = null;
  private NetworkInterface remoteNetworkInterface;
  private NetworkListener mNetworkListener = null;

  public interface NetworkListener {
    public abstract void onNetworkEvent(NetworkInterface datagram);
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
     * launchAttackBubbles -
     *   This flag indicates that attack bubbles are to be launched.
     *   totalAttackBubbles and attackBubbles[] must be set accordingly.
     * 
     * launchBubble -
     *   This flag indicates that the player desires a bubble launch to
     *   occur.  This flag must be set with a valid aimPosition value,
     *   as well as valid values for launchBubbleColor and
     *   nextBubbleColor.
     * 
     * swapBubble -
     *   This flag indicates that the player desires that the current
     *   launch bubble be swapped with the next launch bubble.  This
     *   flag must be set with a valid aimPosition value, as well as
     *   valid values for launchBubbleColor and nextBubbleColor.
     */
    public boolean launchAttackBubbles;
    public boolean launchBubble;
    public boolean swapBubble;
    public byte    launchBubbleColor;
    public byte    nextBubbleColor;
    public byte    newNextBubbleColor;
    public byte    attackBubbles[] = { -1, -1, -1, -1, -1,
                                       -1, -1, -1, -1, -1,
                                       -1, -1, -1, -1, -1 };
    public short   totalAttackBubbles;
    public double  aimPosition;

    /**
     * Class constructor.
     * @param playerID - the player ID associated with this action.
     */
    /*public PlayerAction(byte playerID, short actionID) {
      this.playerID       = playerID;
      this.actionID       = actionID;
      launchAttackBubbles = false;
      launchBubble        = false;
      swapBubble          = false;
      launchBubbleColor   = -1;
      nextBubbleColor     = -1;
      newNextBubbleColor  = -1;
      totalAttackBubbles  = 0; 
      aimPosition         = 0.0d;
    }*/

    /**
     * Class constructor.
     * @param action - PlayerAction object to copy to this instance.
     */
    public PlayerAction(PlayerAction action) {
      copy(action);
    }

    public void copy(PlayerAction action) {
      if (action != null) {
        this.playerID            = action.playerID;
        this.actionID            = action.actionID;
        this.launchAttackBubbles = action.launchAttackBubbles;
        this.launchBubble        = action.launchBubble;
        this.swapBubble          = action.swapBubble;
        this.launchBubbleColor   = action.launchBubbleColor;
        this.nextBubbleColor     = action.nextBubbleColor;
        this.newNextBubbleColor  = action.newNextBubbleColor;
        this.totalAttackBubbles  = action.totalAttackBubbles;
        this.aimPosition         = action.aimPosition;
  
        for (int index = 0; index < 15; index++) {
          this.attackBubbles[index] = action.attackBubbles[index];
        }
      }
    }
  }

  public class GameFieldData {
    public byte     playerID           = -1;
    public short    actionID           = -1;
    public byte     launchBubbleColor  = -1;
    public byte     nextBubbleColor    = -1;
    public byte     newNextBubbleColor = -1;
    /*
     * The game field is represented by a 2-dimensional array, with 8
     * rows and 12 columns.  This is displayed on the screen as 12 rows
     * with 8 columns.
     */
    public byte[][] gameField =
      {{ -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }};

    /**
     * Class constructor.
     * @param action - PlayerAction object to copy to this instance.
     */
    public GameFieldData(GameFieldData fieldData) {
      copy(fieldData);
    }

    public void copy(GameFieldData fieldData) {
      if (fieldData != null) {
        this.playerID            = fieldData.playerID;
        this.actionID            = fieldData.actionID;
        this.launchBubbleColor   = fieldData.launchBubbleColor;
        this.nextBubbleColor     = fieldData.nextBubbleColor;
        this.newNextBubbleColor  = fieldData.newNextBubbleColor;
  
        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 15; y++) {
            this.gameField[x][y] = fieldData.gameField[x][y];
          }
        }
      }
    }
  }

  public class NetworkInterface {
    public byte          messageId;
    public PlayerAction  playerAction;
    public GameFieldData gameFieldData;

    public void init() {
      messageId = -1;
      playerAction = null;
      gameFieldData = null;
    }
  }

  @Override
  public void onMulticastEvent(int type, String string) {
    /*
     * TODO: process the multicast message.
     */
    byte[] tempArray = string.getBytes();
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
    remoteActionList = new ArrayList<PlayerAction>();
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
    localPlayer = null;
    remotePlayer = null;
    localActionID = 0;
    remoteActionID = 0;
    remoteNetworkInterface.init();
    running = true;
  }

  private void cleanUp() {
    localPlayer = null;
    remotePlayer = null;
    remoteNetworkInterface.init();
    running = false;
    mNetworkListener = null;

    if (localActionList != null)
      localActionList.clear();
    localActionList = null;

    if (remoteActionList != null)
      remoteActionList.clear();
    remoteActionList = null;

    if (session != null)
      session.stopMulticast();
    session = null;
  }

  private synchronized void addAction(PlayerAction newAction) {
    if (running && (localPlayer != null) && (remotePlayer != null)) {
      if (newAction.playerID == localPlayer.playerID)
        localActionList.add(newAction);
      else if (newAction.playerID == remotePlayer.playerID)
        remoteActionList.add(newAction);
    }
  }

  private synchronized PlayerAction getRemoteAction() {
    int listSize = remoteActionList.size();

    remoteNetworkInterface.playerAction = null;

    for (int index = 0; index < listSize; index++) {
      if (remoteActionList.get(index).actionID == remoteActionID) {
        remoteNetworkInterface.playerAction =
            new PlayerAction(remoteActionList.get(index));
        try {
            remoteActionList.remove(index);
        } catch (IndexOutOfBoundsException ioobe) {
          // TODO - auto-generated exception handler stub.
          //e.printStackTrace();
        }
        remoteActionID++;
      }
    }
    /*
     * TODO: if the current actionID is not in the list and there exists
     * an action with an ID greater than the current action ID, request
     * a re-issue of the appropriate action.  This can only occur upon
     * message loss from a remote player.
     */
    return (remoteNetworkInterface.playerAction);
  }

  public void registerPlayers(VirtualInput localPlayer,
                              VirtualInput remotePlayer) {
    this.localPlayer = localPlayer;
    this.remotePlayer = remotePlayer;
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
       * Extract the current remote player action in the action queue.
       */
      /*
       * TODO: check for game field synchronization.
       */
      if (getRemoteAction() != null) {
        mNetworkListener.onNetworkEvent(remoteNetworkInterface);
      }
    }
  }

  /**
   * Transmit the local player action to the remote player.  The action
   * counter identifier is incremented automatically.
   * @param playerId - the local player ID.
   * @param sendAttack - set <code>true</code> to launch attack bubbles.
   * @param launch - set <code>true</code> to launch a bubble.
   * @param swap - set <code>true</code> to swap the launch bubble with
   *   the next bubble.
   * @param launchColor - the launch bubble color.
   * @param nextColor - the next bubble color.
   * @param newNextColor - when a bubble is launched, this is the new
   *   next bubble color.  The prior next color is promoted to the
   *   launch bubble color.
   * @param totalAttackBubbles - the number of attack bubbles stored on
   *   the attack bar.
   * @param attackBubbles - the array of attack bubble colors.  A value
   *   of -1 denotes no color, and thus no attack bubble at that column.
   * @param aimPosition - the launcher aim aimPosition.
   */
  public void sendLocalPlayerAction(byte playerID,
                                    boolean sendAttack,
                                    boolean launch,
                                    boolean swap,
                                    byte launchColor,
                                    byte nextColor,
                                    byte newNextColor,
                                    byte totalAttackBubbles,
                                    byte attackBubbles[],
                                    double aimPosition) {
    PlayerAction tempAction = new PlayerAction(null);
    localActionID++;
    tempAction.playerID = playerID;
    tempAction.actionID = localActionID;
    tempAction.launchAttackBubbles = sendAttack;
    tempAction.launchBubble = launch;
    tempAction.swapBubble = swap;
    tempAction.launchBubbleColor = launchColor;
    tempAction.nextBubbleColor = nextColor;
    tempAction.newNextBubbleColor = newNextColor;
    tempAction.totalAttackBubbles = totalAttackBubbles;
    if (attackBubbles != null)
      for (int index = 0;index < 15; index++)
        tempAction.attackBubbles[index] = attackBubbles[index];
    tempAction.aimPosition = aimPosition;
    transmitAction(tempAction);
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

  /**
   * Transmit the local player action to the remote player via the
   * network interface
   * @param action - the player action to transmit.
   */
  private void transmitAction(PlayerAction action) {
    /*
     * TODO: Send the player action via the multicast manager.
     * TODO: Either parse it into a string or a byte array.
     */
    //session.transmit(actionString);
  }
}
