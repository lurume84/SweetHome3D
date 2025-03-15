/*
 * HomeOnlineOutputStream.java 3 févr. 2025
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.tools.ResourceURLContent;
import com.eteks.sweethome3d.tools.SimpleURLContent;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * An <code>OutputStream</code> filter that writes a home in an online stream
 * at .sh3x file format.
 * @author Emmanuel Puybaret
 */
public class HomeOnlineOutputStream extends HomeOutputStream {
  private static Map<Content, String> savedContentNames = Collections.synchronizedMap(new WeakHashMap<Content, String>());

  private int              compressionLevel;
  private HomeXMLExporter  homeXmlExporter;
  private String           readResourceURL;
  private String           writeResourceURL;
  private Set<Content>     onlineContents;

  public HomeOnlineOutputStream(OutputStream     out,
                                int              compressionLevel,
                                HomeXMLExporter  homeXmlExporter,
                                String           writeResourceURL,
                                String           readResourceURL,
                                Set<Content>     onlineContents) throws IOException {
    super(out);
    this.compressionLevel = compressionLevel;
    this.homeXmlExporter = homeXmlExporter;
    this.writeResourceURL = writeResourceURL;
    this.readResourceURL = readResourceURL;
    this.onlineContents = onlineContents;
  }

  /**
   * Writes home in a zipped stream followed by <code>Content</code> objects
   * it points to.
   */
  public void writeHome(Home home) throws IOException {
    // Create a zip output on out stream
    ZipOutputStream zipOut = new ZipOutputStream(this.out);
    zipOut.setLevel(this.compressionLevel);
    checkCurrentThreadIsntInterrupted();
    // Save resource content with a dummy output stream
    HomeOnlineContentObjectsTracker contentTracker = new HomeOnlineContentObjectsTracker(new OutputStream() {
        @Override
        public void write(int b) throws IOException {
          // Don't write anything
        }
      });
    contentTracker.writeObject(home);

    // Write home at XML format in the second entry named "Home.xml"
    zipOut.putNextEntry(new ZipEntry("Home.xml"));
    // Save home replacing Content objects if needed
    XMLWriter xmlWriter = new XMLWriter(zipOut);
    this.homeXmlExporter.setSavedContentNames(savedContentNames);
    this.homeXmlExporter.writeElement(xmlWriter, home);
    xmlWriter.flush();
    zipOut.closeEntry();
    zipOut.finish();
  }

  protected boolean checkOnlineSession() throws IOException {
    return true;
  }

  /**
   * A dummy <code>ObjectOutputStream</code> that keeps track of the <code>Content</code>
   * objects of a home and save them as resources.
   */
  private class HomeOnlineContentObjectsTracker extends ObjectOutputStream {
    public HomeOnlineContentObjectsTracker(OutputStream out) throws IOException {
      super(out);
      enableReplaceObject(true);
    }

