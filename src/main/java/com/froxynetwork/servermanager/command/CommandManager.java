package com.froxynetwork.servermanager.command;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.servermanager.Main;

/**
 * MIT License
 *
 * Copyright (c) 2019 FroxyNetwork
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
public class CommandManager {

	private final Logger LOG = LoggerFactory.getLogger(getClass());
	private Main main;

	private Thread commandThread;
	private boolean stop = false;

	public CommandManager(Main main) {
		this.main = main;

		commandThread = new Thread(() -> {
			LOG.info("Starting Thread \"ServerManager - Command Handler\"");
			try (Scanner sc = new Scanner(System.in)) {
				while (!stop) {
					String cmd = sc.nextLine();
					if (cmd == null || "".equalsIgnoreCase(cmd.trim()))
						continue;
					handleCommand(cmd);
				}
			}
			// End
			LOG.info("Shutdowning NetworkManager");
			main.getNetworkManager().shutdown();
			LOG.info("Ending Thread \"ServerManager - Command Handler\"");
		}, "ServerManager - Command Handler");
		commandThread.start();
	}

	public void handleCommand(String cmd) {
		if (cmd == null || "".equalsIgnoreCase(cmd))
			return;
		String command = cmd.split(" ")[0];
		String[] args = commandToArgs(cmd);
		if (!handleCommand(command, args)) {
			// Unknown command
			LOG.info("/" + command + ": Unknown command");
		}
	}

	private boolean handleCommand(String label, String[] args) {
		if ("stop".equalsIgnoreCase(label)) {
			// Stop
			stop = true;
			return true;
		} else if ("currentThreads".equalsIgnoreCase(label)) {
			// Debug: Get current Threads
			Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
			LOG.info("Current Threads:");
			threadSet.stream().sorted(Comparator.comparing(Thread::getId)).forEach(t -> {
				LOG.info(t.getId() + " - " + t.getName());
			});
			return true;
		} else if ("create".equalsIgnoreCase(label)) {
			if (args.length < 1 || args.length > 1) {
				LOG.info("Syntax error: /create <type>");
				return true;
			}
			String type = args[0];
			main.getServerManager().openServer(type, srv -> {
				LOG.info("Done, srv = " + srv);
			}, () -> {
				LOG.error("ERROR");
			});
			return true;
		}
		return false;
	}

	public String[] commandToArgs(String cmd) {
		String[] split = cmd.split(" ");
		return Arrays.copyOfRange(split, 1, split.length);
	}
}
