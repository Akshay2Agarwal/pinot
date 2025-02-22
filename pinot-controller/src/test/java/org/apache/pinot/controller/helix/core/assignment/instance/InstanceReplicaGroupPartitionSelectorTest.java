/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.controller.helix.core.assignment.instance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.text.StringSubstitutor;
import org.apache.helix.model.InstanceConfig;
import org.apache.helix.zookeeper.datamodel.ZNRecord;
import org.apache.pinot.common.assignment.InstancePartitions;
import org.apache.pinot.spi.config.table.assignment.InstanceReplicaGroupPartitionConfig;
import org.testng.annotations.Test;


public class InstanceReplicaGroupPartitionSelectorTest {

  private static final String INSTANCE_CONFIG_TEMPLATE =
      "{\n" + "  \"id\": \"Server_pinot-server-${serverName}.pinot-server-headless.pinot.svc.cluster.local_8098\",\n"
          + "  \"simpleFields\": {\n" + "    \"HELIX_ENABLED\": \"true\",\n"
          + "    \"HELIX_ENABLED_TIMESTAMP\": \"1688959934305\",\n"
          + "    \"HELIX_HOST\": \"pinot-server-${serverName}.pinot-server-headless.pinot.svc.cluster.local\",\n"
          + "    \"HELIX_PORT\": \"8098\",\n" + "    \"adminPort\": \"8097\",\n" + "    \"grpcPort\": \"8090\",\n"
          + "    \"queryMailboxPort\": \"46347\",\n" + "    \"queryServerPort\": \"45031\",\n"
          + "    \"shutdownInProgress\": \"false\"\n" + "  },\n" + "  \"mapFields\": {\n"
          + "    \"SYSTEM_RESOURCE_INFO\": {\n" + "      \"numCores\": \"16\",\n"
          + "      \"totalMemoryMB\": \"126976\",\n" + "      \"maxHeapSizeMB\": \"65536\"\n" + "    },\n"
          + "    \"pool\": {\n" + "      \"DefaultTenant_OFFLINE\": \"${pool}\",\n"
          + "      \"${poolName}\": \"${pool}\",\n" + "      \"AllReplicationGroups\": \"1\"\n" + "    }\n" + "  },\n"
          + "  \"listFields\": {\n" + "    \"TAG_LIST\": [\n" + "      \"DefaultTenant_OFFLINE\",\n"
          + "      \"DefaultTenant_REALTIME\",\n" + "      \"${poolName}\",\n" + "      \"AllReplicationGroups\"\n"
          + "    ]\n" + "  }\n" + "}";

  @Test
  public void testSelectInstances() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    String existingPartitionsJson =
        "    {\n" + "      \"instancePartitionsName\": \"0f97dac8-4123-47c6-9a4d-b8ce039c5ea5_OFFLINE\",\n"
            + "      \"partitionToInstancesMap\": {\n" + "        \"0_0\": [\n"
            + "          \"Server_pinot-server-rg0-0.pinot-server-headless.pinot.svc.cluster.local_8098\",\n"
            + "          \"Server_pinot-server-rg0-1.pinot-server-headless.pinot.svc.cluster.local_8098\"\n"
            + "        ]\n" + "      }\n" + "    }\n";
    InstancePartitions existing = objectMapper.readValue(existingPartitionsJson, InstancePartitions.class);
    InstanceReplicaGroupPartitionConfig config =
        new InstanceReplicaGroupPartitionConfig(true, 0, 2, 2, 1, 2, true, null);

    InstanceReplicaGroupPartitionSelector selector =
        new InstanceReplicaGroupPartitionSelector(config, "tableNameBlah", existing);

    String[] serverNames = {"rg0-0", "rg0-1", "rg1-0", "rg1-1"};
    String[] poolNumbers = {"0", "0", "1", "1"};
    String[] poolNames = {"FirstHalfReplicationGroups", "FirstHalfReplicationGroups", "SecondHalfReplicationGroups",
        "SecondHalfReplicationGroups"};
    Map<Integer, List<InstanceConfig>> poolToInstanceConfigsMap = new HashMap<>();

    for (int i = 0; i < serverNames.length; i++) {
      Map<String, String> valuesMap = new HashMap<>();
      valuesMap.put("serverName", serverNames[i]);
      valuesMap.put("pool", poolNumbers[i]);
      valuesMap.put("poolName", poolNames[i]);

      StringSubstitutor substitutor = new StringSubstitutor(valuesMap);
      String resolvedString = substitutor.replace(INSTANCE_CONFIG_TEMPLATE);

      ZNRecord znRecord = objectMapper.readValue(resolvedString, ZNRecord.class);
      int poolNumber = Integer.parseInt(poolNumbers[i]);
      poolToInstanceConfigsMap.computeIfAbsent(poolNumber, k -> new ArrayList<>()).add(new InstanceConfig(znRecord));
    }
    InstancePartitions assignedPartitions = new InstancePartitions("0f97dac8-4123-47c6-9a4d-b8ce039c5ea5_OFFLINE");
    selector.selectInstances(poolToInstanceConfigsMap, assignedPartitions);

    String expectedInstancePartitions =
        "    {\n" + "      \"instancePartitionsName\": \"0f97dac8-4123-47c6-9a4d-b8ce039c5ea5_OFFLINE\",\n"
            + "      \"partitionToInstancesMap\": {\n" + "        \"0_0\": [\n"
            + "          \"Server_pinot-server-rg0-0.pinot-server-headless.pinot.svc.cluster.local_8098\",\n"
            + "          \"Server_pinot-server-rg0-1.pinot-server-headless.pinot.svc.cluster.local_8098\"\n"
            + "        ],\n" + "        \"0_1\": [\n"
            + "          \"Server_pinot-server-rg1-0.pinot-server-headless.pinot.svc.cluster.local_8098\",\n"
            + "          \"Server_pinot-server-rg1-1.pinot-server-headless.pinot.svc.cluster.local_8098\"\n"
            + "        ]\n" + "      }\n" + "  }\n";
    InstancePartitions expectedPartitions =
        objectMapper.readValue(expectedInstancePartitions, InstancePartitions.class);
    assert assignedPartitions.equals(expectedPartitions);
  }
}
