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

import java.util.Random;

import org.jfedor.frozenbubble.FrozenGame;
import org.jfedor.frozenbubble.LevelManager;

public class ComputerAI extends Thread {
  private boolean fireFlag;
  private boolean running;
  private boolean swapFlag;
  private FrozenGame myFrozenGame;
  private LevelManager myLevelManager;

  /**
   * Game AI thread class constructor.
   * 
   * @param gameRef
   *        - reference to the game to access information used to decide
   *        when to fire.
   * 
   * @param levelRef
   *        - reference to the level grid to determine where to fire.
   */
  public ComputerAI(FrozenGame gameRef, LevelManager levelRef) {
    myFrozenGame = gameRef;
    myLevelManager = levelRef;
    fireFlag = true;
    swapFlag = false;
    running = true;
  }

  /**
   * Return the current state of the fire flag.  When the AI has
   * generated the next launch trajectory, the fire flag is set to true.
   * <p>
   * This function clears the fire flag when it is read, so this must
   * only ever be called from one location, period.
   * 
   * @return - returns the state of the fire flag.
   */
  public boolean getFireFlag() {
    /*
     * If the fire flag is true, clear it, and return true.
     */
    if (fireFlag) {
      fireFlag = false;
      return true;
    }
    else
      return false;
  }

  /**
   * Return the current state of the swap flag.  When the AI has
   * determined that the current launch bubble is less desirable than
   * the next one, the swap flag is set to true.
   * <p>
   * This function clears the swap flag when it is read, so this must
   * only ever be called from one location, period.
   * 
   * @return - returns the state of the swap flag.
   */
  public boolean getSwapFlag() {
    /*
     * If the swap flag is true, clear it, and return true.
     */
    if (swapFlag) {
      swapFlag = false;
      return true;
    }
    else
      return false;
  }

  @Override
  public void run() {
    while(running) {
      try {
        synchronized(this) {
          /*
           * Only fire if the game state permits, and the last bubble
           * launch trajectory has been processed.
           */
          if (myFrozenGame.getOkToFire() && !fireFlag) {
            Random random = new Random();
            myFrozenGame.setPosition(random.nextInt(FrozenGame.MAX_LAUNCH_POSITION - 1) + 1);
            fireFlag = true;
            wait(900);
          }
          wait(100);
        }
      } catch (InterruptedException e) {
      } finally {
      }
    }
  }

  /**
   * Stop the thread <code>run()</code> execution.
   * <p>
   * Interrupt the thread when it is suspended via <code>wait()</code>.
   */
  public void stopThread() {
    running = false;
    synchronized(this) {
      this.notifyAll();
    }
  }
}