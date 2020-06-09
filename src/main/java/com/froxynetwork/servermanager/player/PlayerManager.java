package com.froxynetwork.servermanager.player;

import java.util.HashMap;
import java.util.UUID;

import com.froxynetwork.froxynetwork.network.output.data.PlayerDataOutput;

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
public class PlayerManager {
	private HashMap<UUID, Player> players;

	public PlayerManager() {
		players = new HashMap<>();
	}

	public void addPlayer(PlayerDataOutput restPlayer) {
		Player p = new Player(restPlayer);
		players.put(p.getUuid(), p);
	}

	public void removePlayer(UUID uuid) {
		players.remove(uuid);
	}

	public Player getPlayer(UUID uuid) {
		return players.get(uuid);
	}
	
//	public void editData(UUID uuid, )
}
