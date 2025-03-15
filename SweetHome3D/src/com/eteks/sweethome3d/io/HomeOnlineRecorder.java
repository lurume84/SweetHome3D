/*
 * HomeOnlineRecorder.java 3 feb. 2025
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
package com.eteks.sweethome3d.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xml.sax.SAXException;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeRecorder;
import com.eteks.sweethome3d.model.InterruptedRecorderException;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.TexturesCategory;

/**
 * Recorder that stores homes on a HTTP server where resources used by a saved home are separated from home data.
 * @author Emmanuel Puybaret
 */
public class HomeOnlineRecorder implements HomeRecorder {
  private final String     writeHomeURL;
  private final String     readHomeURL;
  private final String     listHomesURL;
  private final String     deleteHomeURL;
  private String           writeResourceURL;
  private String           readResourceURL;
  private Set<Content>     onlineContents;
  private long             availableHomesCacheTime;
  private String []        availableHomesCache;

  /**
   * Creates a recorder that will use the URLs in parameter to write, read, list and delete homes
   * on Online server.
   */
  public HomeOnlineRecorder(String writeHomeURL,
                            String readHomeURL,
                            String listHomesURL,
                            String deleteHomeURL,
                            String writeResourceURL,
                            String readResourceURL,
                            FurnitureCatalog onlineFurnitureCatalog,
                            TexturesCatalog onlineTexturesCatalog) {
    this(writeHomeURL, readHomeURL, listHomesURL, deleteHomeURL);
    this.writeResourceURL = writeResourceURL;
    this.readResourceURL = readResourceURL;

    this.onlineContents = new HashSet<Content>();
    for (FurnitureCategory category : onlineFurnitureCatalog.getCategories()) {
      for (CatalogPieceOfFurniture piece : category.getFurniture()) {
        if (piece.getIcon() != null) {
          this.onlineContents.add(piece.getIcon());
        }
        if (piece.getPlanIcon() != null) {
          this.onlineContents.add(piece.getPlanIcon());
        }
        if (piece.getModel() != null) {
          this.onlineContents.add(piece.getModel());
        }
      }
    }
    for (TexturesCategory category : onlineTexturesCatalog.getCategories()) {
      for (CatalogTexture texture : category.getTextures()) {
        if (texture.getImage() != null) {
          this.onlineContents.add(texture.getImage());
        }
      }
    }
  }

  protected HomeOnlineRecorder(String writeHomeURL,
                               String readHomeURL,
                               String listHomesURL,
                               String deleteHomeURL) {
    this.writeHomeURL = writeHomeURL;
    this.readHomeURL = readHomeURL;
    this.listHomesURL = listHomesURL;
    this.deleteHomeURL = deleteHomeURL;
  }

