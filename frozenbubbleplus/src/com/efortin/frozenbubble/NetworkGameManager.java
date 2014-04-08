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

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.jfedor.frozenbubble.FrozenBubble;

import android.content.Context;
import android.content.SharedPreferences;

import com.efortin.frozenbubble.MulticastManager.MulticastListener;

/**
 * This class manages the actions in a network multiplayer game by
 * sending the local actions to the remote player, and queueing the
 * incoming remote player actions for enactment on the local machine.
 * <p>The thread created by this class will not <code>run()</code> until
 * <code>VirtualInput</code> objects for each player are attached via
 * <code>startNetworkGame()</code>.
 * @author Eric Fortin
 *
 */
public class NetworkGameManager extends Thread implements MulticastListener {
  /*
   * Message identifier definitions.
   */
  public static final byte MSG_ID_JOIN_GAME   = 1;
  public static final byte MSG_ID_REQUEST     = 2;
  public static final byte MSG_ID_PREFS       = 3;
  public static final byte MSG_ID_REBROADCAST = 4;
  public static final byte MSG_ID_ACTION      = 5;
  public static final byte MSG_ID_GAME_FIELD  = 6;
  public static final int  GAMEFIELD_BYTES    = 105;
  public static final int  ACTION_BYTES       = 36;

  private boolean          running;
  private boolean          join_rx;
  private boolean          join_tx;
  private boolean          prefs_rx;
  private boolean          prefs_tx;
  private boolean          field_rx;
  private boolean          field_tx;
  private short            localActionID;
  private short            remoteActionID;
  private Context          mContext;
  private Preferences      localPrefs;
  private Preferences      remotePrefs;
  private VirtualInput     localPlayer = null;
  private VirtualInput     remotePlayer = null;
  private MulticastManager session = null;
  /*
   * Keep action lists for action retransmission requests and game
   * access.
   */
  private ArrayList<PlayerAction> localActionList = null;
  private ArrayList<PlayerAction> remoteActionList = null;
  private NetGameInterface remoteNetGameInterface;

  /**
   * This class represents the current state of an individual player
   * game field.  The game field consists of the launcher bubbles, the
   * bubbles fixed to the game field, and the the attack bar.
   * @author Eric Fortin
   *
   */
  public class GameFieldData {
    public byte     playerID           = -1;
    public short    actionID           = -1;
    public byte     compressorSteps    = 0;
    public byte     launchBubbleColor  = -1;
    public byte     nextBubbleColor    = -1;
    public byte     newNextBubbleColor = -1;
    public short    totalAttackBubbles = 0;
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
     * @param action - GameFieldData object to copy to this instance.
     */
    public GameFieldData(GameFieldData fieldData) {
      copyFromFieldData(fieldData);
    }

    /**
     * Class constructor.
     * @param buffer - buffer contents to copy to this instance.
     */
    public GameFieldData(byte[] buffer, int startIndex) {
      copyFromBuffer(buffer, startIndex);
    }

