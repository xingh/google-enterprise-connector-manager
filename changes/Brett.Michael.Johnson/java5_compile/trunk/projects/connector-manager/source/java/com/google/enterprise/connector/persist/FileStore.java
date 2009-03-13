// Copyright 2008-2009 Google Inc.  All Rights Reserved.
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
package com.google.enterprise.connector.persist;

import com.google.enterprise.connector.common.PropertiesUtils;
import com.google.enterprise.connector.common.PropertiesException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manage persistence for schedule and state and configuration
 * for a named connector. The persistent store for these data items
 * are files in the connector's work directory.
 */
public class FileStore implements ConnectorScheduleStore,
    ConnectorStateStore, ConnectorConfigStore {

  private static final Logger LOGGER =
      Logger.getLogger(FileStore.class.getName());

  private Hashtable<String, String> cacheMap = new Hashtable<String, String>();
  private static final String schedName = "_schedule.txt";
  private static final String stateName = "_state.txt";
  private static final String configName = ".properties";

  /**
   * Retrieves connector schedule.
   *
   * @param context a StoreContext
   * @return connectorSchedule schedule of the corresponding connector.
   */
  public String getConnectorSchedule(StoreContext context) {
    testStoreContext(context);
    String key = context.getConnectorName() + schedName;
    if (cacheMap.containsKey(key)) {
      return (String) cacheMap.get(key);
    }
    String schedule = readStoreFile(context, schedName);
    if (schedule != null) {
      cacheMap.put(key, schedule);
    }
    return schedule;
  }

  /**
   * Stores connector schedule.
   *
   * @param context a StoreContext
   * @param connectorSchedule schedule of the corresponding connector.
   */
  public void storeConnectorSchedule(StoreContext context,
                                     String connectorSchedule) {
    if (connectorSchedule == null) {
      // We can't write null state to file, so just remove it.
      removeConnectorSchedule(context);
      return;
    }
    testStoreContext(context);
    cacheMap.put(context.getConnectorName() + schedName, connectorSchedule);
    writeStoreFile(context, schedName, connectorSchedule);
  }

  /**
   * Remove a connector schedule.  If no such connector exists, do nothing.
   *
   * @param context a StoreContext
   */
  public void removeConnectorSchedule(StoreContext context) {
    testStoreContext(context);
    deleteStoreFile(context, schedName);
    cacheMap.remove(context.getConnectorName() + schedName);
  }

  /**
   * Gets the stored state of a named connector.
   *
   * @param context a StoreContext
   * @return the state, or null if no state has been stored for this connector.
   */
  public String getConnectorState(StoreContext context) {
    testStoreContext(context);
    String key = context.getConnectorName() + stateName;
    if (cacheMap.containsKey(key)) {
      return (String) cacheMap.get(key);
    }
    String state = readStoreFile(context, stateName);
    if (state != null) {
      cacheMap.put(key, state);
    }
    return state;
  }

  /**
   * Stores connector state.
   *
   * @param context a StoreContext
   * @param connectorState state of the corresponding connector
   */
  public void storeConnectorState(StoreContext context, String connectorState) {
    if (connectorState == null) {
      // We can't write null state to file, so just remove it.
      removeConnectorState(context);
      return;
    }
    testStoreContext(context);
    cacheMap.put(context.getConnectorName() + stateName, connectorState);
    writeStoreFile(context, stateName, connectorState);
  }

  /**
   * Remove connector state.  If no such connector exists, do nothing.
   *
   * @param context a StoreContext
   */
  public void removeConnectorState(StoreContext context) {
    testStoreContext(context);
    deleteStoreFile(context, stateName);
    cacheMap.remove(context.getConnectorName() + stateName);
  }


  /**
   * Gets the stored configuration of a named connector.
   *
   * @param context a StoreContext
   * @return the configuration Properties, or null if no configuration
   *         has been stored for this connector.
   */
  public Properties getConnectorConfiguration(StoreContext context) {
    testStoreContext(context);
    Properties props = null;
    File propFile = getStoreFile(context, configName);
    if (propFile.exists()) {
      try {
        props = PropertiesUtils.loadFromFile(propFile);
      } catch (PropertiesException e) {
        LOGGER.log(Level.WARNING, "Failed to read connector configuration for "
                   + context.getConnectorName(), e);
      }
    }
    return props;
  }

  /**
   * Stores the configuration of a named connector.
   *
   * @param context a StoreContext
   * @param configuration Properties to store
   */
  public void storeConnectorConfiguration(StoreContext context,
      Properties configuration) {
    if (configuration == null) {
      removeConnectorConfiguration(context);
      return;
    }
    testStoreContext(context);
    File propFile = getStoreFile(context, configName);
    String header = "Configuration for Connector " + context.getConnectorName();
    try {
      PropertiesUtils.storeToFile(configuration, propFile, header);
    } catch (PropertiesException e) {
      LOGGER.log(Level.WARNING, "Failed to store connector configuration for "
                 + context.getConnectorName(), e);
    }
  }

  /**
   * Remove a stored connector configuration.  If no such connector exists,
   * do nothing.
   *
   * @param context a StoreContext
   */
  public void removeConnectorConfiguration(StoreContext context) {
    testStoreContext(context);
    deleteStoreFile(context, configName);
  }

  /**
   * Test the StoreContext to make sure it is sane.
   *
   * @param context a StoreContext
   */
  private static void testStoreContext(StoreContext context) {
    if (context == null) {
      throw new IllegalArgumentException("StoreContext may not be null.");
    }
    String connectorName = context.getConnectorName();
    if (connectorName == null || connectorName.length() < 1) {
      throw new IllegalArgumentException(
          "StoreContext.connectorName may not be null or empty.");
    }
    File connectorDir = context.getConnectorDir();
    if (connectorDir == null) {
      throw new IllegalArgumentException(
          "StoreContext.connectorDir may not be null.");
    }
    if (!connectorDir.exists() || !connectorDir.isDirectory()) {
      throw new IllegalArgumentException(
          "StoreContext.connectorDir directory must exist.");
    }
  }

  /**
   * Return a File object representing the on-disk store.
   *
   * @param context a StoreContext
   * @param suffix String to append to file name
   */
  private static File getStoreFile(StoreContext context, String suffix) {
    return new File(context.getConnectorDir(),
                    context.getConnectorName() + suffix);
  }

  /**
   * Delete a store file.
   *
   * @param context a StoreContext
   * @param suffix String to append to file name
   */
  private static void deleteStoreFile(StoreContext context, String suffix) {
    getStoreFile(context, suffix).delete();
  }

  /**
   * Write the data to a store file.
   *
   * @param context a StoreContext
   * @param data to write to file
   */
  private static void writeStoreFile(StoreContext context, String suffix,
      String data) {
    FileOutputStream fos = null;
    File storeFile = null;
    try {
      storeFile = getStoreFile(context, suffix);
      fos = new FileOutputStream(storeFile);
      fos.write(data.getBytes());
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Cannot write store file "
          + storeFile + " for connector " + context.getConnectorName(), e);
    } finally {
      if (fos != null) {
        try {
          fos.close();
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Error closing store file "
              + storeFile + " for connector " + context.getConnectorName(), e);
        }
      }
    }
  }

  /**
   * Read a store file, returning a String containing the contents.
   *
   * @param context a StoreContext
   * @param suffix String to append to file name
   * @return String containing store file contents or null if none exists.
   */
  private static String readStoreFile(StoreContext context, String suffix) {
    FileInputStream fis = null;
    File storeFile = null;
    try {
      storeFile = getStoreFile(context, suffix);
      int length = (int) storeFile.length();
      if (length == 0) {
        return (storeFile.exists() ? "" : null);
      }
      byte[] buffer = new byte[length];
      fis = new FileInputStream(storeFile);
      int bytesRead = fis.read(buffer);
      return new String(buffer, 0, bytesRead);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Cannot read store file "
          + storeFile + " for connector " + context.getConnectorName(), e);
      return null;
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e1) {
          LOGGER.log(Level.WARNING, "Error closing store file "
              + storeFile + " for connector " + context.getConnectorName(), e1);
        }
      }
    }
  }
}