    @Override
    protected Object replaceObject(Object obj) throws IOException {
      if (obj instanceof URLContent
          && !(((URLContent)obj).isJAREntry() && ((URLContent)obj).getURL().getFile().startsWith("http")
               || !((URLContent)obj).isJAREntry() && ((URLContent)obj).getURL().getProtocol().startsWith("http"))) {
        URLContent localContent = (URLContent)obj;
        // Check if duplicated content can be avoided
        ContentDigestManager contentDigestManager = ContentDigestManager.getInstance();
        for (Map.Entry<Content, String> contentEntry : savedContentNames.entrySet()) {
          if (contentDigestManager.equals(localContent, contentEntry.getKey())) {
            savedContentNames.put(localContent, contentEntry.getValue());
            return obj;
          }
        }
        // Check if saving content can be avoided if it exists in online catalogs
        if (onlineContents != null) {
          for (Content onlineContent : onlineContents) {
            if (contentDigestManager.equals(localContent, onlineContent)) {
              savedContentNames.put(localContent, ((URLContent)onlineContent).getURL().toString());
              return obj;
            }
          }
        }

        checkCurrentThreadIsntInterrupted();

        // TODO convert BMP to PNG ?
        InputStream in = null;
        boolean imageContent = false;
        try {
          in = localContent.openStream();
          ImageInputStream imageInputStream = ImageIO.createImageInputStream(in);
          imageContent = ImageIO.getImageReaders(imageInputStream).hasNext();
        } finally {
          if (in != null) {
            in.close();
          }
        }
        String resourceFileName = UUID.randomUUID() + (imageContent ? ".dat" : ".zip");
        String readResourceFileURL = String.format(readResourceURL, resourceFileName);

        // If content comes from a zipped content
        if (localContent.isJAREntry()) {
          String entryName = localContent.getJAREntryName();
          if (localContent instanceof HomeURLContent) {
            // May happen if content was copied from a local home file
            int slashIndex = entryName.indexOf('/');
            // If content comes from a directory of a home file
            if (slashIndex > 0) {
              // Retrieve entry name in zipped stream without the directory
              String subEntryName = entryName.substring(slashIndex);
              readResourceFileURL = "jar:" + readResourceFileURL + "!" + subEntryName;
            }
          } else if (localContent instanceof ResourceURLContent) {
            ResourceURLContent resourceUrlContent = (ResourceURLContent)localContent;
            if (resourceUrlContent.isMultiPartResource()) {
              // If content is a resource coming from a JAR file, retrieve its file name
              int lastSlashIndex = entryName.lastIndexOf('/');
              if (lastSlashIndex != -1) {
                // Consider content is a multi part resource only if it's in a subdirectory
                String subEntryName = entryName.substring(lastSlashIndex);
                readResourceFileURL = "jar:" + readResourceFileURL + "!" + subEntryName;
              }
            }
          } else if (!(localContent instanceof SimpleURLContent)) {
            // Retrieve entry name in zipped stream
            readResourceFileURL = "jar:" + readResourceFileURL + "!/" + entryName;
          }
        } else if (localContent instanceof ResourceURLContent) {
          ResourceURLContent resourceUrlContent = (ResourceURLContent)localContent;
          // If content is a resource coming from a directory (this should be the case
          // only when resource isn't in a JAR file during development), retrieve its file name
          if (resourceUrlContent.isMultiPartResource()) {
            try {
              String entryName = new File(resourceUrlContent.getURL().toURI()).getName();
              readResourceFileURL = "jar:" + readResourceFileURL + "!/" + entryName;
            } catch (URISyntaxException ex) {
              IOException ex2 = new IOException();
              ex2.initCause(ex);
              throw ex2;
            }
          }
        }

        HttpURLConnection connection = null;
        try {
          // Send content to server
          checkOnlineSession();
          connection = (HttpURLConnection)new URL(String.format(writeResourceURL,
              URLEncoder.encode(resourceFileName, "UTF-8"))).openConnection();
          connection.setRequestProperty("Content-Type", "application/octet-stream");
          connection.setRequestMethod("POST");
          connection.setDoOutput(true);
          connection.setDoInput(true);
          connection.setUseCaches(false);
          // Post resource data
          OutputStream out = connection.getOutputStream();
          if (imageContent) {
            // Copy content
            in = null;
            try {
              in = localContent.openStream();
              byte [] buffer = new byte [8192];
              int size;
              while ((size = in.read(buffer)) != -1) {
                out.write(buffer, 0, size);
              }
            } finally {
              if (in != null) {
                in.close();
              }
            }
          } else {
            ZipOutputStream zipOut = new ZipOutputStream(out);
            zipOut.setLevel(compressionLevel);
            if (localContent instanceof ResourceURLContent) {
              if (((ResourceURLContent)localContent).isMultiPartResource()) {
                writeResourceZipEntries(zipOut, null, (ResourceURLContent)localContent);
              } else {
                writeZipEntry(zipOut, "data", localContent);
                readResourceFileURL = "jar:" + readResourceFileURL + "!/data";
              }
            } else if (!(localContent instanceof SimpleURLContent)
                && ((URLContent)localContent).isJAREntry()) {
              // If content comes from a home stream
              if (localContent instanceof HomeURLContent) {
                if (((URLContent)localContent).getJAREntryName().indexOf('/') > 0) {
                  writeHomeZipEntries(zipOut, null, localContent);
                } else {
                  writeZipEntry(zipOut, "data", localContent);
                  readResourceFileURL = "jar:" + readResourceFileURL + "!/data";
                }
              } else {
                writeZipEntries(zipOut, null, localContent);
              }
            } else {
              writeZipEntry(zipOut, "data", localContent);
              readResourceFileURL = "jar:" + readResourceFileURL + "!/data";
            }
            zipOut.finish();
          }
          out.close();
          connection.getInputStream().read();
        } finally {
          if (connection != null) {
            connection.disconnect();
          }
        }

        savedContentNames.put((Content)obj, readResourceFileURL);
      }
      return obj;
    }
  }
}
