/*
 * SweetHome3DOnlineRecorder.java 3 feb. 2025
 *
 * Sweet Home 3D, Copyright (c) 2025 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d;

import java.awt.Color;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.RootPaneContainer;

import com.eteks.sweethome3d.io.Base64;
import com.eteks.sweethome3d.io.HomeOnlineRecorder;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.SwingTools;
import com.eteks.sweethome3d.tools.OperatingSystem;

/**
 * Recorder which stores homes on a HTTP server where resources used by a saved home are separated from home data.
 * @author Emmanuel Puybaret
 */
public class SweetHome3DOnlineRecorder extends HomeOnlineRecorder {
  private String           loginURL;
  private String           sessionCookie;
  private UserPreferences  preferences;

  private JTextField       memberNameTextField;
  private JPasswordField   passwordTextField;
  private JLabel           errorLabel = new JLabel("");

  private String           onlineSessionId;
  private long             onlineSessionDate;

  private static long      SESSION_DURATION = 15 * 60 * 1000; // ms

  /**
   * Creates a recorder that will use the URLs in parameter to write, read, list and delete homes
   * on Online server.
   */
  public SweetHome3DOnlineRecorder(String writeHomeURL,
                                   String readHomeURL,
                                   String listHomesURL,
                                   String deleteHomeURL,
                                   String writeResourceURL,
                                   String readResourceURL,
                                   String loginURL,
                                   String sessionCookie,
                                   FurnitureCatalog onlineFurnitureCatalog,
                                   TexturesCatalog onlineTexturesCatalog,
                                   UserPreferences preferences) {
    super(writeHomeURL, readHomeURL, listHomesURL, deleteHomeURL, writeResourceURL, readResourceURL,
        onlineFurnitureCatalog, onlineTexturesCatalog);
    this.loginURL = loginURL;
    this.sessionCookie = sessionCookie;
    this.preferences = preferences;
  }

  /**
   * Returns <code>true</code> if session is valid.
   */
  public boolean checkOnlineSession() throws IOException {
    if (this.onlineSessionId == null
        || System.currentTimeMillis() - onlineSessionDate > SESSION_DURATION) {
      if (this.onlineSessionId == null) {
        this.memberNameTextField = new JTextField(15);
        this.passwordTextField = new JPasswordField(15);

        JPanel loginPanel = new JPanel(new GridBagLayout());
        int labelAlignment = OperatingSystem.isMacOSX()
            ? GridBagConstraints.LINE_END
            : GridBagConstraints.LINE_START;
        int standardGap = Math.round(5 * SwingTools.getResolutionScale());
        loginPanel.add(new JLabel(SwingTools.getLocalizedLabelText(this.preferences, SweetHome3DOnlineRecorder.class, "loginLabel.text")),
            new GridBagConstraints(0, 0, 1, 1, 0, 0,
                labelAlignment, GridBagConstraints.NONE, new Insets(0, 0, 0, standardGap), 0, 0));
        loginPanel.add(this.memberNameTextField, new GridBagConstraints(1, 0, 1, 1, 1, 0,
            GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        loginPanel.add(new JLabel(SwingTools.getLocalizedLabelText(this.preferences, SweetHome3DOnlineRecorder.class, "passwordLabel.text")),
            new GridBagConstraints(0, 1, 1, 1, 0, 0,
                labelAlignment, GridBagConstraints.NONE, new Insets(standardGap, 0, 0, standardGap), 0, 0));
        loginPanel.add(this.passwordTextField, new GridBagConstraints(1, 1, 1, 1, 1, 0,
            GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(standardGap, 0, 0, 0), 0, 0));
        loginPanel.add(this.errorLabel,new GridBagConstraints(0, 2, 2, 1, 1, 0,
            GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(standardGap, 0, 0, 0), 0, 0));
        JComponent parent = null;
        // Find a parent for login window
        for (Window window : Window.getWindows()) {
          if (window.isActive()) {
            Container container = window.getParent() != null ? window.getParent() : window;
            if (container instanceof RootPaneContainer) {
              parent = ((RootPaneContainer)container).getRootPane();
              break;
            }
          }
        }
        String dialogTitle = SwingTools.getLocalizedLabelText(this.preferences, SweetHome3DOnlineRecorder.class, "loginDialog.title");
        if (SwingTools.showConfirmDialog(parent, loginPanel, dialogTitle, this.memberNameTextField) != JOptionPane.OK_OPTION) {
          return false;
        }
      }

      // Post login to server
      String memberName = this.memberNameTextField.getText();
      String password = new String(this.passwordTextField.getPassword());
      String encodedPassword;
      try {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.update(password.getBytes());
        encodedPassword = Base64.encodeBytes(digest.digest());
      } catch (NoSuchAlgorithmException ex) {
        IOException ex2 = new IOException("Can't encode password");
        ex2.initCause(ex);
        throw ex2;
      }

      CookieHandler.setDefault(null);
      HttpURLConnection connection = (HttpURLConnection)new URL(this.loginURL).openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);
      // Handle manually possible redirections to retrieve cookie
      connection.setInstanceFollowRedirects(false);

      OutputStream out = connection.getOutputStream();
      out.write(("MemberName=" + URLEncoder.encode(memberName, "UTF-8")
          + "&md5pw=" + encodedPassword).getBytes("UTF-8"));
      out.flush();
      out.close();

      String cookie = null;
      if (connection.getResponseCode() == HttpURLConnection.HTTP_OK
          || connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
        cookie = connection.getHeaderField("Set-Cookie");
      }
      if (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
        String redirectedUrl = connection.getHeaderField("Location");
        connection.disconnect();
        connection = (HttpURLConnection)new URL(redirectedUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setUseCaches(false);
      }

      InputStream in = connection.getInputStream();
      byte [] data = new byte [1024];
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      for (int i = 0; (i = in.read(data)) != -1; ) {
        buffer.write(data, 0, i);
      }
      in.close();

      // Expect {"result":"success"} answer
      String answer = new String(buffer.toByteArray(), "UTF-8").trim();
      if (cookie != null
          && answer.startsWith("{")
          && answer.endsWith("}")
          && answer.substring(1, answer.length() - 2).replaceAll("\\s", "").contains("\"result\":\"success\"")) {
        int sessionIdIndex = cookie.indexOf(this.sessionCookie) + this.sessionCookie.length() + 1;
        this.onlineSessionId = cookie.substring(sessionIdIndex, cookie.indexOf(";", sessionIdIndex));
        this.onlineSessionDate = System.currentTimeMillis();
        // Set cookie handler for the application to ensure all Online requests have their session id set
        final Map<String, List<String>> cookieMap =
            Collections.singletonMap("Cookie", Arrays.asList(this.sessionCookie + "=" + onlineSessionId));
        CookieHandler.setDefault(new CookieHandler() {
            @Override
            public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
              return cookieMap;
            }

            @Override
            public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
            }
          });
      } else {
        this.errorLabel.setText(SwingTools.getLocalizedLabelText(this.preferences,
            SweetHome3DOnlineRecorder.class, "invalidLoginLabel.text"));
        this.errorLabel.setForeground(Color.RED);
        return checkOnlineSession();
      }
      connection.disconnect();
    }
    return true;
  }
}
