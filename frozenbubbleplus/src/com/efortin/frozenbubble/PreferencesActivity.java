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
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity{

  private static int     collision  = BubbleSprite.MIN_PIX;
  private static boolean compressor = true;
  private static int     difficulty = LevelManager.MODERATE;
  private static boolean dontRushMe = false;
  private static boolean fullscreen = true;
  private static boolean colorMode  = false;
  private static int     gameMode   = FrozenBubble.GAME_NORMAL;
  private static boolean musicOn    = true;
  private static boolean soundOn    = true;
  private static int     targetMode = FrozenBubble.POINT_TO_SHOOT;

  private CheckBoxPreference colorOption;
  private CheckBoxPreference compressorOption;
  private CheckBoxPreference hurryOption;
  private CheckBoxPreference musicOption;
  private CheckBoxPreference screenOption;
  private CheckBoxPreference soundOption;
  private ListPreference     targetOption;
  private SeekBarPreference  collisionOption;
  private SeekBarPreference  difficultyOption;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
     super.onCreate(savedInstanceState);

     addPreferencesFromResource(R.layout.activity_preferences_screen);
     getPreferences();
     restoreGamePrefs();
     setDefaults();
  }

  private void getPreferences() {
    colorOption = (CheckBoxPreference)findPreference("colorblind_option");
    compressorOption = (CheckBoxPreference)findPreference("compressor_option");
    hurryOption = (CheckBoxPreference)findPreference("rush_me_option");
    musicOption = (CheckBoxPreference)findPreference("play_music_option");
    screenOption = (CheckBoxPreference)findPreference("fullscreen_option");
    soundOption = (CheckBoxPreference)findPreference("sound_effects_option");
    targetOption = (ListPreference)findPreference("targeting_option");
    //collisionOption = (SeekBarPreference)findPreference("collision_option");
    //difficultyOption = (SeekBarPreference)findPreference("difficulty_option");
  }

  private void restoreGamePrefs() {
    SharedPreferences mConfig = getSharedPreferences(FrozenBubble.PREFS_NAME,
                                                     Context.MODE_PRIVATE);
    collision  = mConfig.getInt    ("collision",  BubbleSprite.MIN_PIX);
    compressor = mConfig.getBoolean("compressor", true);
    difficulty = mConfig.getInt    ("difficulty", LevelManager.MODERATE);
    dontRushMe = mConfig.getBoolean("dontRushMe", false);
    fullscreen = mConfig.getBoolean("fullscreen", true);
    gameMode   = mConfig.getInt    ("gameMode",   FrozenBubble.GAME_NORMAL);
    musicOn    = mConfig.getBoolean("musicOn",    true);
    soundOn    = mConfig.getBoolean("soundOn",    true);
    targetMode = mConfig.getInt    ("targetMode", FrozenBubble.POINT_TO_SHOOT);

    if (gameMode == FrozenBubble.GAME_NORMAL)
      colorMode = false;
    else
      colorMode = true;
  }

  private void setDefaults() {
    colorOption.setChecked(colorMode);
    compressorOption.setChecked(compressor);
    hurryOption.setChecked(!dontRushMe);
    musicOption.setChecked(musicOn);
    screenOption.setChecked(fullscreen);
    soundOption.setChecked(soundOn);
    targetOption.setValue(String.valueOf(targetMode));
  }
}