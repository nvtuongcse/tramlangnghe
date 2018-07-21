package vn.com.sonca.MyLog;

public class Debugger {

	public static void createBreakPoint() {
		if (Configuration.DEBUG == true) {
			android.os.Debug.waitForDebugger();
		}
	}
}
