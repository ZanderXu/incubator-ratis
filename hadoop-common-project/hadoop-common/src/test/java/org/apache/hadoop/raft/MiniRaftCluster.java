/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.raft;

import org.apache.hadoop.raft.client.RaftClient;
import org.apache.hadoop.raft.protocol.RaftClientReply;
import org.apache.hadoop.raft.protocol.RaftClientRequest;
import org.apache.hadoop.raft.server.RaftConfiguration;
import org.apache.hadoop.raft.server.RaftServer;
import org.apache.hadoop.raft.server.protocol.RaftPeer;
import org.apache.hadoop.raft.server.protocol.RaftServerReply;
import org.apache.hadoop.raft.server.protocol.RaftServerRequest;
import org.apache.hadoop.raft.server.simulation.SimulatedRpc;
import org.junit.Assert;

import java.io.PrintStream;
import java.util.*;

public class MiniRaftCluster {
  private final RaftConfiguration conf;
  private final SimulatedRpc<RaftServerRequest, RaftServerReply> serverRpc;
  private final SimulatedRpc<RaftClientRequest, RaftClientReply> client2serverRpc;
  private final Map<String, RaftServer> servers = new LinkedHashMap<>();

  MiniRaftCluster(int numServers) {
    this.conf = initConfiguration(numServers);
    serverRpc = new SimulatedRpc<>(conf.getPeers());
    client2serverRpc = new SimulatedRpc<>(conf.getPeers());

    for (RaftPeer p : conf.getPeers()) {
      servers.put(p.getId(), new RaftServer(p.getId(), conf, serverRpc,
          client2serverRpc));
    }
  }

  public void start() {
    for (RaftServer server : servers.values()) {
      server.start();
    }
  }

  private static RaftConfiguration initConfiguration(int num) {
    RaftPeer[] peers = new RaftPeer[num];
    for (int i = 0; i < num; i++) {
      peers[i] = new RaftPeer("s" + i);
    }
    return new RaftConfiguration(peers);
  }

  void killServer(String id) {
    servers.get(id).kill();
  }

  void printServers(PrintStream out) {
    out.println("#servers = " + servers.size());
    for (RaftServer s : servers.values()) {
      out.print("  ");
      out.println(s);
    }
  }

  void printAllLogs(PrintStream out) {
    out.println("#servers = " + servers.size());
    for(RaftServer s : servers.values()) {
      out.print("  ");
      out.println(s);
      out.print("    ");
      s.getState().getLog().printEntries(out);
    }
  }

  RaftServer getLeader() {
    final List<RaftServer> leaders = new ArrayList<>();
    for(RaftServer s : servers.values()) {
      if (s.isRunning() && s.isLeader()) {
        leaders.add(s);
      }
    }
    if (leaders.isEmpty()) {
      return null;
    } else {
      Assert.assertEquals(1, leaders.size());
      return leaders.get(0);
    }
  }

  List<RaftServer> getFollowers() {
    final List<RaftServer> followers = new ArrayList<>();
    for (RaftServer s : servers.values()) {
      if (s.isRunning() && s.isFollower()) {
        followers.add(s);
      }
    }
    return followers;
  }

  Collection<RaftServer> getServers() {
    return servers.values();
  }

  RaftClient createClient(String clientId, String leaderId) {
    return new RaftClient(clientId, conf.getPeers(), client2serverRpc, leaderId);
  }
}
