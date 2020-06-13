package com.froxynetwork.servermanager.websocket.commands.core;

import java.util.regex.Pattern;

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
public class ServerNewCommand implements IWebSocketCommander {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private Pattern spacePattern = Pattern.compile(" ");

	@Override
	public String name() {
		return "new";
	}

	@Override
	public String description() {
		return "New server";
	}

	@Override
	public void onReceive(String message) {
		// message = <id> <type>
		String[] split = spacePattern.split(message);
		if (split.length < 2) {
			// Error
			LOG.error("Invalid message: {}", message);
			return;
		}
		String id = split[0];
		String type = split[1];
		// Do not continue if it's a bungee
		if (type.equalsIgnoreCase("BUNGEE"))
			return;

		// All seams ok
		Main.get().getServerManager().newServer(id, type);
	}
}