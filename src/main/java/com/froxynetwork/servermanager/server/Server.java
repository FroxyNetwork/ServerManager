package com.froxynetwork.servermanager.server;

import com.froxynetwork.servermanager.websocket.WebSocketServerImpl;

import lombok.Getter;
import lombok.Setter;

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
@Getter
public class Server {
	// The id of the server
	private String id;
	// Information returned by the rest
	private com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server restServer;
	// The VPS
	private Vps vps;
	// The container id
	private String containerId;
	// The WebSocket
	private WebSocketServerImpl webSocketServerImpl;
	@Setter
	private boolean closed;
	private int defaultWebSocketAuthTimeout;
	private int webSocketAuthTimeout;

	public Server(String id,
			com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server restServer, Vps vps,
			String containerId, int webSocketAuthTimeout) {
		this.id = id;
		this.restServer = restServer;
		this.vps = vps;
		this.containerId = containerId;
		this.closed = false;
		this.webSocketAuthTimeout = this.defaultWebSocketAuthTimeout = webSocketAuthTimeout;
	}

	public int getPort() {
		return restServer.getPort();
	}

	public boolean isBungee() {
		return vps.getBungee() == this;
	}

	public void setWebSocketServerImpl(WebSocketServerImpl webSocketServerImpl) {
		this.webSocketServerImpl = webSocketServerImpl;
		this.webSocketAuthTimeout = this.defaultWebSocketAuthTimeout;
	}

	public void timeOut() {
		webSocketAuthTimeout--;
	}
}
