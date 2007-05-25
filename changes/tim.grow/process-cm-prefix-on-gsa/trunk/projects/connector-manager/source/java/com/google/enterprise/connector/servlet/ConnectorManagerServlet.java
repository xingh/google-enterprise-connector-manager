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

import com.google.enterprise.connector.common.StringUtils;
import com.google.enterprise.connector.manager.Context;
import com.google.enterprise.connector.manager.Manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * An abstract class for Connector Manager servlets.
 * It contains an abstract method "processDoPost".
 *
 */
public abstract class ConnectorManagerServlet extends HttpServlet {
  private static final Logger LOGGER =
      Logger.getLogger(ConnectorManagerServlet.class.getName());

  /**
   * This abstract method processes XML servlet-specific request body,
   * make servlet-specific call to the connector manager and write the
   * XML response body.
   * 
   * @param xmlBody String the servlet-specific request body string in XML
   * @param manager Manager
   * @param out PrintWriter where the XML response body is written
   */
  protected abstract void processDoPost(
      String xmlBody, Manager manager, PrintWriter out);

  /**
   * Returns an XML response to the HTTP GET request.
   * 
   * @param req
   * @param res
   * @throws ServletException
   * @throws IOException
   *
   */
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    doPost(req, res);
  }

  /**
   * Returns an XML response including full status (ConnectorMessageCode) to
   * the HTTP POST request.
   * @param req
   * @param res
   * @throws ServletException
   * @throws IOException
   *
   */
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    for (java.util.Enumeration headerNames = req.getHeaderNames(); headerNames.hasMoreElements(); ) {
      String name = (String) headerNames.nextElement();
      LOGGER.log(Level.WARNING, "HEADER " + name + ": " + req.getHeader(name));
    }
    BufferedReader reader = req.getReader();
//     BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(req.getInputStream(), "UTF-8"));
    res.setContentType(ServletUtil.MIMETYPE_XML);
    res.setCharacterEncoding("UTF-8");
    PrintWriter out = res.getWriter();
    String xmlBody = StringUtils.readAllToString(reader);
    if (xmlBody == null || xmlBody.length() < 1) {
      ServletUtil.writeResponse(
          out, ConnectorMessageCode.RESPONSE_EMPTY_REQUEST);
      LOGGER.log(Level.WARNING, ServletUtil.LOG_RESPONSE_EMPTY_REQUEST);
      out.close();
      return;
    }
    
    ServletContext servletContext = this.getServletContext();
    Manager manager = Context.getInstance(servletContext).getManager();
    processDoPost(xmlBody, manager, out);
    out.close();
  }

}
