package com.froxynetwork.servermanager.websocket;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.java_websocket.WebSocket;
import org.java_websocket.enums.ReadyState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.servermanager.server.Server;

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
public class WebSocketServerImpl {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	@Getter
	private WebSocket client;
	@Getter
	private InetSocketAddress socketAddress;
	@Getter
	@Setter
	private Server server;

	private List<Consumer<Boolean>> listenerDisconnection;
	private List<String> listeners;

	public WebSocketServerImpl(WebSocket client) {
		this.client = client;
		this.listenerDisconnection = new ArrayList<>();
		this.listeners = new ArrayList<>();
		this.socketAddress = client.getRemoteSocketAddress();
	}

	/**
	 * @return true if the app is connected with the WebSocket server
	 */
	public boolean isConnected() {
		return client.isOpen();
	}

	/**
	 * Disconnect if already connected
	 */
	public void disconnect() {
		client.close();
	}

	/**
	 * Method called when this client is disconnectedS
	 * 
	 * @param remote True means the remote has closed the connection
	 */
	public void onDisconnection(boolean remote) {
		for (Consumer<Boolean> r : listenerDisconnection)
			r.accept(remote);
	}

	/**
	 * Register an event that is called when the app is disconnected to the
	 * WebSocket
	 * 
	 * @param run The action to execute. Parameter depends on the closing of the
	 *            connection that was initiated or not by the WebSocket server
	 */
	public void registerWebSocketDisconnection(Consumer<Boolean> run) {
		if (!listenerDisconnection.contains(run))
			listenerDisconnection.add(run);
	}

	/**
	 * Unregister the registered event
	 * 
	 * @param run
	 */
	public void unregisterWebSocketDisconnection(Consumer<Boolean> run) {
		listenerDisconnection.remove(run);
	}

	/**
	 * @return The current state of the connection with the WebSocket
	 */
	public ReadyState getConnectionState() {
		return client.getReadyState();
	}

	/**
	 * Send a message to specific client
	 * 
	 * @param server  The server
	 * @param channel The channel
	 * @param message The message
	 */
	public void sendMessage(String server, String channel, String message) {
		client.send(server + " " + channel
				+ ((message == null || "".equalsIgnoreCase(message.trim())) ? "" : " " + message));
	}

	/**
	 * Add listener for specific channel
	 * 
	 * @param channel The channel
	 */
	public void addChannelListener(String channel) {
		if (listeners.contains(channel))
			return;
		listeners.add(channel);
	}

	/**
	 * Remove listener for specific channel
	 * 
	 * @param channel The channel
	 */
	public void removeChannelListener(String channel) {
		listeners.remove(channel);
	}

	/**
	 * Return true if this client is listening to this channel
	 * 
	 * @param channel The channel to check if client is listening
	 * @return true if this client is listening to this channel
	 */
	public boolean isListening(String channel) {
		return listeners.contains(channel);
	}

	/**
	 * @return true if this client is authentified
	 */
	public boolean isAuthentified() {
		return server != null;
	}
}
