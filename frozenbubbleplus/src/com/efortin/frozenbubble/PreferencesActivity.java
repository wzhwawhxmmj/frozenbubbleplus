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

import org.jfedor.frozenbubble.BubbleSprite;
import org.jfedor.frozenbubble.FrozenBubble;
import org.jfedor.frozenbubble.LevelManager;
import org.jfedor.frozenbubble.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

public class PreferencesActivity extends PreferenceActivity{

  private static int     collision  = BubbleSprite.MIN_PIX;
  private static boolean compressor = false;
  private static int     difficulty = LevelManager.MODERATE;
  private static boolean dontRushMe = false;
  private static boolean fullscreen = true;
  private static boolean colorMode  = false;
  private static int     gameMode   = FrozenBubble.GAME_NORMAL;
  private static boolean musicOn    = true;
  private static boolean soundOn    = true;
  private static int     targetMode = FrozenBubble.POINT_TO_SHOOT;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
     super.onCreate(savedInstanceState);

     setDefaultPreferences(this);
     addPreferencesFromResource(R.layout.activity_preferences_screen);
  }

  @Override
  public void onContentChanged() {
    super.onContentChanged();
    savePreferences();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent msg) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      savePreferences();
    }
    return super.onKeyDown(keyCode, msg);
  }

  private static void restoreGamePrefs() {
    collision  = FrozenBubble.getCollision();
    compressor = FrozenBubble.getCompressor();
    dontRushMe = FrozenBubble.getDontRushMe();
    fullscreen = FrozenBubble.getFullscreen();
    gameMode   = FrozenBubble.getMode();
    musicOn    = FrozenBubble.getMusicOn();
    soundOn    = FrozenBubble.getSoundOn();
    targetMode = FrozenBubble.getTargetMode();

    if (gameMode == FrozenBubble.GAME_NORMAL)
      colorMode = false;
    else
      colorMode = true;
  }

  private void savePreferences() {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(this);
    collision  = prefs.getInt("collision_option", BubbleSprite.MIN_PIX);
    compressor = prefs.getBoolean("compressor_option", false);
    difficulty = prefs.getInt("difficulty_option", LevelManager.MODERATE);
    dontRushMe = !prefs.getBoolean("rush_me_option", true);
    fullscreen = prefs.getBoolean("fullscreen_option", true);
    colorMode  = prefs.getBoolean("colorblind_option", false);
    musicOn    = prefs.getBoolean("play_music_option", true);
    soundOn    = prefs.getBoolean("sound_effects_option", true);
    targetMode = Integer.valueOf(prefs.getString("targeting_option",
        Integer.toString(FrozenBubble.POINT_TO_SHOOT)));

    if (!colorMode)
      gameMode = FrozenBubble.GAME_NORMAL;
    else
      gameMode = FrozenBubble.GAME_COLORBLIND;

    SharedPreferences sp = getSharedPreferences(FrozenBubble.PREFS_NAME,
                                                Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sp.edit();
    editor.putInt("collision", collision);
    editor.putBoolean("compressor", compressor);
    editor.putInt("difficulty", difficulty);
    editor.putBoolean("dontRushMe", dontRushMe);
    editor.putBoolean("fullscreen", fullscreen);
    editor.putInt("gameMode", gameMode);
    editor.putBoolean("musicOn", musicOn);
    editor.putBoolean("soundOn", soundOn);
    editor.putInt("targetMode", targetMode);
    editor.commit();
  }

  public static void setDefaultPreferences(Context context) {
    restoreGamePrefs();

    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = prefs.edit();

    editor.putInt("collision_option", collision);
    editor.putBoolean("compressor_option", compressor);
    editor.putInt("difficulty_option", difficulty);
    editor.putBoolean("rush_me_option", !dontRushMe);
    editor.putBoolean("fullscreen_option", fullscreen);
    editor.putBoolean("colorblind_option", colorMode);
    editor.putBoolean("play_music_option", musicOn);
    editor.putBoolean("sound_effects_option", soundOn);
    editor.putString("targeting_option", Integer.toString(targetMode));
    editor.commit();
  }
}
