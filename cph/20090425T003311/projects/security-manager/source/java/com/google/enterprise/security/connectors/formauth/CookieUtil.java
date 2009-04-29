// Copyright 2006 Google Inc.
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

package com.google.enterprise.security.connectors.formauth;

import com.google.enterprise.common.Base64;
import com.google.enterprise.common.Base64DecoderException;
import com.google.enterprise.common.HttpExchange;
import com.google.enterprise.common.ServletBase;
import com.google.enterprise.saml.common.GsaConstants;

import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;

/**
 * A class with some static methods to be used for Forms Authentication
 * feature.
 */
public final class CookieUtil {
  // RFC 2109, sect 10.1.2, says: Wdy, DD-Mon-YY HH:MM:SS GMT
  private static final DateFormat DATE_FORMAT_RFC2109 =
      new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss zzz");
  // ..and to be flexible we accept a variation.
  private static final DateFormat DATE_FORMAT_ALT =
      new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
  static {
    DATE_FORMAT_RFC2109.setTimeZone(TimeZone.getTimeZone("GMT"));
    DATE_FORMAT_ALT.setTimeZone(TimeZone.getTimeZone("GMT"));
  }
  private static final Logger LOG =
      Logger.getLogger(CookieUtil.class.getName());

  private CookieUtil() {
  }

  /** Fetch a page, using POST or GET, and do not follow redirect.
   *  @param exchange The HTTP exchange object
   *  @param url The URL to fetch
   *  @param userAgent The string to send in the user agent header
   *  @param userAgentCookies Cookies received from the user agent.
   *  @param receivedCookies Cookies received from the IdP; new cookies will be added here.
   *  @param bodyBuffer A StringBuffer to store response body. Ignored if null
   *  @param redirectBuffer A StringBuffer to store the redirect URL
   *                        if so exists; may be null
   *  @return The HTTP status code of the request.
   */
  public static int fetchPage(HttpExchange exchange,
                              URL url,
                              String userAgent,
                              Collection<Cookie> userAgentCookies,
                              Collection<Cookie> receivedCookies,
                              StringBuffer bodyBuffer,
                              StringBuffer redirectBuffer)
      throws IOException {

    if (bodyBuffer != null) {
      bodyBuffer.setLength(0);
    }

    if (redirectBuffer != null) {
      redirectBuffer.setLength(0);
    }

    // Boilerplate headers.
    exchange.setRequestHeader("Date", ServletBase.httpDateString());
    exchange.setRequestHeader(
        "Accept", "text/html, text/xhtml;q=0.9, text/plain;q=0.5, text/*;q=0.1");
    exchange.setRequestHeader("Accept-Charset", "us-ascii, iso-8859-1, utf-8");
    exchange.setRequestHeader("Accept-Encoding", "identity");
    exchange.setRequestHeader("Accept-Language", "en-us, en;q=0.9");

    if (!undefined(userAgent)) {
      exchange.setRequestHeader("User-Agent", userAgent);
    }

    // Set up cookies to be sent.
    Collection<Cookie> cookiesToSend = new ArrayList<Cookie>();
    cookiesToSend.addAll(receivedCookies);
    subtractCookieSets(userAgentCookies, receivedCookies, cookiesToSend);
    LOG.info("Cookies total/sent: " +
             (userAgentCookies.size() + receivedCookies.size()) +
             "/" + cookiesToSend.size());
    String cookieStr = filterCookieToSend(cookiesToSend, url);
    if (!undefined(cookieStr)) {
      exchange.setRequestHeader("Cookie", cookieStr);
    }

    int status = exchange.exchange();

    if (bodyBuffer != null) {
      bodyBuffer.append(exchange.getResponseEntityAsString());
    }
    if (redirectBuffer != null) {
      String redirect = getRedirectUrl(exchange, url);
      if (redirect != null)
        redirectBuffer.append(redirect);
    }

    List<SetCookie> newCookies = parseHttpResponseCookies(exchange);

    // Remove duplicate cookies in old collection.  This is O(n^2) for small n.
    Iterator<Cookie> iter2 = receivedCookies.iterator();
    while (iter2.hasNext()) {
      String name = iter2.next().getName();
      if (hasCookieNamed(name, newCookies)) {
        iter2.remove();
      }
    }

    // Get the current time so we can check for expired cookies.
    Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
    long curTimeGmt = cal.getTimeInMillis();

    // Remove any incoming cookies that have expired.
    Iterator<SetCookie> iter1 = newCookies.iterator();
    while (iter1.hasNext()) {
      SetCookie setCookie = iter1.next();
      String name = setCookie.getName();
      String expiresAt = setCookie.getExpires();
      LOG.info("name=" + name + " age=" + setCookie.getMaxAge() +
               " exp=" + expiresAt);

      // Ignore cookies with max-age=0.
      if (setCookie.getMaxAge() == 0) {
        // Max age is non-negative (RFC2109) and negative means
        // attribute not present, so we just check for 0.
        LOG.info("This cookie has expired based on max-age: " + name);
        iter1.remove();
        continue;
      }

      // Ignore expired cookies.
      if (expiresAt != null) {
        // Compute expiration date of cookie, if possible.
        // We try 2 styles of date formats.
        long expMillis = -1;
        try {
          expMillis = DATE_FORMAT_RFC2109.parse(expiresAt).getTime();
        } catch (ParseException e1) {
          // Parsing failed, try other format.
          try {
            expMillis = DATE_FORMAT_ALT.parse(expiresAt).getTime();
          } catch (ParseException e2) {
            LOG.log(Level.WARNING,
                    "Can't parse cookie date \"" + expiresAt + "\" for cookie " + name,
                    e2);
          }
        }

        if (expMillis >= 0 && expMillis <= curTimeGmt) {
          LOG.info("This cookie has expired based on time: " + name);
          iter1.remove();
          continue;
        }
      }

      // TODO what to do with cookies that have no "domain" attribute??

      // Cookie not expired, add it to set.
      LOG.info("Add new cookie: " + name);
      receivedCookies.add(setCookie);
    }

    exchange.close();
    return status;
  }

