/*
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
package org.apache.ambari.controller;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ambari.common.rest.agent.Action;
import org.apache.ambari.common.rest.agent.Action.Kind;
import org.apache.ambari.common.rest.agent.ActionResult;
import org.apache.ambari.common.rest.agent.AgentRoleState;
import org.apache.ambari.common.rest.agent.CommandResult;
import org.apache.ambari.common.rest.agent.ControllerResponse;
import org.apache.ambari.common.rest.agent.HeartBeat;
import org.apache.ambari.common.rest.entities.ClusterDefinition;
import org.apache.ambari.common.rest.entities.ClusterState;
import org.apache.ambari.common.rest.entities.Node;
import org.apache.ambari.common.rest.entities.NodeState;
import org.apache.ambari.components.ComponentPlugin;
import org.apache.ambari.controller.HeartbeatHandler.ClusterNameAndRev;
import org.apache.ambari.controller.HeartbeatHandler.SpecialServiceIDs;
import org.apache.ambari.event.AsyncDispatcher;
import org.apache.ambari.event.EventHandler;
import org.apache.ambari.resource.statemachine.ClusterFSM;
import org.apache.ambari.resource.statemachine.RoleEvent;
import org.apache.ambari.resource.statemachine.RoleEventType;
import org.apache.ambari.resource.statemachine.RoleFSM;
import org.apache.ambari.resource.statemachine.RoleState;
import org.apache.ambari.resource.statemachine.ServiceEvent;
import org.apache.ambari.resource.statemachine.ServiceEventType;
import org.apache.ambari.resource.statemachine.ServiceFSM;
import org.apache.ambari.resource.statemachine.ServiceState;
import org.apache.ambari.resource.statemachine.StateMachineInvoker;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestHeartbeat {
  
  ComponentPlugin plugin;
  String[] roles = {"abc"};
  String[] services = {"comp1"};
  ClusterDefinition cdef;
  Cluster cluster;
  Nodes nodes;
  Clusters clusters;
  HeartBeat heartbeat;
  Node node;
  final String script = "script-content";
  final int scriptHash = script.hashCode();
  
  private static ConcurrentHashMap<String, ClusterFSM> c;
  
  @BeforeMethod
  public void setup() throws Exception {
    plugin = mock(ComponentPlugin.class);
    when(plugin.getActiveRoles()).thenReturn(roles);
    cdef = mock(ClusterDefinition.class);
    when(cdef.getEnabledServices()).thenReturn(Arrays.asList("comp1"));
    cluster = mock(Cluster.class);
    when(cluster.getClusterDefinition(anyInt())).thenReturn(cdef);
    when(cluster.getName()).thenReturn("cluster1");
    when(cluster.getComponentDefinition("comp1")).thenReturn(plugin);
    when(cluster.getLatestRevisionNumber()).thenReturn(-1);
    Action startAction = new Action();
    startAction.setKind(Kind.START_ACTION);
    when(plugin.startServer("cluster1", "abc")).thenReturn(startAction);
    when(plugin.runCheckRole()).thenReturn("abc");
    when(plugin.runPreStartRole()).thenReturn("abc");
    Action preStartAction = new Action();
    preStartAction.setKind(Kind.RUN_ACTION);
    when(plugin.preStartAction("cluster1", "abc")).thenReturn(preStartAction);
    Action checkServiceAction = new Action();
    preStartAction.setKind(Kind.RUN_ACTION);
    when(plugin.checkService("cluster1", "abc")).thenReturn(checkServiceAction);
    nodes = mock(Nodes.class);
    clusters = mock(Clusters.class);
    node = new Node();
    node.setName("localhost");
    NodeState nodeState = new NodeState();
    nodeState.setClusterName("cluster1");
    node.setNodeState(nodeState);
    when(nodes.getNode("localhost")).thenReturn(node);
    when(nodes.getNodeRoles("localhost"))
         .thenReturn(Arrays.asList(roles));
    when(clusters.getClusterByName("cluster1")).thenReturn(cluster);
    when(clusters.getInstallAndConfigureScript(anyString(), anyInt()))
        .thenReturn(script);
    heartbeat = new HeartBeat();
    heartbeat.setIdle(true);
    heartbeat.setInstallScriptHash(-1);
    heartbeat.setHostname("localhost");
    heartbeat.setInstalledRoleStates(new ArrayList<AgentRoleState>());
    StateMachineInvoker.init(new AsyncDispatcher(), 
        (c=new ConcurrentHashMap<String, ClusterFSM>()));
  }
  
  @AfterTest
  public void teardown() {
    c.clear();
  }

  @Test
  public void testInstall() throws Exception {
    //send a heartbeat and get a response with install/config action
    HeartbeatHandler handler = new HeartbeatHandler(clusters, nodes);
    
    ControllerResponse response = handler.processHeartBeat(heartbeat);
    List<Action> actions = response.getActions();
    assert(actions.size() == 1);
    assert(actions.get(0).getKind() == Action.Kind.INSTALL_AND_CONFIG_ACTION);
  }
  
  
  @Test
  public void testStartServer() throws Exception {
    //send a heartbeat when some server needs to be started, 
    //and the heartbeat response should have the start action
    TestClusterImpl clusterImpl = new TestClusterImpl(services,roles);
    ((TestRoleImpl)clusterImpl.getServices()
        .get(0).getRoles().get(0)).setShouldStart(true);
    c.put("cluster1", clusterImpl);
    processHeartbeatAndGetResponse(true);
  }
  
  @Test
  public void testStopServer() throws Exception {
    //send a heartbeat when some server needs to be stopped, 
    //and the heartbeat response shouldn't have a start action
    //for the server
    TestClusterImpl clusterImpl = new TestClusterImpl(services,roles);
    ((TestRoleImpl)clusterImpl.getServices()
        .get(0).getRoles().get(0)).setShouldStart(false);
    c.put("cluster1", clusterImpl);
    processHeartbeatAndGetResponse(false);
  }
  
  @Test
  public void testIsRoleActive() throws Exception {
    //send a heartbeat with some role server start success, 
    //and then the role should be considered active
    TestClusterImpl clusterImpl = new TestClusterImpl(services,roles);
    c.put("cluster1", clusterImpl);
    RoleFSM roleFsm = clusterImpl.getServices()
        .get(0).getRoles().get(0);
    heartbeat.setInstallScriptHash(scriptHash);
    List<AgentRoleState> installedRoleStates = new ArrayList<AgentRoleState>();
    AgentRoleState roleState = new AgentRoleState();
    roleState.setRoleName(roles[0]);
    roleState.setClusterDefinitionRevision(-1);
    roleState.setClusterId("cluster1");
    roleState.setComponentName("comp1");
    installedRoleStates.add(roleState);
    heartbeat.setInstalledRoleStates(installedRoleStates);
    HeartbeatHandler handler = new HeartbeatHandler(clusters, nodes);
    ControllerResponse response = handler.processHeartBeat(heartbeat);
    checkActions(response, true);
    int i = 0;
    while (i++ < 10) {
      if (roleFsm.getRoleState() == RoleState.ACTIVE) {
        break;
      }
      Thread.sleep(1000);
    }
    assert(roleFsm.getRoleState() == RoleState.ACTIVE);
  }
  
  @Test
  public void testCreationOfPreStartAction() throws Exception {
    TestClusterImpl clusterImpl = new TestClusterImpl(services,roles);
    ServiceFSM serviceImpl = clusterImpl.getServices().get(0);
    ((TestRoleImpl)clusterImpl.getServices().get(0).getRoles().get(0)).setShouldStart(false);
    ((TestServiceImpl)serviceImpl).setServiceState(ServiceState.PRESTART);
    c.put("cluster1", clusterImpl);
    checkSpecialAction(ServiceState.PRESTART, ServiceEventType.START, 
        SpecialServiceIDs.SERVICE_PRESTART_CHECK_ID);
  }
  @Test
  public void testCreationOfCheckRoleAction() throws Exception {
    
    TestClusterImpl clusterImpl = new TestClusterImpl(services,roles);
    ServiceFSM serviceImpl = clusterImpl.getServices().get(0);
    ((TestServiceImpl)serviceImpl).setServiceState(ServiceState.STARTED);
    c.put("cluster1", clusterImpl);
    checkSpecialAction(ServiceState.STARTED, ServiceEventType.ROLE_START_SUCCESS, 
        SpecialServiceIDs.SERVICE_AVAILABILITY_CHECK_ID);
  }
  
  @Test
  public void testServiceAvailableEvent() throws Exception {
    TestClusterImpl clusterImpl = new TestClusterImpl(services,roles);
    c.put("cluster1", clusterImpl);
    heartbeat.setInstallScriptHash(scriptHash);
    ServiceFSM serviceImpl = clusterImpl.getServices().get(0);
    ((TestServiceImpl)serviceImpl).setServiceState(ServiceState.STARTED);
    ActionResult actionResult = new ActionResult();
    actionResult.setKind(Kind.RUN_ACTION);
    ClusterNameAndRev clusterNameAndRev = new ClusterNameAndRev("cluster1", -1);
    String checkActionId = HeartbeatHandler.getSpecialActionID(
        clusterNameAndRev, "comp1", "abc", 
        SpecialServiceIDs.SERVICE_AVAILABILITY_CHECK_ID);
    actionResult.setId(checkActionId);
    actionResult.setClusterId("cluster1");
    actionResult.setClusterDefinitionRevision(-1);
    CommandResult commandResult = new CommandResult(0,"","");
    actionResult.setCommandResult(commandResult);
    List<ActionResult> actionResults = new ArrayList<ActionResult>();
    actionResults.add(actionResult);
    heartbeat.setActionResults(actionResults);
    HeartbeatHandler handler = new HeartbeatHandler(clusters, nodes);
    handler.processHeartBeat(heartbeat);
    int i = 0;
    while (i++ < 10) {
      if (serviceImpl.getServiceState() == ServiceState.ACTIVE) {
        break;
      }
      Thread.sleep(1000);
    }
    assert(serviceImpl.getServiceState() == ServiceState.ACTIVE);
  }
  
  @Test
  public void testServiceReadyToStartEvent() throws Exception {
    TestClusterImpl clusterImpl = new TestClusterImpl(services,roles);
    c.put("cluster1", clusterImpl);
    heartbeat.setInstallScriptHash(scriptHash);
    ServiceFSM serviceImpl = clusterImpl.getServices().get(0);
    ((TestServiceImpl)serviceImpl).setServiceState(ServiceState.PRESTART);
    ActionResult actionResult = new ActionResult();
    actionResult.setKind(Kind.RUN_ACTION);
    ClusterNameAndRev clusterNameAndRev = new ClusterNameAndRev("cluster1", -1);
    String checkActionId = HeartbeatHandler.getSpecialActionID(
        clusterNameAndRev, "comp1", "abc", 
        SpecialServiceIDs.SERVICE_PRESTART_CHECK_ID);
    actionResult.setId(checkActionId);
    actionResult.setClusterId("cluster1");
    actionResult.setClusterDefinitionRevision(-1);
    CommandResult commandResult = new CommandResult(0,"","");
    actionResult.setCommandResult(commandResult);
    List<ActionResult> actionResults = new ArrayList<ActionResult>();
    actionResults.add(actionResult);
    heartbeat.setActionResults(actionResults);
    HeartbeatHandler handler = new HeartbeatHandler(clusters, nodes);
    handler.processHeartBeat(heartbeat);
    int i = 0;
    while (i++ < 10) {
      if (serviceImpl.getServiceState() == ServiceState.STARTING) {
        break;
      }
      Thread.sleep(1000);
    }
    assert(serviceImpl.getServiceState() == ServiceState.STARTING);
  }
  
  private void checkSpecialAction(ServiceState serviceState, 
      ServiceEventType serviceEventType, 
      SpecialServiceIDs serviceId) throws Exception {
    heartbeat.setInstallScriptHash(scriptHash);
    HeartbeatHandler handler = new HeartbeatHandler(clusters, nodes);
    ControllerResponse response = handler.processHeartBeat(heartbeat);
    checkActions(response, ServiceState.STARTED == serviceState);
    ClusterNameAndRev clusterNameAndRev = new ClusterNameAndRev("cluster1", -1);
    boolean found = false;
    String checkActionId = HeartbeatHandler.getSpecialActionID(
        clusterNameAndRev, "comp1", "abc", 
        serviceId);
    for (Action action : response.getActions()) {
      if (action.getKind() == Kind.RUN_ACTION && 
          action.getId().equals(checkActionId)) {
        found = true;
        break;
      }
    }
    assert(found != false);
  }
  
  private void processHeartbeatAndGetResponse(boolean shouldFindStart)
      throws Exception {
    heartbeat.setInstallScriptHash(scriptHash);
    HeartbeatHandler handler = new HeartbeatHandler(clusters, nodes);
    ControllerResponse response = handler.processHeartBeat(heartbeat);
    checkActions(response, shouldFindStart);
  }
  
  private void checkActions(ControllerResponse response, boolean shouldFindStart) {
    List<Action> actions = response.getActions();
    boolean foundStart = false;
    boolean foundInstall = false;
    for (Action a : actions) {
      if (a.getKind() == Action.Kind.START_ACTION) {
        foundStart = true;
      }
      if (a.getKind() == Action.Kind.INSTALL_AND_CONFIG_ACTION) {
        foundInstall = true;
      }
    }
    assert (foundInstall != false && foundStart == shouldFindStart);
  }

  
  class TestClusterImpl implements ClusterFSM {
    ClusterState clusterState;
    List<ServiceFSM> serviceFsms;
    public void setClusterState(ClusterState state) {
      this.clusterState = state;
    }
    public TestClusterImpl(String[] services, String roles[]) {
      serviceFsms = new ArrayList<ServiceFSM>();
      for (String service : services) {
        ServiceFSM srv = new TestServiceImpl(service,roles);
        serviceFsms.add(srv);
      }
    }
    @Override
    public List<ServiceFSM> getServices() {
      return serviceFsms;
    }

    @Override
    public Map<String, String> getServiceStates() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void terminate() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public ClusterState getClusterState() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void activate() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void deactivate() {
      // TODO Auto-generated method stub
      
    }
    
  }
  
  class TestServiceImpl implements ServiceFSM, EventHandler<ServiceEvent> {

    ServiceState serviceState;
    String serviceName;
    List<RoleFSM> roleFsms;
    public void setServiceState(ServiceState state) {
      this.serviceState = state;
    }

    public TestServiceImpl(String service, String[] roles) {
      roleFsms = new ArrayList<RoleFSM>();
      for (String role : roles) {
        TestRoleImpl r = new TestRoleImpl(role);
        roleFsms.add(r);
      }
      serviceName = service;
    }
    
    @Override
    public ServiceState getServiceState() {
      return serviceState;
    }

    @Override
    public String getServiceName() {
      return serviceName;
    }

    @Override
    public ClusterFSM getAssociatedCluster() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean isActive() {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public List<RoleFSM> getRoles() {
      return roleFsms;
    }

    @Override
    public void activate() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void deactivate() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void handle(ServiceEvent event) {
      if (event.getType() == ServiceEventType.AVAILABLE_CHECK_SUCCESS) {
        serviceState = ServiceState.ACTIVE;
      }
      if (event.getType() == ServiceEventType.PRESTART_SUCCESS) {
        serviceState = ServiceState.STARTING;
      }
    }
    
  }
  
  class TestRoleImpl implements RoleFSM, EventHandler<RoleEvent>  {
 
    RoleState roleState;
    String roleName;
    boolean shouldStart = true;
    public void setShouldStart(boolean shouldStart) {
      this.shouldStart = shouldStart;
    }
    public void setRoleState(RoleState roleState) {
      this.roleState = roleState;
    }
    
    public TestRoleImpl(String role) {
      this.roleName = role;
    }
    @Override
    public RoleState getRoleState() {
      return roleState;
    }

    @Override
    public String getRoleName() {
      return roleName;
    }

    @Override
    public ServiceFSM getAssociatedService() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean shouldStop() {
      return false;
    }

    @Override
    public boolean shouldStart() {
      return shouldStart;
    }

    @Override
    public void activate() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void deactivate() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void handle(RoleEvent event) {
      if (event.getType() == RoleEventType.START_SUCCESS) {
        roleState = RoleState.ACTIVE;
      }
    }
  }
}