// this class is for a separate thread to control the
// modplug player, getting commands from the Activity

// to do:  needs more error checking (I ignore the minbuffer size when getting the audio track, LoadMODData()
//         may fail, etc. etc.
//

//    Typical call order:
//
//    PlayerThreadOLD() - to get player instance (in top most activity that will use music)
//     LoadMODData() - to call libmodplug's Load() function with the MOD data
// or
//    PlayerThreadOLD(moddatabuffer) - to get player instance and load data in one call
//
// then
//    start()
//
//    then when changing songs (i.e. new game level or transition to another sub-activity, etc.) 
//      PausePlay()
//     UnLoadMod()
//
//    LoadMODData(newmodfiledata)
//    UnPausePlay()
//    repeat...

// *NOTE*
// This class sort of assumes there's only one player thread for a whole application (all activities)
// thus the static lock objects (mPVlock, mRDlock) below, and lots of other probably bad coding
// practice below... :-(  
// For a multi-Activity application, you can try the TakeOwnership() and GiveUpOwnership() calls...
// e.g. TakeOwnership(this) in an Activity's OnCreate(), and then GiveUpOwnership(this) in onPause()
//      YMMV

package com.peculiargames.andmodplug;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Player class for MOD/XM song files (extends Java Thread). Has methods to load song file data,
 * play the song, get information about the song, pause, unpause, etc.
 * 
 * 
 * <p>   <b>Typical call order:</b>
 * <br>   <code>pt = PlayerThread(); // get player instance (in topmost activity, etc.)</code>
 *<br>     <code>pt.LoadMODData();  // load MOD/XM data into player</code>
 * <br><b>or:</b><br>
 *    <code>pt = PlayerThread(moddatabuffer);  // get player & load data in one call</code>
 * <br><b>then:</b><br>
 *    <code>pt.start();   // start thread (playing song)</code>
 * <br>
 *    <b>then when changing songs (new game level or transition to another sub-activity, etc.):</b> 
 *    <br>  <code>pt.PausePlay();</code>
 *   <br>  <code>pt.UnLoadMod();</code>
 *
 *  <br>  <code>pt.LoadMODData(newmodfiledata);</code>
 *  <br>  <code>pt.UnPausePlay();</code>
 *  <br> <code>// repeat...</code>
 * 
 * @author    P.A. Casey (crow) Peculiar-Games.com
 *              
 * @version   1.0
 *  
 */
public class PlayerThread extends Thread {

   //
   // version number for this build of AndModPlug
   public static final String VERS = "1.0";

   //
   // prefix for Log output
   private final static String LOGPREFIX = "PLAYERTHREAD";

   // flags for pattern changes
   /**
    * constant for <code>setPatternLoopRange()</code> calls - change to new pattern range immediately
    */
   public final static int PATTERN_CHANGE_IMMEDIATE = 1;
   /**
    * constant for <code>setPatternLoopRange()</code> calls - change to new pattern range 
    * after currently playing pattern finishes
    */
   public final static int PATTERN_CHANGE_AFTER_CURRENT = 2;
   /**
    * constant for <code>setPatternLoopRange()</code> calls - change to new pattern range after current
    * range of patterns finishes playing
    */
   public final static int PATTERN_CHANGE_AFTER_GROUP = 3;
   // values for song loop counts
   /**
    * constant for <code>setLoopCount()</code> calls - loop song forever
    */
   public final static int LOOP_SONG_FOREVER = -1;

   // limit volume volume steps to 8 steps (just an arbitrary decision...)
   // there's also a setVolume() method that accepts a float
   public final static float[] sVolume_floats = {0.0f, 0.125f, 0.25f, 0.375f, 0.5f, 
                                   0.625f, 0.75f, 1.0f};

   // object for lock on PlayerValid check (mostly necessary for passing a single PlayerThread instance among
   // Activities in an Android multi-activity application)
   public static Object sPVlock;

   // object for lock on ReadData call (to prevent UI thread messing with player thread's GetSoundData() calls)
   public static Object sRDlock;

   // mark the player as invalid (for when an Activity shuts it down, but Android allows a reference to
   // the player to persist -- better solution is probably to just null out the reference to the PlayerThread
   // object in whichever Activity shuts it down)
   public boolean mPlayerValid = false;

