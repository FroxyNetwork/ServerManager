package com.froxynetwork.servermanager.websocket.commands.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.websocket.IWebSocketCommander;
import com.froxynetwork.servermanager.Main;

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
public class ServerStopCommand implements IWebSocketCommander {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	@Override
	public String name() {
		return "stop";
	}

	@Override
	public String description() {
		return "Stop an existing server";
	}

	@Override
	public void onReceive(String message) {
		// stop <id>
		if (message == null)
			return;
		if (message.indexOf(' ') != -1) {
			LOG.warn("Invalid \"stop\" command ! Got {}", message);
			return;
		}
		Main.get().getServerManager().closeServer(message, () -> {
			LOG.error("Error while stoping server {}", message);
		});
	}
}
