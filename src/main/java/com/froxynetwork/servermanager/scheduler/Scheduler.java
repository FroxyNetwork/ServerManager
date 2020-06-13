package com.froxynetwork.servermanager.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * MIT License
 *
 * Copyright (c) 2020 FroxyNetwork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * @author 0ddlyoko
 */
/**
 * Execute an action every seconds until the action has been correctly executed
 */
public class Scheduler {
	private static List<CustomScheduler> execute;
	private static Thread runnable;
	private static boolean stop = false;

	static {
		start();
	}

	/**
	 * Execute an action every seconds until the action is correctly executed.<br />
	 * If Scheduler is stopped (by a reload or something else), error is
	 * called<br />
	 * When you call this method, the action is directly executed and saved if the
	 * action fail
	 * 
	 * @param exec  The action to execute
	 * @param error The action to execute if Scheduler is stopped
	 */
	public static void add(Supplier<Boolean> exec, Runnable error) {
		boolean b = exec.get();
		if (!b)
			execute.add(new CustomScheduler(exec, error));
	}

	public static void start() {
		// Avoid starting when already running
		if (stop)
			return;
		execute = new ArrayList<>();
		runnable = new Thread(() -> {
			while (!stop) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
					break;
				}
				// To avoid ConcurrentModificationException
				List<CustomScheduler> copy = new ArrayList<>(execute);
				execute = new ArrayList<>();
				for (CustomScheduler cs : copy)
					try {
						if (!cs.getExec().get())
							execute.add(cs);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
			}
		});
		runnable.start();
	}

	/**
	 * Stop this Scheduler and call errors for each remaining schedulers
	 */
	public static void stop() {
		stop = true;
		if (runnable.isAlive())
			runnable.interrupt();
		for (CustomScheduler cs : execute)
			if (cs.getError() != null)
				try {
					cs.getError().run();
				} catch (Exception ex) {
					// Empty exception
				}
		execute.clear();
	}
}