   private static boolean sLogOutput = false;
   
   private boolean mWaitFlag = false;
   private boolean mFlushedData = false;
   private boolean mPlaying = true;
   private boolean mRunning = true;

   // Android will report the minimum buffer size needed to keep playing audio at our
   // requested rate smoothly
   private int mMinbuffer;
   private int mModsize;    // holds the size in bytes of the mod file

   private final static int BUFFERSIZE = 20000; // the full sound sample buffer size

   // buffer for audio data
   private static short[] mBuffer;  

   private AudioTrack mMytrack;
   
   private boolean mLoad_ok;
   
   // for storing info about the MOD file currently loaded
   private String mModname;
   private int mNumChannels;
   private int mRate;

   // could probably get rid of this, unneeded when using libmodplug as single-entrant library?!?
   private static byte[] mdunused;

   // start the player in a paused state?
   private boolean mStart_paused;

   // play once through (one packet of sample data) then pause
   private boolean mPlay_once;

   private static final int NUM_RATES = 5;
   private final int[] try_rates = {44100, 32000, 22000, 16000, 8000};

   //
   // ownership code -- for when several activities try to share a single mod player instance...
   //
   // probably needs to be synchronized...
   //
   private Object mOwner;

   public boolean TakeOwnership(Object newowner) {
      if (mOwner == null || mOwner == newowner) {
         mOwner = newowner;
         return true;
      }
      else
         return false;
   }
   
   public boolean GiveUpOwnership(Object currowner) {
      if (mOwner == null || mOwner == currowner) {
         mOwner = null;
         return true;
      }
      else
         return false;
   }

   public Object GetOwner() {
      return mOwner;
   }

   //**********************************************************
   // Listener interface for various events
   //**********************************************************
   // event types
   public static final int EVENT_PLAYER_STARTED = 1;
   public static final int EVENT_PATTERN_CHANGE = 2;
   public static final int EVENT_SONG_COMPLETED = 3;

   // track if player has started (after loading a new mod)
   private static boolean sPlayerStarted;

   // listener user set
   public interface PlayerListener {
       public abstract void onPlayerEvent(int type);
   }

   PlayerListener mPlayerListener;

   public void setPlayerListener(PlayerListener pl) {
      mPlayerListener = pl;
   }

   //**********************************************************
   // Constructors
   //**********************************************************
   //
   //  here's (one of) the constructor(s) -- grabs an audio track and loads a mod file
   //
   //  mod file data has already been read in (using a FileStream) by the caller -- that
   //  functionality could probably be included here, but for now we'll do it this way.
   //
   //  you could use this constructor in the top parent activity (like a game menu) to
   //  create a PlayerThread and load the mod data in one call
   //
   /**
    * Allocates a MOD/XM/etc. song PlayerThread  
                             
    * The modData argument is a byte[] array with the MOD file pre-loaded
    * into it. The desiredrate argument is a specifier that attempts to set the rate
    * audio data will play at - will be overridden if the OS doesn't allow
    * that rate. 
    *  
                             
   @param  modData  a byte[] array containing the MOD file data.
    *  
                             
   @param  desiredrate rate of playback (e.g. 44100Hz, or 0 for default rate) for system audio data playback.
    *  
    */
   public PlayerThread(byte[] modData, int desiredrate)
   {
      // just call the regular constructor and then load in the supplied
      // MOD file data
      this(desiredrate);

      // load the mod file (data) into libmodplug
      mLoad_ok = ModPlug_JLoad(modData, modData.length);

      if (mLoad_ok)
      {
         // get info (name and number of tracks) for the loaded MOD file
         mModname = ModPlug_JGetName();
         mNumChannels = ModPlug_JNumChannels();
      }
   }

