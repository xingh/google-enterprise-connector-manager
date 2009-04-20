// Copyright (C) 2008, 2009 Google Inc.
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

package com.google.enterprise.saml.server;

import com.google.enterprise.common.GettableHttpServlet;
import com.google.enterprise.common.PostableHttpServlet;
import com.google.enterprise.common.SessionAttribute;
import com.google.enterprise.connector.spi.AuthenticationResponse;
import com.google.enterprise.saml.common.SecurityManagerServlet;
import com.google.enterprise.security.identity.CredentialsGroup;
import com.google.enterprise.security.ui.OmniForm;
import com.google.enterprise.security.ui.OmniFormHtml;

import org.opensaml.common.binding.SAMLMessageContext;
import org.opensaml.saml2.binding.decoding.HTTPRedirectDeflateDecoder;
import org.opensaml.saml2.binding.encoding.HTTPArtifactEncoder;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AudienceRestriction;
import org.opensaml.saml2.core.AuthnContext;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.Conditions;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.ws.transport.http.HttpServletRequestAdapter;
import org.opensaml.ws.transport.http.HttpServletResponseAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.google.enterprise.saml.common.OpenSamlUtil.initializeLocalEntity;
import static com.google.enterprise.saml.common.OpenSamlUtil.initializePeerEntity;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeAssertion;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeAttribute;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeAttributeStatement;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeAttributeValue;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeAudience;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeAudienceRestriction;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeAuthnStatement;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeConditions;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeIssuer;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeResponse;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeStatus;
import static com.google.enterprise.saml.common.OpenSamlUtil.makeSubject;
import static com.google.enterprise.saml.common.OpenSamlUtil.runDecoder;
import static com.google.enterprise.saml.common.OpenSamlUtil.runEncoder;

import static org.opensaml.common.xml.SAMLConstants.SAML20P_NS;
import static org.opensaml.common.xml.SAMLConstants.SAML2_ARTIFACT_BINDING_URI;

/**
 * Handler for SAML authentication requests.  These requests are sent by a service provider, in our
 * case the Google Search Appliance.  This is one part of the security manager's identity provider.
 */
