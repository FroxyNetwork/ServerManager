package com.froxynetwork.servermanager.player;

import java.util.Date;
import java.util.UUID;

import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.server.Server;

import lombok.Getter;
import lombok.Setter;

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
@Getter
public class Player {
	private UUID uuid;
	private String nickname;
	@Setter
	private String displayName;
	@Setter
	private int coins;
	@Setter
	private int level;
	@Setter
	private int exp;
	private Date firstLogin;
	@Setter
	private Date lastLogin;
	private String ip;
	@Setter
	private String lang;
	@Setter
	private Server server;
	private com.froxynetwork.froxynetwork.network.output.data.PlayerDataOutput restPlayer;

	public Player(com.froxynetwork.froxynetwork.network.output.data.PlayerDataOutput restPlayer) {
		this.restPlayer = restPlayer;
		this.uuid = UUID.fromString(restPlayer.getData().getUuid());
		this.nickname = restPlayer.getData().getNickname();
		this.displayName = restPlayer.getData().getDisplayName();
		this.coins = restPlayer.getData().getCoins();
		this.level = restPlayer.getData().getLevel();
		this.exp = restPlayer.getData().getExp();
		this.firstLogin = restPlayer.getData().getFirstLogin();
		this.lastLogin = restPlayer.getData().getLastLogin();
		String serverId = restPlayer.getData().getServer() == null ? null : restPlayer.getData().getServer().getId();
		server = Main.get().getServerManager().getServer(serverId);
	}
}