   //
   //  this one just gets an audio track. the mod file will be loaded later with 
   //  a call to LoadMODData()
   //
   /**
    * Allocates a MOD/XM/etc. song PlayerThread  
                             
    * The desiredrate argument is a specifier that attempts to set the rate
    * audio data will play at - will be overridden if the OS doesn't allow
    * that rate.
    * <p>
    *  General call order when using this constructor is:
    *  <br>
    *     pthr = new PlayerThread(0);
    *  <br>
    *      pthr.LoadMODData(modData);
    *  <br>
    *      pthr.start();
    *  
                             
   @param  desiredrate rate of playback (e.g. 44100Hz, or 0 for default rate) for system audio data playback.
    *  
    */
   public PlayerThread(int desiredrate)
   {
      // no Activity owns this player yet
      mOwner = null;

      mStart_paused  = false;
      mPlay_once     = false;
      sPlayerStarted = false;

      // try to get the audio track
      if (!GetAndroidAudioTrack(desiredrate))
         return;

      // set up our audio sample buffer(libmodplug processes the mod file and fills this
      // with sample data)
      // for proper error checking, this should check that BUFFERSIZE is greater than the
      // minbuffer size the audio system reports in the contructors...
      mBuffer = new short[BUFFERSIZE];

      mPlayerValid = true;
   }

   //
   // try to get an Android stereo audio track
   // used by the various constructors
   private boolean GetAndroidAudioTrack(int desiredrate)
   {
      int rateindex = 0;

      // get a stereo audio track from Android 
      // PACKETSIZE is the amount of data we request from libmodplug, minbuffer is the size
      // Android tells us is necessary to play smoothly for the rate, configuration we want and
      // is a separate buffer the OS handles

      // init the track and player for the desired rate (or if none specified, highest possible)
      if (desiredrate == 0)
      {
         boolean success = false;
         while (!success && (rateindex < NUM_RATES))
         {
            try
            {
               mMinbuffer = AudioTrack.getMinBufferSize(try_rates[rateindex], AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT);
               Log.i(LOGPREFIX, "mMinbuffer="+mMinbuffer+" our BUFFERSIZE="+BUFFERSIZE);
               mMytrack = new AudioTrack(AudioManager.STREAM_MUSIC, try_rates[rateindex], AudioFormat.CHANNEL_CONFIGURATION_STEREO,
               AudioFormat.ENCODING_PCM_16BIT, mMinbuffer, AudioTrack.MODE_STREAM);
               // init the Modplug player for this sample rate
               ModPlug_Init(try_rates[rateindex]);
               success = true;
            }
            catch (IllegalArgumentException e)
            {
               Log.i(LOGPREFIX, "couldn't get an AUDIOTRACK at rate "+try_rates[rateindex]+"Hz!");
               rateindex++;
            }
         }
      }
      else
      {
         mMinbuffer = AudioTrack.getMinBufferSize(desiredrate, AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT);
         Log.i(LOGPREFIX, "mMinbuffer="+mMinbuffer+" our BUFFERSIZE="+BUFFERSIZE);
         mMytrack = new AudioTrack(AudioManager.STREAM_MUSIC, desiredrate, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
         AudioFormat.ENCODING_PCM_16BIT, mMinbuffer, AudioTrack.MODE_STREAM);
         // init the Modplug player for this sample rate
         ModPlug_Init(desiredrate);
      }

      if (desiredrate == 0)
         mRate = try_rates[rateindex];
      else
         mRate = desiredrate;

      if (mMytrack == null)
      {
         Log.i(LOGPREFIX, "COULDN'T GET AN AUDIOTRACK");
         mPlayerValid = false;
         // couldn't get an audio track so return false to caller
         return false;
      }
      else
      {
         switch(mMytrack.getState())
         {
            case AudioTrack.STATE_INITIALIZED:
               Log.i(LOGPREFIX, "GOT THE INITIALIZED AUDIOTRACK!");
               break;
            default:
               Log.i(LOGPREFIX, "GOT THE AUDIOTRACK, BUT IT'S UNINITIALIZED?!?");
               Log.v(LOGPREFIX, "trying mMinbuffer*2 sized audiotrack instantiation...");
               mMytrack = new AudioTrack(AudioManager.STREAM_MUSIC, mRate, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
               AudioFormat.ENCODING_PCM_16BIT, mMinbuffer*2, AudioTrack.MODE_STREAM);
               switch(mMytrack.getState())
               {
                  case AudioTrack.STATE_INITIALIZED:
                     Log.v("--------", "STATE_INITIALIZED");
                     break;
                  default:
                     Log.v("--------", "STATE_UNINITIALIZED or NO STATIC DATA?"); 
                     break;
               }
               break;
         }
      }
      // got the audio track!
      return true;
   }