public class SamlAuthn extends SecurityManagerServlet
    implements GettableHttpServlet, PostableHttpServlet {
  private static final Logger LOGGER = Logger.getLogger(SamlAuthn.class.getName());
  /** Required for serializable classes. */
  private static final long serialVersionUID = 1L;
  private static final int defaultMaxPrompts = 3;
  private static final SessionAttribute<Integer> PROMPT_COUNTER_ATTR =
      SessionAttribute.getNamed("promptCounter");
  private static final SessionAttribute<OmniForm> OMNI_FORM_ATTR =
      SessionAttribute.getNamed("omniForm");
  private static final SessionAttribute<List<CredentialsGroup>> CREDENTIALS_GROUPS_ATTR =
      SessionAttribute.getNamed("credentialsGroups");

  private int maxPrompts;

  public SamlAuthn() {
    maxPrompts = defaultMaxPrompts;
  }

  public void setMaxPrompts(int maxPrompts) {
    this.maxPrompts = maxPrompts;
  }

  /**
   * Accept an authentication request and (eventually) respond to the service provider with a
   * response.  The request is generated by the service provider, then sent to the user agent as a
   * redirect.  The user agent redirects here, with the SAML AuthnRequest message encoded as a query
   * parameter.
   *
   * It's our job to authenticate the user behind the agent.  At the moment we respond with a
   * trivial form that prompts for username and password, but soon this will be replaced by
   * something more sophisticated.  Once the user agent posts the credentials, we validate them and
   * send the response.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    BackEnd backend = getBackEnd();

    // Establish the SAML message context
    SAMLMessageContext<AuthnRequest, Response, NameID> context =
        newSamlMessageContext(request);
    {
      EntityDescriptor localEntity = getSmEntity();
      initializeLocalEntity(context, localEntity, localEntity.getIDPSSODescriptor(SAML20P_NS),
                            SingleSignOnService.DEFAULT_ELEMENT_NAME);
    }

    // Decode the request
    context.setInboundMessageTransport(new HttpServletRequestAdapter(request));
    runDecoder(new HTTPRedirectDeflateDecoder(), context);

    // Select entity for response
    {
      EntityDescriptor peerEntity = getEntity(context.getInboundMessageIssuer());
      initializePeerEntity(context, peerEntity, peerEntity.getSPSSODescriptor(SAML20P_NS),
                           AssertionConsumerService.DEFAULT_ELEMENT_NAME,
                           SAML2_ARTIFACT_BINDING_URI);
    }

    // If there are cookies we can decode, use them.
    if (tryCookies(request, response)) {
      return;
    }

    if (backend.isIdentityConfigured()) {
      maybePrompt(request, response);
    } else {
      makeUnsuccessfulResponse(request, response, "Security Manager not configured");
    }
  }

  // Try to find cookies that can be decoded into identities.
  private boolean tryCookies(HttpServletRequest request, HttpServletResponse response)
      throws ServletException {
    // TODO(cph): these cookies should be assigned to particular CredentialsGroups.
    //    If that's done, then we might still need to prompt for more information with the
    //    OmniForm, after having filled in the known identities.
    List<String> ids = new ArrayList<String>();
    for (AuthenticationResponse ar: getBackEnd().handleCookie(getAuthnContext(request))) {
      ids.add(ar.getData());
    }
    if (ids.isEmpty()) {
      return false;
    }
    makeSuccessfulResponse(request, response, ids);
    return true;
  }

  /**
   * Extract the username and password from the parameters, then ask the backend to validate them.
   * The backend returns the appropriate SAML Response message, which we then encode and return to
   * the service provider.  At the moment we only support the Artifact binding for the response.
   *
   * @param request The HTTP request message.
   * @param response The HTTP response message.
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    BackEnd backend = getBackEnd();
    OmniForm omniform = getOmniForm(request);
    List<CredentialsGroup> cgs = getCredentialsGroups(request);

    omniform.handleFormSubmit(request);
    List<String> ids = new ArrayList<String>();
    for (CredentialsGroup cg : cgs) {
      if (!cg.isVerified() && cg.isVerifiable()) {
        backend.authenticate(cg);
        if (cg.isVerified()) {
          ids.add(cg.getUsername());
        } else {
          LOGGER.info("Credentials group unfulfilled: " + cg.getName());
        }
      }
    }
    if (ids.isEmpty()) {
      maybePrompt(request, response);
      return;
    }

    // This sequence is done; reset for next.
    resetPromptCounter(request);

    // Update the Session Manager with the necessary info.
    backend.updateSessionManager(getSessionId(request), cgs);

    makeSuccessfulResponse(request, response, ids);
  }

  private void maybePrompt(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (shouldPrompt(request)) {
      PrintWriter writer = initNormalResponse(response);
      writer.print(getOmniForm(request).generateForm());
      writer.close();
    } else {
      makeUnsuccessfulResponse(request, response, "Incorrect username or password");
    }
  }

  private boolean shouldPrompt(HttpServletRequest request)
      throws ServletException {
    String sessionId = getSessionId(request);
    Integer value = PROMPT_COUNTER_ATTR.get(sessionId);
    int n = (value == null) ? 0 : value;
    if (n < maxPrompts) {
      PROMPT_COUNTER_ATTR.put(sessionId, Integer.valueOf(n + 1));
      return true;
    }
    resetPromptCounter(request);
    return false;
  }

  private void resetPromptCounter(HttpServletRequest request)
      throws ServletException {
    PROMPT_COUNTER_ATTR.remove(getSessionId(request));
  }

  private OmniForm getOmniForm(HttpServletRequest request)
      throws IOException, ServletException {
    String sessionId = getSessionId(request);
    OmniForm omniform = OMNI_FORM_ATTR.get(sessionId);
    if (null == omniform) {
      omniform = new OmniForm(getCredentialsGroups(request), new OmniFormHtml(getAction(request)));
      OMNI_FORM_ATTR.put(sessionId, omniform);
    }
    return omniform;
  }

  private static List<CredentialsGroup> getCredentialsGroups(HttpServletRequest request)
      throws IOException, ServletException {
    return getCredentialsGroups(getSessionId(request));
  }

  // Not private so can be used by unit tests.
  static List<CredentialsGroup> getCredentialsGroups(String sessionId)
      throws IOException {
    List<CredentialsGroup> groups = CREDENTIALS_GROUPS_ATTR.get(sessionId);
    if (null == groups) {
      groups = CredentialsGroup.newGroups(getBackEnd().getIdentityConfiguration());
      CREDENTIALS_GROUPS_ATTR.put(sessionId, groups);
    }
    return groups;
  }

  private String getAction(HttpServletRequest request) {
    String url = request.getRequestURL().toString();
    int q = url.indexOf("?");
    return (q < 0) ? url : url.substring(0, q);
  }

  // We have at least one verified identity.  The first identity is considered the primary.
  private void makeSuccessfulResponse(HttpServletRequest request, HttpServletResponse response,
                                      List<String> ids)
      throws ServletException {
    LOGGER.info("Verified IDs: " + idsToString(ids));
    SAMLMessageContext<AuthnRequest, Response, NameID> context =
        existingSamlMessageContext(request);

    // Generate <Assertion> with <AuthnStatement>
    Assertion assertion =
        makeAssertion(makeIssuer(getSmEntity().getEntityID()), makeSubject(ids.get(0)));
    assertion.getAuthnStatements().add(makeAuthnStatement(AuthnContext.IP_PASSWORD_AUTHN_CTX));

    // Generate <Conditions> with <AudienceRestriction>
    Conditions conditions = makeConditions();
    AudienceRestriction restriction = makeAudienceRestriction();
    restriction.getAudiences().add(makeAudience(context.getInboundMessageIssuer()));
    conditions.getAudienceRestrictions().add(restriction);
    assertion.setConditions(conditions);

    // Generate <Response>
    Response samlResponse =
        makeResponse(context.getInboundSAMLMessage(), makeStatus(StatusCode.SUCCESS_URI));
    if (ids.size() > 1) {
      Attribute attribute = makeAttribute("secondary-ids");
      for (String id: ids.subList(1, ids.size())) {
        attribute.getAttributeValues().add(makeAttributeValue(id));
      }
      AttributeStatement attrStatement = makeAttributeStatement();
      attrStatement.getAttributes().add(attribute);
      assertion.getAttributeStatements().add(attrStatement);
    }
    samlResponse.getAssertions().add(assertion);
    context.setOutboundSAMLMessage(samlResponse);
    doRedirect(request, response);
  }

  private String idsToString(List<String> ids) {
    StringBuffer buffer = new StringBuffer();
    for (String id: ids) {
      if (buffer.length() > 0) {
        buffer.append(", ");
      }
      buffer.append(id);
    }
    return buffer.toString();
  }

  private void makeUnsuccessfulResponse(HttpServletRequest request, HttpServletResponse response,
                                        String message)
      throws ServletException {
    LOGGER.log(Level.WARNING, message);
    SAMLMessageContext<AuthnRequest, Response, NameID> context =
        existingSamlMessageContext(request);
    context.setOutboundSAMLMessage(makeResponse(context.getInboundSAMLMessage(),
                                                makeStatus(StatusCode.AUTHN_FAILED_URI, message)));
    doRedirect(request, response);
  }

  private void doRedirect(HttpServletRequest request, HttpServletResponse response)
      throws ServletException {
    SAMLMessageContext<AuthnRequest, Response, NameID> context =
        existingSamlMessageContext(request);
    // Encode the response message
    initResponse(response);
    context.setOutboundMessageTransport(new HttpServletResponseAdapter(response, true));
    HTTPArtifactEncoder encoder = new HTTPArtifactEncoder(null, null, getBackEnd().getArtifactMap());
    encoder.setPostEncoding(false);
    runEncoder(encoder, context);
  }
}
