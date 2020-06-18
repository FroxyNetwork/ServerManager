package com.froxynetwork.servermanager.server;

import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.ServerStatus;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServerImpl;
import com.froxynetwork.servermanager.scheduler.Scheduler;

import lombok.Getter;

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
public class Server {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	@Getter
	private UUID uuid;
	@Getter
	private String id;
	@Getter
	private String name;
	@Getter
	private String type;
	@Getter
	private int port;
	@Getter
	private ServerStatus status;
	@Getter
	private Date creationTime;
	@Getter
	private boolean bungee;
	@Getter
	private WebSocketServerImpl webSocket;
	@Getter
	private int timeout;

	public Server(UUID uuid, String id,
			com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server restServer,
			boolean bungee) {
		this.uuid = uuid;
		this.id = id;
		this.name = restServer.getName();
		this.type = restServer.getType();
		this.port = restServer.getPort();
		this.status = restServer.getStatus();
		this.creationTime = restServer.getCreationTime();
		this.bungee = bungee;
		resetTimeout();
	}

	/**
	 * Send a message throw WebSocket to this VPS
	 * 
	 * @param channel The channel to use
	 * @param message The message to send
	 */
	public void sendMessage(String channel, String message) {
		Scheduler.add(() -> {
			if (!isLinked())
				return false;
			try {
				webSocket.sendCommand(channel, message);
			} catch (Exception ex) {
				LOG.error("Error while sending a message to server {} with channel {}", id, channel);
				LOG.error("", ex);
				return false;
			}
			return true;
		}, null);
	}

	/**
	 * Check if this VPS is linked with the CoreManager
	 * 
	 * @return true if there is a WebSocket connection between the CoreManager and
	 *         this VPS
	 */
	public boolean isLinked() {
		return webSocket != null && webSocket.isConnected();
	}

	/**
	 * Resume this webSocket
	 * 
	 * @param webSocket
	 */
	public void resumeWebSocket(WebSocketServerImpl webSocket) {
		this.webSocket = webSocket;
		if (webSocket == null)
			return;
	}

	public void resetTimeout() {
		timeout = 60;
	}

	public void timeout() {
		timeout--;
	}
}
