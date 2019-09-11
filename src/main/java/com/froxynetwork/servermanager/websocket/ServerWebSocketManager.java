package com.froxynetwork.servermanager.websocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.websocket.WebSocketManager;

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
public class ServerWebSocketManager extends WebSocketServer {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	@Getter
	private String url;
	@Getter
	private int port;
	@Getter
	private boolean stopped;
	private List<WebSocketManager> servers;

	public ServerWebSocketManager(String url, int port) {
		super(new InetSocketAddress(url, port));
		LOG.info("WebSocket running on url = {} and port = {}", url, port);
		this.url = url;
		this.port = port;
		this.stopped = true;
		this.servers = new ArrayList<>();
	}

	@Override
	public void start() {
		// Already running
		if (!stopped)
			return;
		stopped = false;
		super.start();
	}

	@Override
	public void stop() {
		try {
			super.stop();
		} catch (Exception ex) {
		}
	}

	@Override
	public void onStart() {
		LOG.info("Server started");
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("new connection to " + conn.getRemoteSocketAddress());
		conn.send("Welcome to the server!"); // This method sends a message to the new client
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		System.out.println(
				"closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		System.out.println("received message from " + conn.getRemoteSocketAddress() + ": " + message);
	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer message) {
		System.out.println("received ByteBuffer from " + conn.getRemoteSocketAddress());
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		System.err.println("an error occured on connection " + conn.getRemoteSocketAddress() + ":" + ex);
	}

}
