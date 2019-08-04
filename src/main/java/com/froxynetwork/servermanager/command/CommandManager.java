package com.froxynetwork.servermanager.command;

import java.util.Comparator;
import java.util.Scanner;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.servermanager.Main;

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
		if ("stop".equalsIgnoreCase(cmd)) {
			// Stop
			stop = true;
		} else if ("currentThreads".equalsIgnoreCase(cmd)) {
			// Debug: Get current Threads
			Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
			LOG.info("Current Threads:");
			threadSet.stream().sorted(Comparator.comparing(Thread::getId)).forEach(t -> {
				LOG.info(t.getId() + " - " + t.getName());
			});
		} else {
			// Unknown command
			LOG.info("/" + cmd + ": Unknown command");
		}
	}
}
