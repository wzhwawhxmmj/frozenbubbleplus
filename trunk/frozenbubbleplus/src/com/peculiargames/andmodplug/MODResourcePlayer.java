// This is basically a convenience class that adds
// a layer on top of the PlayerThreadOLD so that you
// don't have to mess around with loading the MOD
// file data from a MOD file resource (R.raw.)
// yourself.
//
// Dec 7 2011   P.A.Casey
//

package com.peculiargames.andmodplug;

import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.util.Log;

/**
 * Convenience class extending PlayerThread, handling all the file operations,
 * accepting Android resource ids for MOD/XM song files.
 *	                  
 * <p>   <b>Typical call order:</b>
 * <br>   <code>mrp = MODResourcePlayer(); // get player instance (in topmost activity, etc.)</code>
 * <br>	  <code>mrp.LoadMODResource(R.raw.coolsong);  // load MOD/XM data into player</code>
 * <br>   <code>mrp.start();   // start thread (playing song)</code>
 * <br>
 * 	<b>then when changing songs (new game level or transition to another sub-activity, etc.):</b> 
 * 	<br>  <code>mrp.PausePlay();</code>
 *  <br>  <code>mrp.LoadMODResource(R.raw.newcoolsong);</code>
 *  <br>  <code>mrp.UnPausePlay();</code>
 *  <br> <code>// repeat...</code>
 * 
 * @author    P.A. Casey (crow) Peculiar-Games.com
 *              
 * @version   1.0
 *  
 */
public class MODResourcePlayer extends PlayerThread {

	//
	// prefix for Log output
	private final static String LOGPREFIX = "MODRESPLAYER";
	
	// application context, for accessing the resources (specifically the
	// R.raw. resources which are MOD music files)
	private Context mContext;
	
	private InputStream mModfileInStream;
	private byte[] modData = null;
	private int modsize;    // holds the size in bytes of the mod file

	/**
	 * Allocates a MOD/XM/etc. song Player object that can handle Android resource
	 * files (typically the songs are stored in the res/raw project directory
	 * and conform to Android build process rules, lower-case names, etc.)
	 * <p>
	 * <b>Note about extensions:</b> developers using Eclipse as an IDE should note
	 * that it allows the .xm file extension but may be fussy about other tracker
	 * format extensions.
	 *	                          	
	 * The cont argument is the application context which allows MODResourcePlayer to
	 * load resources directly.
	                          
	@param  cont application Context
	 *  
	 */
	public MODResourcePlayer(Context cont) {
		// get super class (PlayerThread) with default rate
		super(0);
		
		mContext = cont;
		
		// full volume
        setVolume(255);
	}
	
	/**
	 * Load a MOD/XM/etc. song file from an Android resource.
	 * <p>
	 * <b>Note about extensions:</b> developers using Eclipse as an IDE should note
	 * that it allows the .xm file extension but may be fussy about other tracker
	 * format extensions.
	 *	                          	
	 * The modresource argument is the resource id for the MOD/XM song file, e.g. R.raw.coolsong
	                          
	@param  modresource Android resource id for a MOD/XM/etc. (tracker format) song file
	 *  
	 */
	public boolean LoadMODResource(int modresource) {
		int currfilesize = 0;
		
		// unload any mod file we have currently loaded
		UnLoadMod();
		
		// get an input stream for the MOD file resource
        mModfileInStream = 	mContext.getResources().openRawResource(modresource);
        try {
			currfilesize = mModfileInStream.available();
		} catch (IOException e) {
			Log.w(LOGPREFIX, "Can't get the file size for MOD file resource!");
			return false;
		}
		
		// if the mod file data buffer has yet to be allocated or is too small
		// we need to get a new one
		if (modData == null || modData.length < currfilesize) {
			// allocate a new buffer that can hold the current MOD file data
	        modData = new byte[currfilesize];
		}
		
        // could use better error checking
        try {
        	modsize = mModfileInStream.read(modData,0, currfilesize);
    		Log.v(LOGPREFIX, "read in "+modsize+" bytes from the mod file");
        } catch (IOException e) {
			// Auto-generated catch block
        	// may need better error handling here...
			e.printStackTrace();
		}

        // load it into the player
        LoadMODData(modData);
		
		return true;
	}
	


	/**
	 * Stop playing the song, close down the player and <code>join()</code> the player thread.
	 * <p>
	 * Typically called in the application's (Activity's) <code>onPause()</code> method 
	 */
	public void StopAndClose() {
        
		PausePlay();
		
    	boolean retry = true;

    	// now close and join() the mod player thread
    	StopThread();
        while (retry) {
        	try {
        		join();
        		retry = false;
        	} catch (InterruptedException e) {
        		// keep trying to close the player thread
        	}
        }
        PlayerThread.CloseLIBMODPLUG();

        InvalidatePlayer();
	}
	
}