  /**
   * Get the URL to redirect to after executing an HTTP request if so exists
   *
   * @param exchange The HTTP exchange object.
   * @param url The base URL object.
   * @return The absolute URL from a <code>Refresh</code> or
   *         <code>Location</code> header. Return null if none exists
   */
  private static String getRedirectUrl(HttpExchange exchange, URL url) {
    int status = exchange.getStatusCode();
    if (status == 200) {
      return getRefreshUrl(exchange);
    }
    if (status >= 300 && status < 400) {
      return exchange.getResponseHeaderValue("Location");
    }
    return null;
  }

  /**
   * Get the relative URL string in Refresh header if exists.
   * @param exchange The HTTP exchange object.
   * @return The relative URL string of Refresh header or null
   *   if none exists
   */
  private static String getRefreshUrl(HttpExchange exchange) {
    String refresh = exchange.getResponseHeaderValue("Refresh");
    if (refresh != null) {
      int pos = refresh.indexOf(';');
      if (pos != -1) {
        // found a semicolon
        String timeToRefresh = refresh.substring(0, pos);
        if ("0".equals(timeToRefresh)) {
          // only follow this if its an immediate refresh (0 seconds)
          pos = refresh.indexOf('=');
          if (pos != -1 && (pos + 1) < refresh.length()) {
            return refresh.substring(pos+1);
          }
        }
      }
    }
    return null;
  }

  /**
   * Subtract one cookie collection from another and store the difference.
   *
   * @param minuend The cookies to start with.
   * @param subtrahend The cookies to subtract from the minuend.
   * @param difference The collection to store the difference in.
   */
  public static void subtractCookieSets(Collection<Cookie> minuend,
                                        Collection<Cookie> subtrahend,
                                        Collection<Cookie> difference) {
    for (Cookie c : minuend) {
      if (!hasCookieNamed(c.getName(), subtrahend)) {
        difference.add(c);
      }
    }
  }

