package vn.com.sonca.MyLog;

import android.util.Log;

public class MyLog {

	public static void e(String tag, Exception ex) {
		if (Configuration.DEBUG == true) {
			Debugger.createBreakPoint();
			ex.printStackTrace();
		}
	}
	
	public static void e(String tag, String msg) {
		if (Configuration.DEBUG == true) {
			Log.e(tag, msg);
		}
	}

	public static void i(String tag, String msg) {
		if (Configuration.DEBUG == true) {
			Log.i(tag, msg);
		}
	}
	
	public static void d(String tag, String msg) {
		if (Configuration.DEBUG == true) {
			Log.d(tag, msg);
		}
	}
}
