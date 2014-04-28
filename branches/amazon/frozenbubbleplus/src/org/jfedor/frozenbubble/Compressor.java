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

package org.jfedor.frozenbubbleplus;

import android.graphics.Canvas;
import android.os.Bundle;

public class Compressor {
  private BmpWrap compressorHead;
  private BmpWrap compressor;
  double moveDown;
  int steps;

  public Compressor(BmpWrap compressorHead, BmpWrap compressor) {
    this.compressorHead = compressorHead;
    this.compressor = compressor;
    this.moveDown = 0.;
    this.steps = 0;
  }

  public double getMoveDown() {
    return moveDown;
  }

  public int getSteps() {
    return steps;
  }

  public void moveDown() {
    moveDown += 28.;
    steps++;
  }

  public void paint(Canvas c, double scale, int dx, int dy) {
    for (int i = 0; i < steps; i++) {
      c.drawBitmap(compressor.bmp,
                   (float)(235 * scale + dx),
                   (float)((28 * i - 4) * scale + dy), null);
      c.drawBitmap(compressor.bmp,
                   (float)(391 * scale + dx),
                   (float)((28 * i - 4) * scale + dy), null);
    }
    c.drawBitmap(compressorHead.bmp,
                 (float)(160 * scale + dx),
                 (float)((-7 + 28 * steps) * scale + dy), null);
  }

  public void restoreState(Bundle map, int id) {
    moveDown = map.getDouble(String.format("%d-compressor-moveDown", id));
    steps = map.getInt(String.format("%d-compressor-steps", id));
  }

  public void saveState(Bundle map, int id) {
    map.putDouble(String.format("%d-compressor-moveDown", id), moveDown);
    map.putInt(String.format("%d-compressor-steps", id), steps);
  }
};
