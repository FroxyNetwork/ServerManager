package com.froxynetwork.servermanager.websocket.commands.core;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.websocket.IWebSocketCommander;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketClientImpl;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.scheduler.Scheduler;

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
public class ServerStartCommand implements IWebSocketCommander {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private static final Pattern space = Pattern.compile(" ");

	private WebSocketClientImpl webSocket;

	public ServerStartCommand(WebSocketClientImpl webSocket) {
		this.webSocket = webSocket;
	}

	@Override
	public String name() {
		return "start";
	}

	@Override
	public String description() {
		return "Start a new server";
	}

	@Override
	public void onReceive(String message) {
		// start <uuid> <type>
		if (message == null)
			return;
		String[] args = space.split(message);
		if (args.length != 2) {
			LOG.warn("Invalid \"start\" command ! Got {}", message);
			return;
		}
		final UUID uuid;
		try {
			uuid = UUID.fromString(args[0]);
		} catch (Exception ex) {
			LOG.warn("{} is not a valid uuid", message);
			return;
		}
		String type = args[1];
		// Check type
		if (!"BUNGEE".equalsIgnoreCase(type) && !Main.get().getServerConfigManager().exist(type)) {
			LOG.error("Type {} does not exist !");
			Scheduler.add(() -> {
				if (!webSocket.isAuthenticated())
					return false;
				webSocket.sendCommand("error", uuid.toString());
				return true;
			}, () -> {
				LOG.error("Error while sending error command !");
			});
			return;
		}
		Main.get().getServerManager().openServer(type, uuid, () -> {
			LOG.error("Error while opening server {} (uuid = {})", type, uuid.toString());
			Scheduler.add(() -> {
				if (!webSocket.isAuthenticated())
					return false;
				webSocket.sendCommand("error", uuid.toString());
				return true;
			}, () -> {
				LOG.error("Error while sending error command !");
			});
		});
	}
}
