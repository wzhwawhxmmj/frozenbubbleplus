# Introduction #
The free, open source game Frozen Bubble for Android - now plus arcade mode, music, high scores, accelerometer based targeting, WiFi network multiplayer, and much, much more.  This project is a de-facto prerelease branch of Pawel Fedorynski's Frozen Bubble for Android project, so features introduced in this repository will eventually migrate to the app on the [Google Play](https://play.google.com/store/apps/details?id=org.jfedor.frozenbubble) store.

This project is a derivative of the Android port of the free open source game [Frozen Bubble](http://www.frozen-bubble.org/), originally developed by Guillaume Cottenceau, and originally [ported to the Android operating system](http://code.google.com/p/frozenbubbleandroid/) by Pawel Aleksander Fedorynski based on the [Java port](http://glenn.sanson.free.fr/v2/) by Glenn Sanson.  Thus it can be installed on and should run correctly on any device that supports Android 1.6 (API level 4 - Donut) or greater.

Download [frozenbubbleplus\_3.3.apk](https://drive.google.com/file/d/0B3wDgQITXUkHVEUzZHFReU94QzA/edit?usp=sharing) from Google Drive directly to your Android device (enable installation of applications from unknown sources), or copy it to your device from your computer and it can then be installed via a file system browser, e.g. [ES File Explorer](http://amzn.to/QA1DCC).

The [Frozen Bubble level editor](https://frozenbubbleplus.googlecode.com/files/sk.halmi.fbeditplus.apk) developed by Halmi Rudolf is also available from the download section.  This installer was added to ensure that interoperability was maintained between the two applications.  If the app store for your device has the level editor available for download, it is recommended that you download it from there to ensure you get the latest version, etc.

# How To Play #
This game is based on the classic Neo-Geo arcade bubble popping puzzle game Puzzle Bobble/Bust-A-Move.

Knock the bubbles down by making clusters of three or more bubbles of the same color.  Try to clear all the bubbles before they reach the bottom of the screen to advance to the next level.  The bubble will rebound off of the wall, since it only sticks to the ceiling or other bubbles it collides with.

Aim carefully to make shots that pass between gaps in the bubbles.

Touch the launch bubble or press the down key on the directional pad to swap the launch bubble with the next bubble.

There are 3 modes of aiming the bubble launcher:
  1. **Aim Then Shoot** - touch and swipe the screen to the left or right of the launcher, or press the left or right directional pad buttons to aim the launcher.
  1. **Point To Shoot** - touch the screen in the play area to simultaneously aim the launcher and fire the bubble towards the location you touched.
  1. **Rotate Then Shoot** - rotate your device to aim the launcher.

To fire the bubble, touch the play area, or press  the up or center keys on the directional pad.

# About #
I wanted to add some features to make the game more fun to play on my [Kindle Fire](https://developer.amazon.com/sdk/fire.html), such as level music, high score functionality for each level, and [other features](https://code.google.com/p/frozenbubbleplus/issues/list?can=1) to improve gameplay.  To that end, the first change I made was to add the [Android ModPlug library](http://www.peculiar-games.com/libmodplug-in-android/andmodplug) developed by Patrick Casey, which is an Android port of [libmodplug](http://sourceforge.net/projects/modplug-xmms/), which is based on the source for [ModPlug](http://openmpt.org/) provided by Olivier Lapicque.  This is used to play [module file](http://en.wikipedia.org/wiki/Module_file) music, which usually has the benefit of being free for non-commercial use due to the history of the tracked music scene being very open and collaborative.  Another advantage of modules is that the songs are significantly smaller than just about any other music file format.

The [high score manager](http://code.google.com/p/andrac/) source code was originally developed by Michel Racic for Frozen Eggs, an Easter-themed derivative of Frozen Bubble.

The [accelerometer manager](https://code.google.com/p/androgames-sample/) source code was originally developed by Antoine Vianey.

The [SeekBar preference](http://robobunny.com/wp/2011/08/13/android-seekbar-preference/) source code is generously provided by Robobunny (Kirk Baucom).

Glenn Sanson has ported just about every feature of the original Frozen Bubble to Java, so the work for the Android port is mostly just replacing the Java AWT graphics in his code with more Android-friendly graphics objects.  Thus a very large portion of the code for this project is derived directly from his work.

This game was developed using the [Android ADT Bundle for Windows](http://developer.android.com/sdk/installing/bundle.html) (which includes the Android SDK and the Eclipse IDE).  It also uses the [Android NDK](http://developer.android.com/tools/sdk/ndk/index.html) to build the C++ source for libmodplug, so the project is a mix of native C++ and Java.  I include the libmodplug source code in this project to expose modifications I have made to add more methods to the interface, which forces a build of the libmodplug library locally.  Thus this project **cannot** be built without the NDK (native development kit) installed in addition to the Android SDK (software development kit).

I have been developing software since 2000, but have just recently starting developing Android games.  I gravitated towards Frozen Bubble because it is one of the more rich, full featured, and enjoyable games available completely free and via open source, and I am hoping that the experience I gain adding features to an existing game will help me develop my own original games.

All the music used in this game is available free for download from [the Mod Archive](http://modarchive.org/).  Each song is in its [original module format](https://code.google.com/p/frozenbubbleplus/source/browse/#svn%2Ftrunk%2Ffrozenbubbleplus%2Fres%2Fraw) with the information intact with regards to each song's title and author.  Each song is the copyright of its respective author, and is not for commercial use unless permission or licensing is granted by the author.