   //
   // load new mod file data   (kind of assumes that PausePlay() has been called first?!)
   /**
    * Loads MOD/XM,etc. song data for playback.
                             
    * The modData argument is a byte[] array with the MOD containing the song file data. 
    * <p>
    * Example of loading the data:
    * <br>
    * modfileInStream =    getResources().openRawResource(R.raw.coolxmsong);
     * <br>
     * try {
     * <br>
     *     modsize = modfileInStream.read(modData,0, modfileInStream.available());
     * <br>
     * } catch (IOException e) {
     * <br>
    *    e.printStackTrace();
     * <br>
    * }
                             
   @param  modData  a byte[] array containing the MOD file data.
    *                              
    */
   public void LoadMODData(byte[] modData) {

      Log.i(LOGPREFIX, "unloading mod data");
      
      UnLoadMod();

      mdunused = modData;

      Log.i("PLAYERTHREAD", "calling ModPLug_JLoad()");

      mLoad_ok = ModPlug_JLoad(modData, modData.length);

      if (mLoad_ok) {
         mModname = ModPlug_JGetName();
         mNumChannels = ModPlug_JNumChannels();
      }

      // re-init this flag so that an event will be passed to the PlayerListener
      // after the first write() to the AudioTrack - when I assume music will
      // actually start playing...
      synchronized(this) {
         sPlayerStarted = false;
      }
   }

   //
   // This PlayerValid stuff is for multi-activity use, or also Android's Pause - Resume 
   //
   // better way to deal with it is probably to always stop and join() the PlayerThread
   // in onPause() and allocate a new PlayerThread in onResume()  (or onCreate() ?? )
   //
   // check if the player thread is still valid
   public boolean PlayerValid() {
      // return whether this player is valid
      synchronized(sPVlock) {
         return mPlayerValid;
      }
   }

   // mark this playerthread as invalid (typically when we're closing down the main Activity)
   public void InvalidatePlayer() {
      synchronized(sPVlock) {
         mPlayerValid = false;
      }
   }

   //
   // the thread's run() call, where the actual sounds get played
   //
   /**
    * Start playing the MOD/XM song (hopefully it's been previously loaded using <code>LoadMODData()</code>
    * or <code>LoadMODResource()</code> ;)
    *                              
    */
   public void run()
   {
      boolean pattern_change = false;

      if (mStart_paused)
         mPlaying = false;
      else
         mPlaying = true;

      // main play loop
      mMytrack.play();

      while (mRunning)
      {
         while (mPlaying)
         {
            // pre-load another packet
            synchronized(sRDlock)
            {
               ModPlug_JGetSoundData(mBuffer, BUFFERSIZE);

               if (ModPlug_CheckPatternChange())
                  pattern_change = true;
            }

            synchronized(this)
            {
               if (!sPlayerStarted)
               {
                  Log.i(LOGPREFIX, "before first write to AudioTrack");
               }
            }

            // pass a packet of sound sample data to the audio track (blocks until audio track
            // can accept the new data)
            mMytrack.write(mBuffer, 0, BUFFERSIZE);

            // send player started event!?
            synchronized(this)
            {
               if (!sPlayerStarted)
               {
                  sPlayerStarted = true;
                  if (mPlayerListener != null)
                  {
                     Log.i(LOGPREFIX, "sending PLAYER_STARTED event");
                     mPlayerListener.onPlayerEvent(EVENT_PLAYER_STARTED);
                  }
               }
            }

            if (mPlay_once)
            {
               mPlay_once = false;
               mPlaying   = false;
            }

            if (pattern_change)
            {
               pattern_change = false;

               if (mPlayerListener != null)
                  mPlayerListener.onPlayerEvent(EVENT_PATTERN_CHANGE);
            }
            //
            //   TODO: Implement a listener notification for when a
            //         song is completed.
            //
            //
            //if (song_complete) {
            //   song_complete = false;
            //
            //   if (mPlayerListener != null)
            //      mPlayerListener.onPlayerEvent(EVENT_SONG_COMPLETED);
         }

         // ******************* WAIT CODE ***********************
         synchronized (this)
         {
            if (mWaitFlag)
            {
               if (sLogOutput)
                  Log.i(LOGPREFIX, "in run() GOT THE SYNCH asked to *** WAIT *** thread "+this.getId());
               try
               {
                  wait();
                  if (sLogOutput)
                     Log.i(LOGPREFIX, "in run() woke up from ... WAIT ... thread "+this.getId());

                  if (mFlushedData)
                  {
                     if (sLogOutput)
                        Log.i(LOGPREFIX, "sleep() workaround to force flush() of audio data");
                     // according to Sasq here: 
                     // http://groups.google.com/group/android-developers/browse_thread/thread/cdd809d1076f0804
                     // need a short(?) sleep() to convince the AudioTrack to actually do the flush of data...
                     sleep(20);
                  }
               }
               catch (Exception e)
               {
                  Log.e(LOGPREFIX, "GOT AN EXCEPTION TRYING TO WAIT!!! thread "+this.getId());
                  e.getCause().printStackTrace();
               }
            }
         }
         // clear flushed flag
         mFlushedData = false;
      }
      //**********************
      // experimental
      //**********************
      mMytrack.release();
   }

