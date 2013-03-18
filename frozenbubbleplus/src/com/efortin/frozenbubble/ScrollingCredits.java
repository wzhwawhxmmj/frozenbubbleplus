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

import org.jfedor.frozenbubble.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

public class ScrollingCredits extends Activity
{
  private ScrollingTextView credits;

  @Override
  public void onCreate( Bundle savedInstanceState )
  {
    super.onCreate( savedInstanceState );
    //   Remove the title bar.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    //   Remove notification bar.
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                              WindowManager.LayoutParams.FLAG_FULLSCREEN);
    //   Load the layout for this activity.
    setContentView( R.layout.activity_scrolling_credits );
    //   Get the instance of the ScrollingTextView object.
    credits = (ScrollingTextView)findViewById(R.id.scrolling_credits);
    //   Configure the credits text presentation.
    credits.setScrollRepeatLimit(0);
    credits.setSpeed(50.0f);
    credits.setScrollDirection(ScrollingTextView.SCROLL_DOWN);
    credits.setTextSize(18.0f);
  }

  @Override
  public boolean onKeyDown( int keyCode, KeyEvent msg )
  {
    return checkCreditsDone();
  }

  @Override
  public boolean onKeyUp( int keyCode, KeyEvent msg )
  {
    return checkCreditsDone();
  }

  @Override
  public boolean onTrackballEvent( MotionEvent event )
  {
    return checkCreditsDone();
  }

  @Override
  public boolean onTouchEvent( MotionEvent event )
  {
    return checkCreditsDone();
  }

  public boolean checkCreditsDone()
  {
    if (!credits.isScrolling())
    {
      finish();
      return true;
    }
    else
    return false;
  }
}