    /**
     * Copy the contents of the supplied field data to this field data.
     * @param action - the action to copy
     */
    public void copyFromFieldData(GameFieldData fieldData) {
      if (fieldData != null) {
        this.playerID            = fieldData.playerID;
        this.actionID            = fieldData.actionID;
        this.compressorSteps     = fieldData.compressorSteps;
        this.launchBubbleColor   = fieldData.launchBubbleColor;
        this.nextBubbleColor     = fieldData.nextBubbleColor;
        this.newNextBubbleColor  = fieldData.newNextBubbleColor;
        this.totalAttackBubbles  = fieldData.totalAttackBubbles;

        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 15; y++) {
            this.gameField[x][y] = fieldData.gameField[x][y];
          }
        }
      }
    }

    /**
     * Copy the contents of the buffer to this field data.
     * @param buffer - the buffer to convert and copy
     * @param startIndex - the start of the data to convert
     */
    public void copyFromBuffer(byte[] buffer, int startIndex) {
      byte[] shortBytes  = new byte[2];

      if (buffer != null) {
        this.playerID            = buffer[startIndex++];
        shortBytes[0]            = buffer[startIndex++];
        shortBytes[1]            = buffer[startIndex++];
        this.actionID            = toShort(shortBytes);
        this.compressorSteps     = buffer[startIndex++];
        this.launchBubbleColor   = buffer[startIndex++];
        this.nextBubbleColor     = buffer[startIndex++];
        this.newNextBubbleColor  = buffer[startIndex++];
        shortBytes[0]            = buffer[startIndex++];
        shortBytes[1]            = buffer[startIndex++];
        this.totalAttackBubbles  = toShort(shortBytes);

        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 15; y++) {
            this.gameField[x][y] = buffer[startIndex++];
          }
        }
      }
    }

    /**
     * Copy the contents of this field data to the buffer.
     * @param buffer - the buffer to copy to
     * @param startIndex - the start location to copy to
     */
    public void copyToBuffer(byte[] buffer, int startIndex) {
      byte[] shortBytes  = new byte[2];

      if (buffer != null) {
        buffer[startIndex++] = this.playerID;
        toByteArray(this.actionID, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];
        buffer[startIndex++] = this.compressorSteps;
        buffer[startIndex++] = this.launchBubbleColor;
        buffer[startIndex++] = this.nextBubbleColor;
        buffer[startIndex++] = this.newNextBubbleColor;
        toByteArray(this.totalAttackBubbles, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];

        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 15; y++) {
            buffer[startIndex++] = this.gameField[x][y];
          }
        }
      }
    }
  };

  /**
   * This class encapsulates variables used to identify all possible
   * player actions.
   * @author Eric Fortin
   *
   */
  public class PlayerAction {
    public byte  playerID;  // the player ID associated with this action.
    public short actionID;  // the ID of this particular action 
    /*
     * The following three booleans are flags associated with player
     * actions.
     * 
     * compress -
     *   This flag indicates whether to lower the game field compressor.
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
    public boolean compress;
    public boolean launchBubble;
    public boolean swapBubble;
    public byte    launchBubbleColor;
    public byte    nextBubbleColor;
    public byte    newNextBubbleColor;
    public short   addAttackBubbles;
    public short   totalAttackBubbles;
    public byte    attackBubbles[] = { -1, -1, -1, -1, -1,
                                       -1, -1, -1, -1, -1,
                                       -1, -1, -1, -1, -1 };
    public double  aimPosition;

    /**
     * Class constructor.
     * @param action - PlayerAction object to copy to this instance.
     */
    public PlayerAction(PlayerAction action) {
      copyFromAction(action);
    }

    /**
     * Class constructor.
     * @param buffer - buffer contents to copy to this instance.
     */
    public PlayerAction(byte[] buffer, int startIndex) {
      copyFromBuffer(buffer, startIndex);
    }

    /**
     * Copy the contents of the supplied action to this action.
     * @param action - the action to copy.
     */
    public void copyFromAction(PlayerAction action) {
      if (action != null) {
        this.playerID           = action.playerID;
        this.actionID           = action.actionID;
        this.compress           = action.compress;
        this.launchBubble       = action.launchBubble;
        this.swapBubble         = action.swapBubble;
        this.launchBubbleColor  = action.launchBubbleColor;
        this.nextBubbleColor    = action.nextBubbleColor;
        this.newNextBubbleColor = action.newNextBubbleColor;
        this.addAttackBubbles   = action.addAttackBubbles;
        this.totalAttackBubbles = action.totalAttackBubbles;

        for (int index = 0; index < 15; index++) {
          this.attackBubbles[index] = action.attackBubbles[index];
        }

        this.aimPosition        = action.aimPosition;
      }
    }

    /**
     * Copy the contents of the buffer to this action.
     * @param buffer - the buffer to convert and copy.
     * @param startIndex - the start of the data to convert.
     */
    public void copyFromBuffer(byte[] buffer, int startIndex) {
      byte[] shortBytes  = new byte[2];
      byte[] doubleBytes = new byte[8];

      if (buffer != null) {
        this.playerID           = buffer[startIndex++];
        shortBytes[0]           = buffer[startIndex++];
        shortBytes[1]           = buffer[startIndex++];
        this.actionID           = toShort(shortBytes);
        this.compress           = buffer[startIndex++] == 1;
        this.launchBubble       = buffer[startIndex++] == 1;
        this.swapBubble         = buffer[startIndex++] == 1;
        this.launchBubbleColor  = buffer[startIndex++];
        this.nextBubbleColor    = buffer[startIndex++];
        this.newNextBubbleColor = buffer[startIndex++];
        shortBytes[0]           = buffer[startIndex++];
        shortBytes[1]           = buffer[startIndex++];
        this.addAttackBubbles   = toShort(shortBytes);
        shortBytes[0]           = buffer[startIndex++];
        shortBytes[1]           = buffer[startIndex++];
        this.totalAttackBubbles = toShort(shortBytes);

        for (int index = 0; index < 15; index++) {
          this.attackBubbles[index] = buffer[startIndex++];
        }

        for (int index = 0; index < 8; index++) {
          doubleBytes[index] = buffer[startIndex++];
        }

        this.aimPosition        = toDouble(doubleBytes);
      }
    }

    /**
     * Copy the contents of this action to the buffer.
     * @param buffer - the buffer to copy to.
     * @param startIndex - the start location to copy to.
     */
    public void copyToBuffer(byte[] buffer, int startIndex) {
      byte[] shortBytes  = new byte[2];
      byte[] doubleBytes = new byte[8];

      if (buffer != null) {
        buffer[startIndex++] = this.playerID;
        toByteArray(this.actionID, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];
        buffer[startIndex++] = (byte) ((this.compress == true)?1:0);
        buffer[startIndex++] = (byte) ((this.launchBubble == true)?1:0);
        buffer[startIndex++] = (byte) ((this.swapBubble == true)?1:0);
        buffer[startIndex++] = this.launchBubbleColor;
        buffer[startIndex++] = this.nextBubbleColor;
        buffer[startIndex++] = this.newNextBubbleColor;
        toByteArray(this.addAttackBubbles, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];
        toByteArray(this.totalAttackBubbles, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];

        for (int index = 0; index < 15; index++) {
          buffer[startIndex++] = this.attackBubbles[index];
        }

        toByteArray(this.aimPosition, doubleBytes);

        for (int index = 0; index < 8; index++) {
          buffer[startIndex++] = doubleBytes[index];
        }
      }
    }
  };

  public class NetGameInterface {
    public byte          messageId;
    public boolean       gotAction;
    public boolean       gotFieldData;
    public PlayerAction  playerAction;
    public GameFieldData gameFieldData;

    public void cleanUp() {
      gotAction = false;
      gotFieldData = false;
      playerAction = null;
      gameFieldData = null;
    }

    public NetGameInterface() {
      messageId = -1;
      playerAction = new PlayerAction(null);
      gameFieldData = new GameFieldData(null);
    }
  };

  @Override
  public void onMulticastEvent(int type, byte[] buffer, int length) {
    /*
     * Process the multicast message.
     */
    if ((type == MulticastManager.EVENT_PACKET_RX) &&
        (buffer != null)) {
      byte msgId = buffer[0];

      /*
       * If the message contains game preferences, then the remote
       * player is player 1.  The game preferences are set per player 1.
       */
      if ((msgId == MSG_ID_PREFS) && (length > Preferences.PREFS_BYTES)) {
        copyPrefsFromBuffer(remotePrefs, buffer, 1);
        PreferencesActivity.setFrozenBubblePrefs(remotePrefs);
        prefs_rx = true;
      }

      /*
       * If the message contains a game action, add it to the
       * appropriate action list.
       */
      if ((msgId == MSG_ID_ACTION) && (length > ACTION_BYTES)) {
        addAction(new PlayerAction(buffer, 1));
      }

      /*
       * Wake up the thread.
       */
      synchronized (this) {
        notify();
      }
    }
  }

  public NetworkGameManager(Context myContext) {
    init(myContext);
    /*
     * Create the player action arrays.  The actions are inserted
     * chronologically based on message receipt order, but are extracted
     * based on consecutive action ID.
     */
    localActionList  = new ArrayList<PlayerAction>();
    remoteActionList = new ArrayList<PlayerAction>();
    /*
     * Start an internet multicast session.
     */
    session = new MulticastManager(mContext.getApplicationContext());
    session.setMulticastListener(this);
  }

  /**
   * Add a player action to the appropriate action list.  Do not allow
   * duplicate actions to populate the lists.
   * @param newAction - the action to add to the appropriate list.
   */
  private synchronized void addAction(PlayerAction newAction) {
    if ((localPlayer != null) && (remotePlayer != null)) {
      if (newAction.playerID == localPlayer.playerID) {
        int listSize = localActionList.size();

        for (int index = 0; index < listSize; index++) {
          /*
           * If a match is found, return from this function without
           * adding the action to the list since it is a duplicate.
           */
          if (localActionList.get(index).actionID == newAction.actionID) {
            return;
          }
        }
        localActionList.add(newAction);
      }
      else if (newAction.playerID == remotePlayer.playerID) {
        int listSize = remoteActionList.size();

        for (int index = 0; index < listSize; index++) {
          /*
           * If a match is found, return from this function without
           * adding the action to the list since it is a duplicate.
           */
          if (remoteActionList.get(index).actionID == newAction.actionID) {
            return;
          }
        }
        remoteActionList.add(newAction);
      }
    }
  }

  public void cleanUp() {
    stopThread();
    /*
     * Restore the local game preferences in the event that they were
     * overwritten by the remote player's preferences.
     */
    if (localPrefs != null) {
      SharedPreferences sp =
          mContext.getSharedPreferences(FrozenBubble.PREFS_NAME,
                                        Context.MODE_PRIVATE);
      PreferencesActivity.setFrozenBubblePrefs(localPrefs, sp);
    }

    localPrefs = null;
    remotePrefs = null;
    localPlayer = null;
    remotePlayer = null;

    if (remoteNetGameInterface != null)
      remoteNetGameInterface.cleanUp();
    remoteNetGameInterface = null;

    if (localActionList != null)
      localActionList.clear();
    localActionList = null;

    if (remoteActionList != null)
      remoteActionList.clear();
    remoteActionList = null;

    if (session != null)
      session.cleanUp();
    session = null;
  }

  /**
   * Copy the contents of the buffer to the designated preferences.
   * @param buffer - the buffer to convert and copy.
   * @param startIndex - the start of the data to convert.
   */
  private void copyPrefsFromBuffer(Preferences prefs,
                                   byte[] buffer,
                                   int startIndex) {
    byte[] intBytes = new byte[4];

    if (buffer != null) {
      intBytes[0]      = buffer[startIndex++];
      intBytes[1]      = buffer[startIndex++];
      intBytes[2]      = buffer[startIndex++];
      intBytes[3]      = buffer[startIndex++];
      prefs.collision  = toInt(intBytes);
      prefs.compressor = buffer[startIndex++] == 1;
      intBytes[0]      = buffer[startIndex++];
      intBytes[1]      = buffer[startIndex++];
      intBytes[2]      = buffer[startIndex++];
      intBytes[3]      = buffer[startIndex++];
      prefs.difficulty = toInt(intBytes);
      prefs.dontRushMe = buffer[startIndex++] == 1;
      prefs.fullscreen = buffer[startIndex++] == 1;
      intBytes[0]      = buffer[startIndex++];
      intBytes[1]      = buffer[startIndex++];
      intBytes[2]      = buffer[startIndex++];
      intBytes[3]      = buffer[startIndex++];
      prefs.gameMode   = toInt(intBytes);
      prefs.musicOn    = buffer[startIndex++] == 1;
      prefs.soundOn    = buffer[startIndex++] == 1;
      intBytes[0]      = buffer[startIndex++];
      intBytes[1]      = buffer[startIndex++];
      intBytes[2]      = buffer[startIndex++];
      intBytes[3]      = buffer[startIndex++];
      prefs.gameMode   = toInt(intBytes);
    }
  }

  /**
   * Copy the contents of this preferences object to the buffer.
   * @param buffer - the buffer to copy to.
   * @param startIndex - the start location to copy to.
   */
  private void copyPrefsToBuffer(Preferences prefs,
                                 byte[] buffer,
                                 int startIndex) {
    byte[] intBytes = new byte[4];

    if (buffer != null) {
      toByteArray(prefs.collision, intBytes);
      buffer[startIndex++] = intBytes[0];
      buffer[startIndex++] = intBytes[1];
      buffer[startIndex++] = intBytes[3];
      buffer[startIndex++] = intBytes[4];
      buffer[startIndex++] = (byte) ((prefs.compressor == true)?1:0);
      toByteArray(prefs.difficulty, intBytes);
      buffer[startIndex++] = intBytes[0];
      buffer[startIndex++] = intBytes[1];
      buffer[startIndex++] = intBytes[3];
      buffer[startIndex++] = intBytes[4];
      buffer[startIndex++] = (byte) ((prefs.dontRushMe == true)?1:0);
      buffer[startIndex++] = (byte) ((prefs.fullscreen == true)?1:0);
      toByteArray(prefs.gameMode, intBytes);
      buffer[startIndex++] = intBytes[0];
      buffer[startIndex++] = intBytes[1];
      buffer[startIndex++] = intBytes[3];
      buffer[startIndex++] = intBytes[4];
      buffer[startIndex++] = (byte) ((prefs.musicOn == true)?1:0);
      buffer[startIndex++] = (byte) ((prefs.soundOn == true)?1:0);
      toByteArray(prefs.targetMode, intBytes);
      buffer[startIndex++] = intBytes[0];
      buffer[startIndex++] = intBytes[1];
      buffer[startIndex++] = intBytes[3];
      buffer[startIndex++] = intBytes[4];
    }
  }

  public synchronized PlayerAction getRemoteActionPreview() {
    PlayerAction tempAction = null;
    int listSize = remoteActionList.size();

    for (int index = 0; index < listSize; index++) {
      /*
       * When a match is found, return a reference to it.
       */
      if (remoteActionList.get(index).actionID == remoteActionID) {
        tempAction = remoteActionList.get(index);
        break;
      }
    }

    return (tempAction);
  }

  private synchronized boolean getRemoteAction() {
    boolean gotAction = false;
    int listSize = remoteActionList.size();

    for (int index = 0; index < listSize; index++) {
      /*
       * When a match is found, copy the necessary element from the
       * list, remove it, and exit the loop.
       */
      if (remoteActionList.get(index).actionID == remoteActionID) {
        remoteNetGameInterface.playerAction.copyFromAction(remoteActionList.get(index));
        try {
            remoteActionList.remove(index);
        } catch (IndexOutOfBoundsException ioobe) {
          // TODO - auto-generated exception handler stub.
          //e.printStackTrace();
        }
        gotAction = true;
        remoteActionID++;
        break;
      }
    }
    /*
     * TODO: if the current actionID is not in the list and there exists
     * an action with an ID greater than the current action ID, request
     * a re-issue of the appropriate action.  This can only occur upon
     * message loss from a remote player.
     */
    return (gotAction);
  }

  /**
   * Initialize all variables to game start values.
   */
  public void init(Context myContext) {
    join_rx = false;
    join_tx = false;
    prefs_rx = false;
    prefs_tx = false;
    field_rx = false;
    field_tx = false;
    mContext = myContext;
    localPrefs = new Preferences();
    remotePrefs = new Preferences();
    localPlayer = null;
    remotePlayer = null;
    SharedPreferences sp =
        myContext.getSharedPreferences(FrozenBubble.PREFS_NAME,
                                       Context.MODE_PRIVATE);
    PreferencesActivity.getFrozenBubblePrefs(localPrefs, sp);
    /*
     * Initialize the local action ID to zero, as it is pre-incremented
     * for every action transmitted to the remote player.
     * 
     * Initialize the remote action ID to 1, as it must be the first
     * action ID received from the remote player.
     */
    localActionID = 0;
    remoteActionID = 1;
    remoteNetGameInterface = new NetGameInterface();
  }

  /**
   * This function is called from manager thread's <code>run()</code>
   * method.  This performs the network handshaking amongst peers to
   * ensure proper game synchronization and operation.
   */
  private void manageNetworkGame() {
    if (!join_tx) {
      if (transmitJoinGame()) {
        join_tx = true;
      }
    }
  }

  /**
   * This function obtains the remote player actions and returns them
   * to the caller.  This must be called periodically as it is assumed
   * that the actions will be performed at the most appropriate time as
   * determined by the owner that instantiated this object.
   * @return A network game interface that supplies all possible remote
   * player actions.
   */
  public NetGameInterface monitorNetwork() {
    remoteNetGameInterface.gotAction = false;
    remoteNetGameInterface.gotFieldData = false;
    remoteNetGameInterface.gotAction = getRemoteAction();
    return (remoteNetGameInterface);
  }

  /**
   * This is the network game manager thread's <code>run()</code> call.
   */
  @Override
  public void run() {
    running = true;

    while (running)
    {
      try {
        synchronized(this) {
          wait(100);
        }
      } catch (InterruptedException ie) {
        /*
         * Receive timeout.  This is expected behavior.
         */
      }
      if (running) {
        manageNetworkGame();
      }
    }
  }

  public void startNetworkGame(VirtualInput localPlayer,
                               VirtualInput remotePlayer) {
    this.localPlayer = localPlayer;
    this.remotePlayer = remotePlayer;
    start();
  }

  /**
   * Transmit the local player action to the remote player.  The action
   * counter identifier is incremented automatically.
   * @param playerId - the local player ID.
   * @param compress - set <code>true</code> to lower the compressor.
   * @param launch - set <code>true</code> to launch a bubble.
   * @param swap - set <code>true</code> to swap the launch bubble with
   * the next bubble.
   * @param launchColor - the launch bubble color.
   * @param nextColor - the next bubble color.
   * @param newNextColor - when a bubble is launched, this is the new
   * next bubble color.  The prior next color is promoted to the
   * launch bubble color.
   * @param addAttackBubbles - the number of attack bubbles to add to
   * the opponent's attack bar.  Note this value may be set by either
   * player, but is only set by the remote player with respect to the
   * local player when a bubble superposition was prevented.  A
   * superposition is detected when an attack bubble attempts to
   * occupy an already occupied grid location.
   * @param totalAttackBubbles - the number of attack bubbles stored on
   * the attack bar prior to adding <code>addAttackBubbles</code>.
   * @param attackBubbles - the array of attack bubble colors.  A value
   * of -1 denotes no color, and thus no attack bubble at that column.
   * @param aimPosition - the launcher aim aimPosition.
   */
  public void sendLocalPlayerAction(int playerId,
                                    boolean compress,
                                    boolean launch,
                                    boolean swap,
                                    int launchColor,
                                    int nextColor,
                                    int newNextColor,
                                    int addAttackBubbles,
                                    int totalAttackBubbles,
                                    byte attackBubbles[],
                                    double aimPosition) {
    PlayerAction tempAction = new PlayerAction(null);
    tempAction.playerID = (byte) playerId;
    tempAction.actionID = ++localActionID;
    tempAction.compress = compress;
    tempAction.launchBubble = launch;
    tempAction.swapBubble = swap;
    tempAction.launchBubbleColor = (byte) launchColor;
    tempAction.nextBubbleColor = (byte) nextColor;
    tempAction.newNextBubbleColor = (byte) newNextColor;
    tempAction.addAttackBubbles = (short) addAttackBubbles;
    tempAction.totalAttackBubbles = (short) totalAttackBubbles;
    if (attackBubbles != null)
      for (int index = 0;index < 15; index++)
        tempAction.attackBubbles[index] = attackBubbles[index];
    tempAction.aimPosition = aimPosition;
    /*
     * TODO: manage the local player action list.
     */
    //addAction(tempAction);
    transmitAction(tempAction);
  }

  /**
   * Stop and <code>join()</code> the network game manager thread.
   */
  private void stopThread() {
    running = false;
    /*
     * Wake up the thread.
     */
    synchronized (this) {
      notify();
    }
    /*
     *  Close and join() the multicast thread.
     */
    boolean retry = true;
    while (retry) {
      try {
        join();
        retry = false;
      } catch (InterruptedException e) {
        /*
         *  Keep trying to close the multicast thread.
         */
      }
    }
  }

  /**
   * Populate a byte array with the byte representation of a short.
   * The byte array must consist of at least 2 bytes.
   * @param value - the short to convert to a byte array.
   * @param array - the byte array where the converted short is placed.
   */
  public static void toByteArray(short value, byte[] array) {
    ByteBuffer.wrap(array).putShort(value);
  }

  /**
   * Populate a byte array with the byte representation of an integer.
   * The byte array must consist of at least 4 bytes.
   * @param value - the integer to convert to a byte array.
   * @param array - the byte array where the converted int is placed.
   */
  public static void toByteArray(int value, byte[] array) {
    ByteBuffer.wrap(array).putInt(value);
  }

  /**
   * Populate a byte array with the byte representation of a double.
   * The byte array must consist of at least 8 bytes.
   * @param value - the double to convert to a byte array.
   * @param array - the byte array where the converted double is placed.
   */
  public static void toByteArray(double value, byte[] array) {
    ByteBuffer.wrap(array).putDouble(value);
  }

  /**
   * Convert a byte array into a double value.
   * @param bytes - the byte array to convert into a double.
   * @return The double representation of the supplied byte array.
   */
  public static double toDouble(byte[] bytes) {
    return ByteBuffer.wrap(bytes).getDouble();
  }

  /**
   * Convert a byte array into an integer value.
   * @param bytes - the byte array to convert into an integer.
   * @return The double representation of the supplied byte array.
   */
  public static int toInt(byte[] bytes) {
    return ByteBuffer.wrap(bytes).getInt();
  }

  /**
   * Convert a byte array into a short value.
   * @param bytes - the byte array to convert into a short.
   * @return The short representation of the supplied byte array.
   */
  public static short toShort(byte[] bytes) {
    return ByteBuffer.wrap(bytes).getShort();
  }

  /**
   * Transmit the local player action to the remote player via the
   * network interface.
   * @param action - the player action to transmit.
   * @return <code>true</code> if the transmission was successful.
   */
  private boolean transmitAction(PlayerAction action) {
    byte[] buffer = new byte[ACTION_BYTES + 1];
    buffer[0] = MSG_ID_ACTION;
    action.copyToBuffer(buffer, 1);
    /*
     * Send the datagram via the multicast manager.
     */
    return session.transmit(buffer);
  }

  /**
   * Transmit the local player game field to the remote player via the
   * network interface.
   * @param gameField - the player game field data to transmit.
   * @return <code>true</code> if the transmission was successful.
   */
  private boolean transmitGameField(GameFieldData gameField) {
    byte[] buffer = new byte[GAMEFIELD_BYTES + 1];
    buffer[0] = MSG_ID_GAME_FIELD;
    gameField.copyToBuffer(buffer, 1);
    /*
     * Send the datagram via the multicast manager.
     */
    return session.transmit(buffer);
  }

  /**
   * Transmit the message to indicate the local player is ready to join
   * a game.
   * @return <code>true</code> if the transmission was successful.
   */
  private boolean transmitJoinGame() {
    byte[] buffer = new byte[2];
    buffer[0] = MSG_ID_JOIN_GAME;
    buffer[1] = (byte) localPlayer.playerID;
    /*
     * Send the datagram via the multicast manager.
     */
    return session.transmit(buffer);
  }

  /**
   * Transmit the local player preferences to the remote player via the
   * network interface.
   * @return <code>true</code> if the transmission was successful.
   */
  private boolean transmitPrefs() {
    byte[] buffer = new byte[Preferences.PREFS_BYTES + 1];
    buffer[0] = MSG_ID_PREFS;
    copyPrefsToBuffer(localPrefs, buffer, 1);
    /*
     * Send the datagram via the multicast manager.
     */
    return session.transmit(buffer);
  }
};