  /**
   * Posts home data to the server URL returned by <code>getHomeSaveURL</code>.
   * @throws RecorderException if a problem occurred while writing home.
   */
  public void writeHome(Home home, String name) throws RecorderException {
    HttpURLConnection connection = null;
    try {
      // Open a stream to server
      checkOnlineSession();

      connection = (HttpURLConnection)new URL(this.writeHomeURL).openConnection();
      connection.setRequestMethod("POST");
      String multiPartBoundary = "---------#@&$!d3emohteews!$&@#---------";
      connection.setRequestProperty("Content-Type", "multipart/form-data; charset=UTF-8; boundary=" + multiPartBoundary);
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);

      // Post home part
      OutputStream out = connection.getOutputStream();
      out.write(("--" + multiPartBoundary + "\r\n").getBytes("UTF-8"));
      // Remove online: prefix if present
      name = name.startsWith(ONLINE_HOME)
          ? name.substring(HomeRecorder.ONLINE_HOME.length())
          : name;
      out.write(("Content-Disposition: form-data; name=\"home\"; filename=\""
          + name.replace('\"', '\'') + "\"\r\n").getBytes("UTF-8"));
      out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));
      out.flush();
      HomeOutputStream homeOut = createHomeOutputStream(out);
      // Write home with HomeOuputStream
      homeOut.writeHome(home);
      homeOut.flush();

      // Post last boundary
      out.write(("\r\n--" + multiPartBoundary + "--\r\n").getBytes("UTF-8"));
      out.close();

      // Read response
      InputStream in = connection.getInputStream();
      int read = in.read();
      in.close();
      if (read != '1') {
        throw new RecorderException("Saving home " + name + " failed");
      }
      // Reset availableHomes to force a new request at next getAvailableHomes or exists call
      this.availableHomesCache = null;
    } catch (InterruptedIOException ex) {
      throw new InterruptedRecorderException("Save " + name + " interrupted");
    } catch (IOException ex) {
      throw new RecorderException("Can't save home " + name, ex);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Returns the filter output stream used to write a home in the output stream in parameter.
   */
  protected HomeOutputStream createHomeOutputStream(OutputStream out) throws IOException {
    return new HomeOnlineOutputStream(out, 9, new HomeXMLExporter(),
                                      this.writeResourceURL, this.readResourceURL,
                                      this.onlineContents) {
        @Override
        protected boolean checkOnlineSession() throws IOException {
          return HomeOnlineRecorder.this.checkOnlineSession();
        }
      };
  }

  /**
   * Returns <code>true</code> if Online session is valid.
   */
  public boolean checkOnlineSession() throws IOException {
    return true;
  }

  /**
   * Returns a home instance read from its file <code>name</code>.
   * @throws RecorderException if a problem occurred while reading home,
   *   or if file <code>name</code> doesn't exist.
   */
  public Home readHome(String name) throws RecorderException {
    URLConnection connection = null;
    HomeInputStream in = null;
    try {
      // Remove online: prefix if present
      name = name.startsWith(ONLINE_HOME)
          ? name.substring(HomeRecorder.ONLINE_HOME.length())
          : name;
      // Replace % sequence by %% except %s before formating readHomeURL with home name
      String readHomeURL = String.format(this.readHomeURL.replaceAll("(%[^s0-9])", "%$1"),
          URLEncoder.encode(name, "UTF-8"));
      // Open a home input stream to server
      checkOnlineSession();
      connection = new URL(readHomeURL).openConnection();
      connection.setRequestProperty("Content-Type", "charset=UTF-8");
      connection.setUseCaches(false);
      in = createHomeInputStream(connection.getInputStream());
      // Read home with HomeInputStream
      Home home = in.readHome();
      return home;
    } catch (InterruptedIOException ex) {
      throw new InterruptedRecorderException("Read " + name + " interrupted");
    } catch (IOException ex) {
      throw new RecorderException("Can't read home from " + name, ex);
    } catch (ClassNotFoundException ex) {
      throw new RecorderException("Missing classes to read home from " + name, ex);
    } finally {
      try {
        if (in != null) {
          in.close();
        }
      } catch (IOException ex) {
        throw new RecorderException("Can't close file " + name, ex);
      }
    }
  }

  /**
   * Returns the filter input stream used to read a home from the input stream in parameter.
   */
  protected HomeInputStream createHomeInputStream(InputStream in) throws IOException {
    return new DefaultHomeInputStream(in, ContentRecording.INCLUDE_ALL_CONTENT,
        new HomeXMLHandler() {
          @Override
          protected Content parseContent(String elementName, Map<String, String> attributes, String attributeName) throws SAXException {
            String contentFile = attributes.get(attributeName);
            if (contentFile != null
                && contentFile.contains("http://") && readResourceURL.startsWith("https://")) {
              attributes.put(attributeName, contentFile.replace("http://" ,"https://"));
            }
            return super.parseContent(elementName, attributes, attributeName);
          }
        },
        null, false);
  }

  /**
   * Returns <code>true</code> if the home <code>name</code> exists.
   */
  public boolean exists(String name) throws RecorderException {
    String [] availableHomes;
    if (this.availableHomesCache != null
        && this.availableHomesCacheTime + 100 > System.currentTimeMillis()) {
      // Return available homes list in cache if the cache is less than 100 ms old
      availableHomes = this.availableHomesCache;
    } else {
      availableHomes = getAvailableHomes();
    }
    for (String home : availableHomes) {
      if (home.equals(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the available homes on server.
   */
  public String [] getAvailableHomes() throws RecorderException {
    URLConnection connection = null;
    InputStream in = null;
    try {
      // Open a stream to server
      checkOnlineSession();
      connection = new URL(this.listHomesURL).openConnection();
      connection.setUseCaches(false);
      in = connection.getInputStream();
      String contentEncoding = connection.getContentEncoding();
      if (contentEncoding == null) {
        contentEncoding = "UTF-8";
      }
      Reader reader = new InputStreamReader(in, contentEncoding);
      StringWriter homes = new StringWriter();
      for (int c; (c = reader.read()) != -1; ) {
        homes.write(c);
      }
      String [] availableHomes = homes.toString().split("\n");
      if (availableHomes.length == 1 && availableHomes [0].length() == 0) {
        this.availableHomesCache = new String [0];
      } else {
        this.availableHomesCache = new String [availableHomes.length];
        for (int i = 0; i < availableHomes.length; i++) {
          // Add online: prefix
          this.availableHomesCache [i] = HomeRecorder.ONLINE_HOME + availableHomes [i];
        }
      }
      this.availableHomesCacheTime = System.currentTimeMillis();
      return this.availableHomesCache;
    } catch (IOException ex) {
      throw new RecorderException("Can't read homes from server", ex);
    } finally {
      try {
        if (in != null) {
          in.close();
        }
      } catch (IOException ex) {
        throw new RecorderException("Can't close connection", ex);
      }
    }
  }

  /**
   * Deletes on server a home from its file <code>name</code>.
   * @throws RecorderException if a problem occurred while deleting home,
   *   or if file <code>name</code> doesn't exist.
   */
  public void deleteHome(String name) throws RecorderException {
    if (!isHomeDeletionAvailable()) {
      throw new RecorderException("Deletion isn't available");
    }
    HttpURLConnection connection = null;
    try {
      // Remove online: prefix if present
      name = name.startsWith(ONLINE_HOME)
          ? name.substring(HomeRecorder.ONLINE_HOME.length())
          : name;
      // Replace % sequence by %% except %s before formating readHomeURL with home name
      String deletedHomeURL = String.format(this.deleteHomeURL.replaceAll("(%[^s0-9])", "%$1"),
          URLEncoder.encode(name, "UTF-8"));
      // Send request to server
      checkOnlineSession();
      connection = (HttpURLConnection)new URL(deletedHomeURL).openConnection();
      connection.setRequestProperty("Content-Type", "charset=UTF-8");
      connection.setUseCaches(false);
      // Read response
      InputStream in = connection.getInputStream();
      int read = in.read();
      in.close();
      if (read != '1') {
        throw new RecorderException("Deleting home " + name + " failed");
      }
      // Reset availableHomes to force a new request at next getAvailableHomes or exists call
      this.availableHomesCache = null;
    } catch (InterruptedIOException ex) {
      throw new InterruptedRecorderException("Delete " + name + " interrupted");
    } catch (IOException ex) {
      throw new RecorderException("Can't delete home " + name, ex);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Returns <code>true</code> if this recorder provides a service able to delete homes.
   */
  public boolean isHomeDeletionAvailable() {
    return this.deleteHomeURL != null;
  }
}
