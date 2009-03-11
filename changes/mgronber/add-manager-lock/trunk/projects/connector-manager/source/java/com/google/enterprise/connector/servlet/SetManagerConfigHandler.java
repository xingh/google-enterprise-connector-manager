// Copyright (C) 2006 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.servlet;

import com.google.enterprise.connector.manager.Manager;
import com.google.enterprise.connector.persist.PersistentStoreException;

import org.w3c.dom.Element;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler class for SetManagerConfig servlet class.
 *
 */
public class SetManagerConfigHandler {
  private static final Logger LOGGER =
    Logger.getLogger(SetManagerConfigHandler.class.getName());

  private ConnectorMessageCode status;
  private String feederGateHost;
  private int feederGatePort;

  /*
   * Reads from a request input XML string
   *
   * @param manager Manager
   * @param xmlBody String Input XML body string.
   */
  public SetManagerConfigHandler(Manager manager, String xmlBody) {
    this.status = new ConnectorMessageCode();
    Element root = ServletUtil.parseAndGetRootElement(
      xmlBody, ServletUtil.XMLTAG_MANAGER_CONFIG);
    if (root == null) {
      this.status = new ConnectorMessageCode(
          ConnectorMessageCode.ERROR_PARSING_XML_REQUEST);
      return;
    }

    // Bail if the manager is currently locked.
    if (manager.isLocked()) {
      this.status = new ConnectorMessageCode(
          ConnectorMessageCode.ATTEMPT_TO_CHANGE_LOCKED_CONNECTOR_MANAGER);
      return;
    }

    this.feederGateHost = ServletUtil.getFirstAttribute(
        root, ServletUtil.XMLTAG_FEEDERGATE,
            ServletUtil.XMLTAG_FEEDERGATE_HOST);
    this.feederGatePort = Integer.parseInt(ServletUtil.getFirstAttribute(
        root, ServletUtil.XMLTAG_FEEDERGATE,
            ServletUtil.XMLTAG_FEEDERGATE_PORT));
    try {
      manager.setConnectorManagerConfig(this.feederGateHost,
          this.feederGatePort);
    } catch (PersistentStoreException e) {
      this.status = new ConnectorMessageCode(
          ConnectorMessageCode.EXCEPTION_PERSISTENT_STORE);
      LOGGER.log(Level.WARNING, ServletUtil.LOG_EXCEPTION_PERSISTENT_STORE, e);
    }
  }

  public String getFeederGateHost() {
    return feederGateHost;
  }

  public int getFeederGatePort() {
    return feederGatePort;
  }

  public ConnectorMessageCode getStatus() {
    return status;
  }
}

