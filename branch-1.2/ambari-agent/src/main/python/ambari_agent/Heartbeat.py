#!/usr/bin/env python2.6

'''
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
'''

import json
import logging
from Hardware import Hardware
from ActionQueue import ActionQueue
from ServerStatus import ServerStatus
import NetUtil
import AmbariConfig
import hostname
import time
import traceback
from pprint import pprint, pformat
from HostInfo import HostInfo

logger = logging.getLogger()

firstContact = True
class Heartbeat:

  def __init__(self, actionQueue):
    self.actionQueue = actionQueue
    self.reports = []

  def build(self, id='-1', state_interval=-1):
    global clusterId, clusterDefinitionRevision, firstContact
    timestamp = int(time.time()*1000)
    queueResult = self.actionQueue.result()

    
    nodeStatus = { "status" : "HEALTHY",
                   "cause" : "NONE"}
    
    heartbeat = { 'responseId'        : int(id),
                  'timestamp'         : timestamp,
                  'hostname'          : hostname.hostname(),
                  'nodeStatus'        : nodeStatus
                }
    if (int(id) >= 0) and state_interval > 0 and (int(id) % state_interval) == 0:
      hostInfo = HostInfo()
      nodeInfo = { }
      # for now, just do the same work as registration
      hostInfo.register(nodeInfo)
      heartbeat['agentEnv'] = nodeInfo

    if len(queueResult) != 0:
      heartbeat['reports'] = queueResult['reports']
      heartbeat['componentStatus'] = queueResult['componentStatus']
      pass
    logger.info("Heartbeat dump: " + pformat(heartbeat))
    return heartbeat

def main(argv=None):
  actionQueue = ActionQueue(AmbariConfig.config)
  heartbeat = Heartbeat(actionQueue)
  print json.dumps(heartbeat.build('3',3))

if __name__ == '__main__':
  main()