  /**
   * Does the cookie collection contain a cookie with the given name?
   *
   * @param name The cookie name to search for.
   * @param cookies The cookie collection to search.
   * @return True iff the collection contains a cookie with that name.
   */
  public static boolean hasCookieNamed(String name, Collection<? extends Cookie> cookies) {
    for (Cookie c : cookies) {
      if (c.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  public static List<SetCookie> parseHttpResponseCookies(HttpExchange exchange) {
    List<SetCookie> cookies = new ArrayList<SetCookie>();
    for (String value: exchange.getResponseHeaderValues("Set-Cookie")) {
      cookies.addAll(SetCookieParser.parse(value));
    }
    for (String value: exchange.getResponseHeaderValues("Set-Cookie2")) {
      cookies.addAll(SetCookieParser.parse(value));
    }
    return cookies;
  }

  /**
   *  Build a String representation out of a Collection of Cookie that
   *  applies for a given URL.
   *  @param cookies Collection of Cookie object
   *  @param url The target URL.
   *  @return A String to be used as value of a "Cookie" header
   */
  private static String filterCookieToSend(Collection<Cookie> cookies, URL url) {
    if (cookies == null || cookies.size() == 0)
      return null;
    StringBuilder buffer = new StringBuilder();
    for (Cookie cookie: cookies) {
      if (isCookieGoodFor(cookie, url)) {
        if (buffer.length() > 0) {
          buffer.append("; ");
        }
        buffer.append(cookie.getName());
        buffer.append('=');
        buffer.append(cookie.getValue());
      }
    }
    return buffer.toString();
  }

  /** Check if a cookie is good for a given target URL.
   *  @param cookie The cookie to be filtered
   *  @param url The URL object to be tested against
   *  @return If the cookie is valid to be sent to the URL.
   */
  public static boolean isCookieGoodFor(Cookie cookie, URL url) {
    if (!undefined(cookie.getDomain()) &&
        !url.getHost().endsWith(cookie.getDomain())) {
      return false;
    }
    if (!undefined(cookie.getPath()) &&
        !url.getPath().startsWith(cookie.getPath())) {
      return false;
    }
    if (cookie.getSecure() && !"https".equals(url.getProtocol())) {
      return false;
    }
    return true;
  }

  public static String serializeCookie(Cookie cookie) {
    StringBuilder str = new StringBuilder(safeSerialize(cookie.getName()));
    str.append(GsaConstants.COOKIE_FIELD_SEPERATOR);
    str.append(safeSerialize(cookie.getValue()));
    str.append(GsaConstants.COOKIE_FIELD_SEPERATOR);
    str.append(safeSerialize(cookie.getPath()));
    str.append(GsaConstants.COOKIE_FIELD_SEPERATOR);
    str.append(safeSerialize(cookie.getDomain()));
    str.append(GsaConstants.COOKIE_FIELD_SEPERATOR);
    str.append(safeSerialize(String.valueOf(cookie.getMaxAge())));
    return str.toString();
  }

  public static String serializeCookies(Vector<Cookie> cookies) {
    StringBuilder str = new StringBuilder();
    for (Cookie cookie : cookies) {
      str.append(serializeCookie(cookie));
      str.append(GsaConstants.COOKIE_RECORD_SEPARATOR);
    }
    return str.toString();
  }

  private static String safeSerialize(String str) {
    if (str == null) str = "";
    return Base64.encodeWebSafe(str.getBytes(), false);
  }

  public static Cookie deserializeCookie(String str) {
    String[] elements = str.split(GsaConstants.COOKIE_FIELD_SEPERATOR);
    Cookie cookie = new Cookie(safeDeserialize(elements[0]),
                               safeDeserialize(elements[1]));
    cookie.setPath(safeDeserialize(elements[2]));
    cookie.setDomain(safeDeserialize(elements[3]));
    Integer maxAge = new Integer(safeDeserialize(elements[4]));
    cookie.setMaxAge(maxAge);
    return cookie;
  }

  public static Vector<Cookie> deserializeCookies(String str) {
    Vector<Cookie> cookies = new Vector<Cookie>();
    String[] elements = str.split(GsaConstants.COOKIE_RECORD_SEPARATOR);
    for (String element : elements) {
      cookies.add(deserializeCookie(element));
    }
    return cookies;
  }

  private static String safeDeserialize(String str) {
     byte[] bytes = str.getBytes();
     try {
       return new String(Base64.decodeWebSafe(bytes, 0, bytes.length));
     } catch (Base64DecoderException e) {
       // Should not happen if the cookie was serialized by us
       LOG.warning("Error while deserializing. Original string = <" + str + ">.");
       return "";
     }
   }

  private static boolean undefined(String str) {
    return str == null || str.isEmpty();
  }
}
