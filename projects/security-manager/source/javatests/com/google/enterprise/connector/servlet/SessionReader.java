// Copyright (C) 2008 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.enterprise.connector.common.cookie.CookieUtil;
import com.google.enterprise.connector.manager.ConnectorManager;
import com.google.enterprise.connector.manager.Context;
import com.google.enterprise.connector.saml.server.BackEndImpl;
import com.google.enterprise.connector.saml.server.GSASessionAdapter;
import com.google.enterprise.sessionmanager.SessionManagerInterface;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Reads and outputs any available SessionManager data generated by the
 * Security Manager.
 */
public class SessionReader extends HttpServlet {

  private static final long serialVersionUID = 1L;
  private static final Logger LOGGER = Logger.getLogger(SessionReader.class.getName());

  @Override
  protected void doGet(HttpServletRequest req,
                       HttpServletResponse res)
      throws IOException
  {
    Preconditions.checkNotNull(req);
    // TODO: implement a way to retrieving specific SID/key values via browser
    // param
    BackEndImpl backend =
        BackEndImpl.class.cast(
            ConnectorManager.class.cast(Context.getInstance(getServletContext()).getManager())
            .getBackEnd());
    SessionManagerInterface sm = backend.getSessionManager();

    StringBuilder msg = new StringBuilder("Message: <br>");
    for (String sid : backend.sessionIds) {
      if (sm.sessionExists(sid)) {
        msg.append(readSession(sm, sid));
      } else {
        backend.sessionIds.remove(sid);
        msg.append("<p>Session ID: " + sid + " expired. Removing...</p>");
      }
    }

    res.setContentType("text/html");
    PrintWriter out = res.getWriter();
    out.println("<HTML><HEAD><TITLE>Session Test</TITLE>"+
                "</HEAD><BODY>Session Test!<br>" + msg.toString() +
                "</BODY></HTML>");
    out.close();
  }

  private String readSession(SessionManagerInterface sm, String sessionId) {
    GSASessionAdapter adapter = new GSASessionAdapter(sm);
    String user = denullify(adapter.getUsername(sessionId));
    String domain = denullify(adapter.getDomain(sessionId));
    String password = denullify(adapter.getPassword(sessionId));
    String cookies = denullify(adapter.getCookies(sessionId));
    LOGGER.info("ReadSession user string: " + user);
    LOGGER.info("ReadSession domain string: " + domain);
    LOGGER.info("ReadSession password string: " + password);
    LOGGER.info("ReadSession cookies string: " + cookies);

    StringBuilder dCookies = new StringBuilder("");
    if ("null" != cookies && !(cookies.length() != 0)) {
      for (Cookie c : CookieUtil.deserializeCookies(cookies)) {
        dCookies.append(c.getName() + ":" + c.getValue() + "<br>");
      }
    }

    return "<p>Session ID: " + sessionId + "<br>" +
        "User: " + user + "<br>" +
        "Domain: " + domain + "<br>" +
        "Password: " + password + "<br>" +
        "Cookies: " + cookies + "<br>" +
        "Deserialized Cookies: " + dCookies.toString() + "</p>";
  }

  private String denullify(String s) {
    return (null == s) ? "null" : s;
  }

  /**
   * Returns servlet info.
   * @return informational message
   *
   */
  @Override
  public String getServletInfo()
  {
    return "Session Test";
  }
}
