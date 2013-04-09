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
 * version 2, as published by the Free Software Foundation.
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

import org.jfedor.frozenbubble.FrozenBubble;
import org.jfedor.frozenbubble.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.peculiargames.andmodplug.MODResourcePlayer;
import com.peculiargames.andmodplug.PlayerThread;

public class ScrollingCredits extends Activity implements Runnable
{
  private LinearLayout      linearLayout;
  private ScrollingTextView credits;
  private MODResourcePlayer resplayer = null;

  private static final int DEFAULT_SONG = 0;
  private final int[] MODlist = {
    R.raw.worldofpeace
  };

  private boolean victoryScreenShown = false;

  @Override
  public void onBackPressed()
  {
    //
    //   Do not call the super class ancestor method.  Finish this
    //   activity so it is destroyed and we simply return to the game.
    //
    //
    end();
  }

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    //   Remove the title bar.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    //   Remove notification bar.
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                              WindowManager.LayoutParams.FLAG_FULLSCREEN);
    //   Load the default XML layout for this activity.
    setContentView(R.layout.activity_scrolling_credits);
    //   Get the instance of the ScrollingTextView object.
    credits = (ScrollingTextView)findViewById(R.id.scrolling_credits);
    //   Configure the credits text presentation.
    credits.setScrollRepeatLimit(0);
    credits.setSpeed(50.0f);
    credits.setScrollDirection(ScrollingTextView.SCROLL_UP);
    credits.setTextSize(18.0f);
    //   Start the credits music.
    newMusicPlayer();
    //   Post this runnable instance to the scrolling text view.
    credits.postDelayed(this, 100);
  }

  @Override
  public void onPause()
  {
    super.onPause();
    if (resplayer != null)
      resplayer.PausePlay();
    credits.setPaused(true);
  }

  @Override
  public void onResume()
  {
    super.onResume();
    if (resplayer != null)
      resplayer.UnPausePlay();
    credits.setPaused(false);
  }

  @Override
  public void onDestroy()
  {
    super.onDestroy();
    cleanUp();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent msg)
  {
    if (keyCode == KeyEvent.KEYCODE_BACK)
    {
      end();
      return true;
    }
    return false;
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event)
  {
    return checkCreditsDone();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    return checkCreditsDone();
  }

  /**
   * Stop the music player, close the thread, and free the instance.
   */
  private void destroyMusicPlayer()
  {
    if (resplayer != null)
    {
      resplayer.StopAndClose();
      resplayer = null;
    }
  }

  /**
   * Start a new MOD player.  If one already exists, it will be
   * destroyed and a new one will be created.
   */
  private void newMusicPlayer()
  {
    destroyMusicPlayer();
    // load the mod file
    resplayer = new MODResourcePlayer(this);
    resplayer.setLoopCount(PlayerThread.LOOP_SONG_FOREVER);
    resplayer.LoadMODResource(MODlist[DEFAULT_SONG]);
    if (FrozenBubble.getMusicOn() == true)
    {
      resplayer.setVolume(255);
    }
    else
    {
      resplayer.setVolume(0);
    }
    // start up the music
    resplayer.startPaused(false);
    resplayer.start();
  }

  public void cleanUp()
  {
    destroyMusicPlayer();
  }

  public boolean checkCreditsDone()
  {
    if (!credits.isScrolling())
    {
      end();
      return true;
    }
    return false;
  }

  public void displayImage(int id)
  {
    //  Construct a new LinearLayout programmatically. 
    linearLayout = new LinearLayout(this);
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    linearLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                                                  LayoutParams.MATCH_PARENT));
    //   ImageView setup for the image.
    ImageView imageView = new ImageView(this);
    //   Set image resource.
    imageView.setImageResource(R.drawable.victory);
    //   Set image position.
    imageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                                               LayoutParams.MATCH_PARENT));
    //   Add view to layout.
    linearLayout.addView(imageView);
    //   Set the content view to this layout and display the image.
    setContentView(linearLayout);
  }

  public void end()
  {
    credits.abort();
    //
    //   Since the default game activity creates its own player,
    //   destroy the current player.
    //
    //
    destroyMusicPlayer();
    //
    //   Create an intent to launch the game activity.  Since it was
    //   running in the background while this activity was running, it
    //   may have been stopped by the system.
    //
    //
    Intent intent = new Intent( this, FrozenBubble.class );
    startActivity( intent );
    finish();
  }

  @Override
  public void run()
  {
    //    Check if we need to display the end of game victory image.
    if (!credits.isScrolling() && !victoryScreenShown)
    {
      victoryScreenShown = true;
      //  Make the credits text transparent.
      credits.setTextColor(Color.TRANSPARENT);
      //  Display the end of game victory image.
      displayImage(R.drawable.victory);
    }
    credits.postDelayed(this, 100);
  }
}
