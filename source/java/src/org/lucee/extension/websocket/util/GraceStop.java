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

		// Instead of calling the removed stop() method, directly interrupt the thread
		if (thread != null && thread.isAlive() && thread != Thread.currentThread()) {
			thread.interrupt();
		}
	}

	public static void sleep(int time) {
		try {
			Thread.sleep(time);
		}
		catch (InterruptedException e) {
		}
	}
}