   //
   // MOD file info getters
   /**
    * Get the name of the song.
    *                              
    * @return the name of the song (from the MOD/XM file header)
    */
   public String getModName() {
      return mModname;
   }

   /**
    * Get the number of channels used in the song (MOD/XM songs typically use from 4 to 32 channels
    * in a pattern, mixed together for awesomeness).
    *                              
    * @return the number of channels the song uses
    */
   public int getNumChannels() {
      return mNumChannels;
   }

   /**
    * Get the file size of the MOD/XM song.
    *                              
    * @return the size of the song file
    */
   public int getModSize() {
      return mModsize;
   }

   public int getRate() {
      return mRate;
   }

   //
   // Pause/UnPause code
   /**
    * Pauses playback of the current song.
    */
   public void PausePlay() {
      mPlaying = false;

      // this check is usually not needed before stop()ing the audio track, but seem
      // to get an uninitialized audio track here occasionally, generating an IllegalStateException
      if (mMytrack.getState() == AudioTrack.STATE_INITIALIZED)
         mMytrack.stop();

      mWaitFlag = true;
      synchronized(this) {
         this.notify();
      }
   }
    
   /**
    * Resumes playback of the current song.
    */
   public void UnPausePlay() {
      mMytrack.play();

      mPlaying = true;

      mWaitFlag = false;
      synchronized(this) {
         this.notify();
      }
   }
   
   public void Flush() {
      if (!mPlaying) {
         mMytrack.flush();
         
         mFlushedData = true;
      }
   }

    
   //
   // sets volume with an integer value from 0-255 in 8 increments
   //
   // probably easier to just pass in a float!
   //
   /**
    * Sets playback volume for the MOD/XM player.
                             
    * The vol argument is an integer from 0 (sound off) to 255 (full volume)
                             
   @param  vol an integer from 0 (sound off) to 255 (full volume)
    *  
    */
    public void setVolume(int vol) {
       vol = vol>>5;
       if (vol>7) vol = 7;
       if (vol<0) vol = 0;
       mMytrack.setStereoVolume(sVolume_floats[vol], sVolume_floats[vol]);
    }

   /**
    * Sets playback volume for the MOD/XM player.
                             
    * The vol argument is floating point number from 0.0f (sound off) to 1.0f (full volume)
                             
   @param  vol a floating point number from 0.0f (sound off) to 1.0f(full volume)
    *  
    */
   public void setVolume(float vol) {
      if (vol>1.0f) vol = 1.0f;
      if (vol<0) vol = 0;
      mMytrack.setStereoVolume(vol, vol);
   }

   //
   // startup options
   public void startPaused(boolean flag) {
      // set before calling the thread's start() method, will cause it
      // to start in paused mode
      mStart_paused = flag;
   }

   public void playthroughOnce(boolean flag) {
      // to wake up the audio pcm playback track
      mPlay_once = flag;
   }

