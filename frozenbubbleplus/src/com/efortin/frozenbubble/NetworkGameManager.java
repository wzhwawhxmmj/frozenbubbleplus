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

import org.jfedor.frozenbubble.BubbleSprite;
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
  public static final byte MSG_ID_STATUS = 1;
  public static final byte MSG_ID_PREFS  = 2;
  public static final byte MSG_ID_ACTION = 3;
  public static final byte MSG_ID_FIELD  = 4;
  /*
   * Datagram size definitions.
   */
  public static final int  ACTION_BYTES = 36;
  public static final int  FIELD_BYTES  = 111;
  public static final int  PREFS_BYTES  = Preferences.PREFS_BYTES;
  public static final int  STATUS_BYTES = 7;
  /*
   * Player status datagram definitions.
   */
  private static final long STATUS_INTERVAL = 1000;

  private boolean          running;
  private long             statusTxTime;
  private Context          mContext;
  private PlayerStatus     localStatus;
  private PlayerStatus     remoteStatus;
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
  private NetGameInterface remoteInterface;

  /**
   * This class represents the current state of an individual player
   * game field.  The game field consists of the launcher bubbles, the
   * bubbles fixed to the game field, and the the attack bar.
   * @author Eric Fortin
   *
   */
  public class GameFieldData {
    public byte     playerID           = 0;
    public byte     compressorSteps    = 0;
    public byte     launchBubbleColor  = -1;
    public byte     nextBubbleColor    = -1;
    public byte     newNextBubbleColor = -1;
    public short    totalAttackBubbles = 0;
    /*
     * The game field is represented by a 2-dimensional array, with 8
     * rows and 13 columns.  This is displayed on the screen as 13 rows
     * with 8 columns.
     */
    public byte[][] gameField =
      {{ -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 },
       { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }};

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
        this.compressorSteps     = fieldData.compressorSteps;
        this.launchBubbleColor   = fieldData.launchBubbleColor;
        this.nextBubbleColor     = fieldData.nextBubbleColor;
        this.newNextBubbleColor  = fieldData.newNextBubbleColor;
        this.totalAttackBubbles  = fieldData.totalAttackBubbles;

        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 13; y++) {
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
        this.compressorSteps     = buffer[startIndex++];
        this.launchBubbleColor   = buffer[startIndex++];
        this.nextBubbleColor     = buffer[startIndex++];
        this.newNextBubbleColor  = buffer[startIndex++];
        shortBytes[0]            = buffer[startIndex++];
        shortBytes[1]            = buffer[startIndex++];
        this.totalAttackBubbles  = toShort(shortBytes);

        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 13; y++) {
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
        buffer[startIndex++] = this.compressorSteps;
        buffer[startIndex++] = this.launchBubbleColor;
        buffer[startIndex++] = this.nextBubbleColor;
        buffer[startIndex++] = this.newNextBubbleColor;
        toByteArray(this.totalAttackBubbles, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];

        for (int x = 0; x < 8; x++) {
          for (int y = 0; y < 13; y++) {
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

  /**
   * This class encapsulates variables used to indicate the local game
   * and player status, and is used to synchronize the exchange of
   * information over the network.
   * <p>This data is intended to be send periodically to the remote
   * player(s) to keep all the players synchronized and informed of
   * potential network issues with lost datagrams.  This is especially
   * common with multicasting, which is implemented via the User
   * Datagram Protocol (UDP), which is unreliable.<br>
   * Refer to:
   * <a href="url">http://en.wikipedia.org/wiki/User_Datagram_Protocol</a>
   * @author Eric Fortin
   *
   */
  public class PlayerStatus {
    /*
     * The following ID is the player associated with this status.
     */
    public byte  playerID;
    /*
     * The following action IDs represent the associated player's
     * current game state.  localActionID will refer to that player's
     * last transmitted action identifer, and remoteActionID will refer
     * to that player's pending action identifier (the action it is 
     * expecting to receive next).
     * 
     * This is useful for noting if a player has missed player action
     * datagrams from another player, because its remoteActionID will be
     * less than or equal to the localActionID of the other player if it
     * has not received all the action transmissions from the other
     * player(s). 
     */
    public short localActionID;
    public short remoteActionID;
    /*
     * The following flags are used to request data from the remote
     * player(s) - either their game preferences, or game field data.
     * When one or either of these flags is true, then the other
     * player(s) shall transmit the appropriate information.
     */
    private boolean field_request;
    private boolean prefs_request;

    /**
     * Class constructor.
     * @param id - the player ID associated with this status
     * @param localActionID - the local last transmitted action ID.
     * @param remoteActionID - the remote current pending action ID.
     * @param field - request field data
     * @param prefs - request preference data
     */
    public PlayerStatus(byte id,
                        short localActionID,
                        short remoteActionID,
                        boolean field,
                        boolean prefs) {
      init(id, localActionID, remoteActionID, field, prefs);
    }

    /**
     * Class constructor.
     * @param action - PlayerAction object to copy to this instance.
     */
    public PlayerStatus(PlayerStatus status) {
      copyFromStatus(status);
    }

    /**
     * Class constructor.
     * @param buffer - buffer contents to copy to this instance.
     */
    public PlayerStatus(byte[] buffer, int startIndex) {
      copyFromBuffer(buffer, startIndex);
    }

    /**
     * Copy the contents of the supplied action to this action.
     * @param action - the action to copy.
     */
    public void copyFromStatus(PlayerStatus status) {
      if (status != null) {
        this.playerID       = status.playerID;
        this.localActionID  = status.localActionID;
        this.remoteActionID = status.remoteActionID;
        this.field_request  = status.field_request;
        this.prefs_request  = status.prefs_request;
      }
    }

    /**
     * Copy the contents of the buffer to this status.
     * @param buffer - the buffer to convert and copy.
     * @param startIndex - the start of the data to convert.
     */
    public void copyFromBuffer(byte[] buffer, int startIndex) {
      byte[] shortBytes  = new byte[2];

      if (buffer != null) {
        this.playerID       = buffer[startIndex++];
        shortBytes[0]       = buffer[startIndex++];
        shortBytes[1]       = buffer[startIndex++];
        this.localActionID  = toShort(shortBytes);
        shortBytes[0]       = buffer[startIndex++];
        shortBytes[1]       = buffer[startIndex++];
        this.remoteActionID = toShort(shortBytes);
        this.field_request  = buffer[startIndex++] == 1;
        this.prefs_request  = buffer[startIndex++] == 1;
      }
    }

    /**
     * Copy the contents of this status to the buffer.
     * @param buffer - the buffer to copy to.
     * @param startIndex - the start location to copy to.
     */
    public void copyToBuffer(byte[] buffer, int startIndex) {
      byte[] shortBytes  = new byte[2];

      if (buffer != null) {
        buffer[startIndex++] = this.playerID;
        toByteArray(this.localActionID, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];
        toByteArray(this.remoteActionID, shortBytes);
        buffer[startIndex++] = shortBytes[0];
        buffer[startIndex++] = shortBytes[1];
        buffer[startIndex++] = (byte) ((this.field_request == true)?1:0);
        buffer[startIndex++] = (byte) ((this.prefs_request == true)?1:0);
      }
    }

    /**
     * Initialize this object with the provided data.
     * @param id - the player ID associated with this status
     * @param localActionID - the local last transmitted action ID.
     * @param remoteActionID - the remote current pending action ID.
     * @param field - request field data
     * @param prefs - request preference data
     */
    public void init(byte id,
                     short localActionID,
                     short remoteActionID,
                     boolean field,
                     boolean prefs) {
      this.playerID       = id;
      this.localActionID  = localActionID;
      this.remoteActionID = remoteActionID;
      this.field_request  = field;
      this.prefs_request  = prefs;
    }
  };

  public class NetGameInterface {
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

    public void postProcess() {
      gotAction = false;
      gotFieldData = false;
    }

    public NetGameInterface() {
      playerAction = new PlayerAction(null);
      gameFieldData = new GameFieldData(null);
    }
  };

  @Override
  public void onMulticastEvent(int type, byte[] buffer, int length) {
    /*
     * Process the multicast message.
     */
    if ((type == MulticastManager.EVENT_PACKET_RX) && (buffer != null)) {
      byte msgId = buffer[0];

      /*
       * If the message contains the remote player status, copy it to
       * the remote player status object.  The remote player status
       * object will be null until the first remote status datagram is
       * received.
       */
      if ((msgId == MSG_ID_STATUS) && (length == (STATUS_BYTES + 1))) {
        if (buffer[0] == remotePlayer.playerID) {
          if (remoteStatus == null) {
            remoteStatus = new PlayerStatus(buffer, 1);
          }
          else {
            remoteStatus.copyFromBuffer(buffer, 1);
          }
        }
      }

      /*
       * If the message contains game preferences from player 1, then
       * update the game preferences.  The game preferences for all
       * players are set per player 1.
       */
      if ((msgId == MSG_ID_PREFS) && (length == (PREFS_BYTES + 2))) {
        if (buffer[1] == VirtualInput.PLAYER1) {
          copyPrefsFromBuffer(remotePrefs, buffer, 2);
          PreferencesActivity.setFrozenBubblePrefs(remotePrefs);
          localStatus.prefs_request = false;
        }
      }

      /*
       * If the message contains a game action, add it to the
       * appropriate action list.
       */
      if ((msgId == MSG_ID_ACTION) && (length == (ACTION_BYTES + 1))) {
        addAction(new PlayerAction(buffer, 1));
      }

      /*
       * If the message contains the remote player game field, update
       * the remote player interface game field object.
       */
      if ((msgId == MSG_ID_FIELD) && (length == (FIELD_BYTES + 1))) {
        if (buffer[0] == remotePlayer.playerID) {
          remoteInterface.gameFieldData.copyFromBuffer(buffer, 1);
          remoteInterface.gotFieldData = true;
          localStatus.field_request = false;
        }
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
    mContext = myContext;
    localPrefs = new Preferences();
    remotePrefs = new Preferences();
    localPlayer = null;
    remotePlayer = null;
    localStatus = null;
    remoteStatus = null;
    statusTxTime = 0;
    SharedPreferences sp =
        myContext.getSharedPreferences(FrozenBubble.PREFS_NAME,
                                       Context.MODE_PRIVATE);
    PreferencesActivity.getFrozenBubblePrefs(localPrefs, sp);
    remoteInterface = new NetGameInterface();
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
        /*
         * If this action is the most current, then we can postpone the
         * cyclic status message.  This is because we just received the
         * data that the status message is supposed to prompt the remote
         * player to send. 
         */
        if (newAction.actionID == localStatus.remoteActionID) {
          statusTxTime = System.currentTimeMillis() + STATUS_INTERVAL;
        }
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

    if (remoteInterface != null)
      remoteInterface.cleanUp();
    remoteInterface = null;

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

  private void getGameFieldData(GameFieldData gameData) {
    gameData.playerID = (byte) localPlayer.playerID;
    gameData.compressorSteps =
        (byte) localPlayer.mGameRef.getCompressorSteps();
    gameData.launchBubbleColor = (byte) localPlayer.mGameRef.getCurrentColor();
    gameData.nextBubbleColor = (byte) localPlayer.mGameRef.getNextColor();
    gameData.newNextBubbleColor =
        (byte) localPlayer.mGameRef.getNewNextColor();
    gameData.totalAttackBubbles =
        (short) localPlayer.mGameRef.getAttackBubbles();
    BubbleSprite[][] bubbleGrid = localPlayer.mGameRef.getGrid();
    for (int i = 0; i < 8; i++) {
      for (int j = 0; j < 13; j++) {
        if (bubbleGrid[i][j] != null) {
          gameData.gameField[i][j] = (byte) bubbleGrid[i][j].getColor();
        }
        else {
          gameData.gameField[i][j] = -1;
        }
      }
    }
  }

  public synchronized PlayerAction getRemoteActionPreview() {
    PlayerAction tempAction = null;
    int listSize = remoteActionList.size();

    for (int index = 0; index < listSize; index++) {
      /*
       * When a match is found, return a reference to it.
       */
      if (remoteActionList.get(index).actionID == localStatus.remoteActionID) {
        tempAction = remoteActionList.get(index);
        break;
      }
    }

    return (tempAction);
  }

  /**
   * This function obtains the expected remote player action (based on
   * action ID) and places it into the remote player interface.
   * <p>This function must be called periodically as it is assumed
   * that the actions will be performed at the most appropriate time as
   * determined by caller.
   * @return <code>true</code> if the appropriate remote player action
   * was retrieved from the remote action list.
   */
  public synchronized boolean getRemoteAction() {
    int listSize = remoteActionList.size();

    remoteInterface.gotAction = false;

    for (int index = 0; index < listSize; index++) {
      /*
       * When a match is found, copy the necessary element from the
       * list, remove it, and exit the loop.
       */
      if (remoteActionList.get(index).actionID == localStatus.remoteActionID) {
        remoteInterface.playerAction.copyFromAction(remoteActionList.get(index));
        try {
            remoteActionList.remove(index);
        } catch (IndexOutOfBoundsException ioobe) {
          // TODO - auto-generated exception handler stub.
          //e.printStackTrace();
        }
        remoteInterface.gotAction = true;
        localStatus.remoteActionID++;
        break;
      }
    }
    /*
     * TODO: if the current actionID is not in the list and there exists
     * an action with an ID greater than the current action ID, request
     * a re-issue of the appropriate action.  This can only occur upon
     * message loss from a remote player.
     */
    return (remoteInterface.gotAction);
  }

  /**
   * This function is called from manager thread's <code>run()</code>
   * method.  This performs the network handshaking amongst peers to
   * ensure proper game synchronization and operation.
   */
  private void manageNetworkGame() {
    long currentTime = System.currentTimeMillis();

    if (currentTime > statusTxTime) {
      if (remoteStatus != null) {
        if (remoteStatus.prefs_request) {
          transmitPrefs();
        }
        else if (remoteStatus.field_request) {
          GameFieldData tempField = new GameFieldData(null);
          getGameFieldData(tempField);
          transmitGameField(tempField);
        }
        else
          transmitStatus(localStatus);
      }
      else
        transmitStatus(localStatus);

      statusTxTime = currentTime + STATUS_INTERVAL;
    }
  }

  /**
   * This function obtains the remote player interface and returns a
   * reference to it to the caller.
   * @return A reference to the remote player network game interface
   * which provides all necessary remote player data.
   */
  public NetGameInterface getRemoteInterface() {
    return (remoteInterface);
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
    /*
     * Initialize the local status local action ID to zero, as it is
     * pre-incremented for every action transmitted to the remote
     * player.
     * 
     * Initialize the local status remote action ID to 1, as it must be
     * the first action ID received from the remote player.
     * 
     * Set the field and preference request flags to request the
     * data from the remote player.  If this player is player 1, then
     * don't request the preference data, since player 1's preferences
     * are used as the game preferences for all players.
     */
    boolean requestPrefs;
    if (localPlayer.playerID == VirtualInput.PLAYER1) {
      requestPrefs = false;
    }
    else {
      requestPrefs = true;
    }
    localStatus = new PlayerStatus((byte) localPlayer.playerID,
                                   (short) 0, (short) 1,
                                   true, requestPrefs);
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
    tempAction.actionID = ++localStatus.localActionID;
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
    byte[] buffer = new byte[FIELD_BYTES + 1];
    buffer[0] = MSG_ID_FIELD;
    gameField.copyToBuffer(buffer, 1);
    /*
     * Send the datagram via the multicast manager.
     */
    return session.transmit(buffer);
  }

  /**
   * Transmit the player status message.
   * @return <code>true</code> if the transmission was successful.
   */
  private boolean transmitStatus(PlayerStatus status) {
    byte[] buffer = new byte[STATUS_BYTES + 1];
    buffer[0] = MSG_ID_STATUS;
    status.copyToBuffer(buffer, 1);
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
    byte[] buffer = new byte[Preferences.PREFS_BYTES + 2];
    buffer[0] = MSG_ID_PREFS;
    buffer[1] = (byte) localPlayer.playerID;
    copyPrefsToBuffer(localPrefs, buffer, 2);
    /*
     * Send the datagram via the multicast manager.
     */
    return session.transmit(buffer);
  }
};
