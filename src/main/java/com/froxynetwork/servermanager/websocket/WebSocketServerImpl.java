package com.froxynetwork.servermanager.websocket;

import java.util.function.Consumer;

import org.java_websocket.enums.ReadyState;

import com.froxynetwork.froxynetwork.network.websocket.IWebSocket;

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
public class WebSocketServerImpl implements IWebSocket {

	@Override
	public boolean isConnected() {
	}

	@Override
	public void reconnect() {
	}

	@Override
	public void disconnect() {
	}

	@Override
	public void registerWebSocketConnection(Consumer<Boolean> run) {
	}

	@Override
	public void unregisterWebSocketConnection(Consumer<Boolean> run) {
	}

	@Override
	public void registerWebSocketDisconnection(Consumer<Boolean> run) {
	}

	@Override
	public void unregisterWebSocketDisconnection(Consumer<Boolean> run) {
	}

	@Override
	public ReadyState getConnectionState() {
	}

	@Override
	public void sendChannelMessage(String channel, String message) {
	}

	@Override
	public void addChannelListener(String channel, Consumer<String> listener) {
	}

	@Override
	public void removeChannelListener(String channel, Consumer<String> listener) {
	}

	@Override
	public void removeChannelListener(String channel) {
	}
}
