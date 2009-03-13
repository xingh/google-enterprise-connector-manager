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

package com.google.enterprise.connector.spi;

import java.util.Map;

/**
 * The response for most configuration methods, such as @link{ConnectorType}.getConfigForm()
 * and @link{ConnectorType}.validateConfig().
 *
 */
public class ConfigureResponse {

  private final String message;
  private final String formSnippet;
  private final Map<String, String> configData;

  /**
   * Simple constructor.
   *
   * @param message
   *          A message to be included to the user along with the form. This
   *          message may be null or empty - no distinction is made between
   *          those cases. The message should be plain text - may not contain
   *          script directives.
   * @param formSnippet
   *          A sequence of &lt;tr&gt; elements, each of which should contain
   *          two &lt;td&gt; fields, first is the description of configuration
   *          element, second is an HTML input field. The snippet may be null or
   *          empty. Again, there may be no script elements.
   */
  public ConfigureResponse(String message, String formSnippet) {
    super();
    this.message = message;
    this.formSnippet = formSnippet;
    this.configData = null;
  }

  /**
   * Complete constructor.
   *
   * @param message
   *          A message to be included to the user along with the form. This
   *          message may be null or empty - no distinction is made between
   *          those cases. The message should be plain text - may not contain
   *          script directives.
   * @param formSnippet
   *          A sequence of &lt;tr&gt; elements, each of which should contain
   *          two &lt;td&gt; fields, first is the description of configuration
   *          element, second is an HTML input field. The snippet may be null or
   *          empty. Again, there may be no script elements.
   * @param configData
   *          A {@link java.util.Map} of name, value pairs (String, String) of
   *          configuration data.  If supplied, where appropriate, the Connector
   *          Manager will use this data.
   */
  public ConfigureResponse(String message, String formSnippet,
                           Map<String, String> configData) {
    super();
    this.message = message;
    this.formSnippet = formSnippet;
    this.configData = configData;
  }

  /**
   * Gets the message
   *
   * @return the message - may be null or empty
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the form snippet.
   *
   * @return the form snippet - may be null or empty
   */
  public String getFormSnippet() {
    return formSnippet;
  }

  /**
   * Gets the config data.
   *
   * @return the config data - may be null or empty
   */
  public Map<String, String> getConfigData() {
    return configData;
  }
}
