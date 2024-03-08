package org.lucee.extension.websocket.util;

public class GraceStop extends Thread {

	private Thread thread;
	private int gracePeriod;

	public GraceStop(Thread thread, int gracePeriod) {
		this.thread = thread;
		this.gracePeriod = gracePeriod;
	}

	@Override
	public void run() {
		if (gracePeriod > 0) {
			sleep(gracePeriod);
		}
		stop(thread);
	}

	public static void stop(Thread thread) {
		if (thread == null || !thread.isAlive() || thread == Thread.currentThread()) return;

		// first we try to interupt, the we force a stop
		if (!_stop(thread, false)) _stop(thread, true);
	}

	private static boolean _stop(Thread thread, boolean force) {
		// we try to interrupt/stop the suspended thrad
		suspendEL(thread);
		try {
			if (!force) thread.interrupt();
			else thread.stop();
		}
		finally {
			resumeEL(thread);
		}
		// a request still will create the error template output, so it can take some time to finish
		for (int i = 0; i < 100; i++) {
			if (!thread.isAlive()) {
				return true;
			}
			sleep(10);
		}
		return false;
	}

	public static void suspendEL(Thread t) {
		try {
			t.suspend();
		}
		catch (Exception e) {}
	}

	public static void resumeEL(Thread t) {
		try {
			t.resume();
		}
		catch (Exception e) {}
	}

	public static void sleep(int time) {
		try {
			Thread.sleep(time);
		}
		catch (InterruptedException e) {}
	}
}