   //
   // closing down code
   /**
    * Stop the player thread (completely stops it, as opposed to pausing)
    *
    *  <p>Typically the player should then be <code>join()</code>ed to completely
    *  remove the thread from the application's Android process, and also call
    *  <code>CloseLIBMODPLUG()</code> to close the native player library and de-allocate
    *  all resources it used.
    */
   public void StopThread() {
      // stops the thread playing  (see run() above)
      mPlaying = false;
      mRunning = false;
      // this check is usually not needed before stop()ing the audio track, but seem
      // to get an uninitialized audio track here occasionally, generating an IllegalStateException
      if (mMytrack.getState() == AudioTrack.STATE_INITIALIZED)
         mMytrack.stop();

      mPlayerValid = false;
      mWaitFlag    = false;

      synchronized(this) {
         this.notify();
      }
   }
    
   /**
    * Close the native internal tracker library (libmodplug) and de-allocate any resources.
    */
   public static void CloseLIBMODPLUG() {
       //ModPlug_JUnload(mdunused, MAXMODSIZE);
       ModPlug_JUnload(mdunused, 0);
       //Log.i("CloseLIBMODPLUG()", "JUnload() returned!");
       ModPlug_CloseDown();
       //Log.i("CloseLIBMODPLUG()", "CloseDown() returned!");
   }

   //
   //
   // Hack function to modify tempo on the fly
   /**
    * EXPERIMENTAL method for modifying the song's tempo (+ or -) by <code>mt</code>.
    * @param  mt modifier for the song's "native" tempo (positive values to increase tempo,
    *  negative values to decrease tempo)
    */
    public void modifyTempo(int mt) {
       ModPlug_ChangeTempo(mt);
    }
   /**
    * EXPERIMENTAL method for setting the song's tempo to <code>temp</code>.
    * @param  temp the tempo for the song (overrides song's "native" tempo)
    */
    public void setTempo(int temp) {
       ModPlug_SetTempo(temp);
    }

   /**
    * EXPERIMENTAL: Get the default tempo from the song's header.
    *                              
    * @return the tempo
    */
   public int getSongDefaultTempo() {
      return ModPlug_GetNativeTempo();
   }
   /**
    * EXPERIMENTAL: Get the current "position" in song
    *                              
    * @return the position
    */
   public int getCurrentPos() {
      return ModPlug_GetCurrentPos();
   }
   /**
    * EXPERIMENTAL: Get the current order
    *                              
    * @return the order
    */
   public int getCurrentOrder() {
      return ModPlug_GetCurrentOrder();
   }
   /**
    * EXPERIMENTAL: Get the current pattern
    *                              
    * @return the pattern
    */
   public int getCurrentPattern() {
      return ModPlug_GetCurrentPattern();
   }
   /**
    * EXPERIMENTAL: set the current pattern (pattern is changed but plays from current row in pattern)
    *                              
    * @param pattern the new pattern to start playing immediately
    */
   public void setCurrentPattern(int pattern) {
      ModPlug_SetCurrentPattern(pattern);
   }
   /**
    * EXPERIMENTAL: set the next pattern to play after current pattern finishes
    *                              
    * @param pattern the new pattern to start playing after the current pattern finishes playing
    */
   public void setNextPattern(int pattern) {
      ModPlug_SetNextPattern(pattern);
   }
   /**
    * EXPERIMENTAL: Get the current row in the pattern
    *                              
    * @return the row
    */
   public int getCurrentRow() {
      return ModPlug_GetCurrentRow();
   }
   /**
    * EXPERIMENTAL: Set log printing flag
    *                              
    * @param flag true to start printing debug information to log output, false to stop
    */
   public void setLogOutput(boolean flag) {
      sLogOutput = flag;
      ModPlug_LogOutput(flag);
   }
    /**
    * EXPERIMENTAL method to change patterns in a song (playing in PATTERN LOOP mode). Waits for
    * the currently playing pattern to finish.
    * 
    * @param  newpattern the new song pattern to start playing(repeating) in PATTERN LOOP mode
    */
   public void changePattern(int newpattern) {
      ModPlug_ChangePattern(newpattern);
   }
    /**
    * EXPERIMENTAL method to change song to PATTERN LOOP mode, repeating <code>pattern</code>
    * @param  pattern the song pattern to start playing(repeating) in PATTERN LOOP mode
    */
   public void repeatPattern(int pattern) {
      ModPlug_RepeatPattern(pattern);
   }
   /**
    * EXPERIMENTAL method to loop song in a group of patterns.
    * @param  from  start of pattern range to play in loop
    * @param  to end of pattern range to play in loop
    * @param  when is a constant flag (PATTERN_CHANGE_IMMEDIATE, PATTERN_CHANGE_AFTER_CURRENT, 
    * PATTERN_CHANGE_AFTER_GROUP) to signal when the new pattern range should take effect
    */
   public void setPatternLoopRange(int from, int to, int when) {
      Log.i(LOGPREFIX, "Setting pattern range from "+from+" to "+to);
      ModPlug_SetPatternLoopRange(from, to, when);
   }
   /**
    * EXPERIMENTAL method to loop song the specified number of times.
    * @param  number of times to loop (-1 = forever)
    */
   public void setLoopCount(int loopcount) {
      ModPlug_SetLoopCount(loopcount);
   }
   /**
    * EXPERIMENTAL method to set song to PATTERN LOOP mode, repeating any pattern playing
    * or subsequently set via <code>changePattern()</code>
    * @param  flag true to set PATTERN LOOP mode, false to turn off PATTERN LOOP mode
    */
   public void setPatternLoopMode(boolean flag) {
      ModPlug_SetPatternLoopMode(flag);
   }
   //
   // unload the current mod from libmodplug, but make sure to wait until any GetSoundData()
   // call in the player thread has finished.
   //
   /**
    * Unload MOD/XM data previously loaded into the native player library.
    */
   public void UnLoadMod()
   {
      // since this can/will be called from the UI thread, need to synch and not
      // have a call into libmodplug unloading the file, while a call to GetModData() is
      // also executing in the player thread (see run() above)
      synchronized(sRDlock) {
         //ModPlug_JUnload(mdunused, MAXMODSIZE);
         ModPlug_JUnload(mdunused, 0);
      }
   }

