package com.froxynetwork.servermanager.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.server.config.ServerConfig;
import com.froxynetwork.servermanager.server.config.ServerVps;

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

	private Thread commandThread;
	private boolean stop = false;

	public CommandManager() {
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
			Main.get().stop();
			LOG.info("Ending Thread \"ServerManager - Command Handler\"");
		}, "ServerManager - Command Handler");
		commandThread.start();
	}

	private Pattern pattern = Pattern.compile(" ");

	public void handleCommand(String cmd) {
		if (cmd == null || "".equalsIgnoreCase(cmd))
			return;
		String[] split = pattern.split(cmd);
		String command = split[0];
		String[] args = Arrays.copyOfRange(split, 1, split.length);
		boolean ok = false;
		try {
			ok = handleCommand(command, args);
		} catch (Exception ex) {
			LOG.error("", ex);
			return;
		}
		if (!ok) {
			// Unknown command
			LOG.info("/" + command + ": Unknown command");
		}
	}

	private boolean handleCommand(String label, String[] args) {
		if ("end".equalsIgnoreCase(label)) {
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
		} else if ("start".equalsIgnoreCase(label)) {
			if (args.length != 1) {
				LOG.info("Syntax error: /start <type>");
				return true;
			}
			String type = args[0];
			Main.get().getServerManager().openServer(type, UUID.randomUUID(), () -> {
				// Error
				LOG.error("Failed while opening a server !");
			});
			return true;
		} else if ("stop".equalsIgnoreCase(label)) {
			if (args.length != 1) {
				LOG.info("Syntax error: /stop <id>");
				return true;
			}
			Main.get().getServerManager().closeServer(args[0], () -> {
				LOG.info("{}: Server deleted !", args[0]);
			});
			return true;
		} else if ("list".equalsIgnoreCase(label)) {
			// List all different types
			Collection<ServerConfig> serverConfigs = Main.get().getServerConfigManager().getAll();
			LOG.info("Number of types: {}", serverConfigs.size());
			for (ServerConfig sc : serverConfigs)
				LOG.info("- Type: {}, database: {}", sc.getType(), sc.getDatabase());
			return true;
		} else if ("reload".equalsIgnoreCase(label)) {
			LOG.info("Reloading servers");
			try {
				Main.get().getServerConfigManager().reload(() -> {
					LOG.info("Server reload done");
				});
			} catch (RestException ex) {
				LOG.error("Error while reloading servers: ", ex);
			} catch (Exception ex) {
				LOG.error("Error while reloading servers: ", ex);
			}
			return true;
		} else if ("info".equalsIgnoreCase(label)) {
			ServerVps sv = Main.get().getServerManager().getServerVps();
			LOG.info("- Id: {}", sv.getId());
			LOG.info("- Host: {}", Main.get().getWebSocketManager().getUrl());
			LOG.info("- Port: {}", Main.get().getWebSocketManager().getPort());
			LOG.info("- MaxServers: {}", sv.getMaxServers());
			return true;
		}

		return false;
	}
}
