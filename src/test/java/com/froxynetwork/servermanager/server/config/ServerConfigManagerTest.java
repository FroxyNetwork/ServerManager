package com.froxynetwork.servermanager.server.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput;

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
@RunWith(MockitoJUnitRunner.class)
public class ServerConfigManagerTest {

	@Test
	public void testResponseTypes() {
		ServerConfigManager scm = new ServerConfigManager();

		ServerConfigDataOutput.ServersConfig response = new ServerConfigDataOutput.ServersConfig();
		ServerConfigDataOutput.ServerConfig[] types = new ServerConfigDataOutput.ServerConfig[2];

		// No variant
		types[0] = new ServerConfigDataOutput.ServerConfig("id1", new String[] { "aa1", "aa2" }, null, 1, 5);

		// 2 variants
		ServerConfigDataOutput.ServerConfig[] variant2 = new ServerConfigDataOutput.ServerConfig[2];
		variant2[0] = new ServerConfigDataOutput.ServerConfig("id2.variant1", new String[] { "bb3" }, null, 1, 5);
		variant2[1] = new ServerConfigDataOutput.ServerConfig("id2.variant2", null, null, 2, 4);
		types[1] = new ServerConfigDataOutput.ServerConfig("id2", new String[] { "bb1", "bb2" }, variant2, 10, 15);

		response.setTypes(types);
		// No VPS
		response.setVps(new ServerConfigDataOutput.VpsConfig[] {});

		scm.response(response);

		// Checks
		// Size of HashMap
		assertEquals(4, scm.getAll().size());

		// Get id1
		ServerConfig sc1 = scm.get("id1");
		assertNotNull(sc1);
		// Type
		assertEquals("id1", sc1.getType());
		// Min / Max
		assertEquals(1, sc1.getMin());
		assertEquals(5, sc1.getMax());
		// DB
		assertEquals(2, sc1.getDatabase().length);
		assertEquals("aa1", sc1.getDatabase()[0]);
		assertEquals("aa2", sc1.getDatabase()[1]);
		// Variants
		assertEquals(0, sc1.getChildrens().size());

		// Get id2
		ServerConfig sc2 = scm.get("id2");
		assertNotNull(sc2);
		// Type
		assertEquals("id2", sc2.getType());
		// Min / Max
		assertEquals(10, sc2.getMin());
		assertEquals(15, sc2.getMax());
		// DB
		assertEquals(2, sc2.getDatabase().length);
		assertEquals("bb1", sc2.getDatabase()[0]);
		assertEquals("bb2", sc2.getDatabase()[1]);
		// Variants
		assertEquals(2, sc2.getChildrens().size());

		// Get id2.variant1
		ServerConfig sc21 = scm.get("id2.variant1");
		assertNotNull(sc21);
		// Type
		assertEquals("id2.variant1", sc21.getType());
		// Min / Max
		assertEquals(1, sc21.getMin());
		assertEquals(5, sc21.getMax());
		// DB
		assertEquals(3, sc21.getDatabase().length);
		assertEquals("bb1", sc21.getDatabase()[0]);
		assertEquals("bb2", sc21.getDatabase()[1]);
		assertEquals("bb3", sc21.getDatabase()[2]);
		// Variants
		assertEquals(0, sc21.getChildrens().size());
		// Parent
		assertEquals(sc2, sc21.getParent());

		// Get id2.variant2
		ServerConfig sc22 = scm.get("id2.variant2");
		assertNotNull(sc22);
		// Type
		assertEquals("id2.variant2", sc22.getType());
		// Min / Max
		assertEquals(2, sc22.getMin());
		assertEquals(4, sc22.getMax());
		// DB
		assertEquals(2, sc22.getDatabase().length);
		assertEquals("bb1", sc22.getDatabase()[0]);
		assertEquals("bb2", sc22.getDatabase()[1]);
		// Variants
		assertEquals(0, sc22.getChildrens().size());
		// Parent
		assertEquals(sc2, sc22.getParent());

		assertNull(scm.get("id.not.exist"));
	}

	@Test
	public void testResponseVps() {
		ServerConfigManager scm = new ServerConfigManager();

		ServerConfigDataOutput.ServersConfig response = new ServerConfigDataOutput.ServersConfig();
		response.setTypes(new ServerConfigDataOutput.ServerConfig[0]);

		ServerConfigDataOutput.VpsConfig[] vps = new ServerConfigDataOutput.VpsConfig[2];

		// First vps
		vps[0] = new ServerConfigDataOutput.VpsConfig("VPS01", 10,
				new ServerConfigDataOutput.VpsConfigConfig[] {
						new ServerConfigDataOutput.VpsConfigConfig("game1", 1, 5),
						new ServerConfigDataOutput.VpsConfigConfig("game2", 1, 10) });

		// Second vps
		vps[1] = new ServerConfigDataOutput.VpsConfig("VPS02", 20,
				new ServerConfigDataOutput.VpsConfigConfig[] {
						new ServerConfigDataOutput.VpsConfigConfig("game1", 2, 10),
						new ServerConfigDataOutput.VpsConfigConfig("game2", 4, 20) });

		response.setVps(vps);
		scm.response(response);

		// Size
		assertEquals(2, scm.getAllVps().size());

		// First VPS
		ServerVps vps1 = scm.getVps("VPS01");
		assertNotNull(vps1);
		assertEquals("VPS01", vps1.getId());
		assertEquals(10, vps1.getMaxServers());
		assertEquals(1, vps1.getMin("game1"));
		assertEquals(5, vps1.getMax("game1"));
		assertEquals(1, vps1.getMin("game2"));
		assertEquals(10, vps1.getMax("game2"));

		// Second VPS
		ServerVps vps2 = scm.getVps("VPS02");
		assertNotNull(vps2);
		assertEquals("VPS02", vps2.getId());
		assertEquals(20, vps2.getMaxServers());
		assertEquals(2, vps2.getMin("game1"));
		assertEquals(10, vps2.getMax("game1"));
		assertEquals(4, vps2.getMin("game2"));
		assertEquals(20, vps2.getMax("game2"));
	}
}