   //
   // Native methods in our JNI libmodplug stub code
   //
   // Some of these don't do anything (CloseDown(), since 
   // I haven't tried to make the libmodplug JNI stub code truly re-entrant...
   //
   //

   // Init() now takes a sample rate in case the Android device doesn't support higher rates?!? 
   public static native boolean ModPlug_Init(int rate);                       // init libmodplug
   public native boolean ModPlug_JLoad(byte[] buffer, int size);              // load a mod file (in the buffer)
   public native String ModPlug_JGetName();                                   // for info only, gets the mod's name
   public native int ModPlug_JNumChannels();                                  // info only, how many channels are used
   public native int ModPlug_JGetSoundData(short[] sndbuffer, int datasize);  // get another packet of sample data
   public static native boolean ModPlug_JUnload(byte[] buffer, int size);     // unload a mod file
   public static native boolean ModPlug_CloseDown();                          // close down libmodplug

   // HACKS ;-)
   public static native int ModPlug_GetNativeTempo();
   public static native void ModPlug_ChangeTempo(int tempotweak); 
   public static native void ModPlug_SetTempo(int tempo); 
   public static native void ModPlug_ChangePattern(int newpattern);
   public static native void ModPlug_RepeatPattern(int pattern);
   public static native boolean ModPlug_CheckPatternChange();
   public static native void ModPlug_SetPatternLoopMode(boolean flag);

   public native void ModPlug_SetCurrentPattern(int pattern);
   public native void ModPlug_SetNextPattern(int pattern);

   // more info
   public native int ModPlug_GetCurrentPos();
   public native int ModPlug_GetCurrentOrder();
   public native int ModPlug_GetCurrentPattern();
   public native int ModPlug_GetCurrentRow();

   // FOURBYFOUR
   public static native void ModPlug_SetPatternLoopRange(int from, int to, int when);
   public static native void ModPlug_SetLoopCount(int loopcount);

   // Log output
   public static native void ModPlug_LogOutput(boolean flag);

   static
   {
      try {
         System.loadLibrary("modplug-"+VERS);
         //System.loadLibrary("modplug");
      }
      catch (UnsatisfiedLinkError ule) {
         Log.e("PLAYERTHREAD", "WARNING: Could not load libmodplug-"+VERS+".so"); 
         //Log.e("PLAYERTHREAD", "WARNING: Could not load libmodplug.so");
         Log.e("PLAYERTHREAD", "------ older or differently named libmodplug???");
      }

      // get lock objects for synchronizing access to playervalid flag and
      // GetSoundData() call
      sPVlock = new Object();
      sRDlock = new Object();
   }
}
