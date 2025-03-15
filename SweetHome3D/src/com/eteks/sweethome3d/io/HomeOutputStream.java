/*
 * HomeOutputStream.java 3 feb. 2025
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
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.tools.ResourceURLContent;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * An <code>OutputStream</code> filter that writes a home in a stream.
 * @author Emmanuel Puybaret
 */
public abstract class HomeOutputStream extends FilterOutputStream {
 /**
   * Creates a stream that will save a home.
   */
  protected HomeOutputStream(OutputStream out) throws IOException {
    super(out);
  }

  /**
   * Writes <code>home</code> in a stream.
   */
  public abstract void writeHome(Home home) throws IOException;

  /**
   * Throws an <code>InterruptedRecorderException</code> exception
   * if current thread is interrupted. The interrupted status of the current thread
   * is cleared when an exception is thrown.
   */
  static void checkCurrentThreadIsntInterrupted() throws InterruptedIOException {
    if (Thread.interrupted()) {
      throw new InterruptedIOException();
    }
  }

  /**
   * Writes in <code>zipOut</code> stream one or more entries matching the content
   * <code>urlContent</code> coming from a resource file.
   */
  protected void writeResourceZipEntries(ZipOutputStream zipOut,
                                         String entryNameOrDirectory,
                                         ResourceURLContent urlContent) throws IOException {
    if (urlContent.isMultiPartResource()) {
      if (urlContent.isJAREntry()) {
        URL zipUrl = urlContent.getJAREntryURL();
        String entryName = urlContent.getJAREntryName();
        int lastSlashIndex = entryName.lastIndexOf('/');
        if (lastSlashIndex != -1) {
          // Consider content is a multi part resource only if it's in a subdirectory
          String entryDirectory = entryName.substring(0, lastSlashIndex + 1);
          // Write in home stream each zipped stream entry that is stored in the same directory
          for (ContentDigestManager.ZipEntryData zipEntry : ContentDigestManager.getInstance().getZipURLEntries(urlContent)) {
            String zipEntryName = zipEntry.getName();
            if (zipEntryName.startsWith(entryDirectory)) {
              Content siblingContent = new URLContent(new URL("jar:" + zipUrl + "!/"
                  + URLEncoder.encode(zipEntryName, "UTF-8").replace("+", "%20")));
              writeZipEntry(zipOut, entryNameOrDirectory != null
                  ? entryNameOrDirectory + zipEntryName.substring(lastSlashIndex)
                  : zipEntryName.substring(lastSlashIndex + 1), siblingContent);
            }
          }
        } else {
          // Consider the content as not a multipart resource
          writeZipEntry(zipOut, entryNameOrDirectory, urlContent);
        }
      } else {
        // This should be the case only when resource isn't in a JAR file during development
        try {
          File contentFile = new File(urlContent.getURL().toURI());
          File parentFile = new File(contentFile.getParent());
          File [] siblingFiles = parentFile.listFiles();
          // Write in home stream each file that is stored in the same directory
          for (File siblingFile : siblingFiles) {
            if (!siblingFile.isDirectory()) {
              writeZipEntry(zipOut, (entryNameOrDirectory != null ? entryNameOrDirectory + "/" : "") + siblingFile.getName(),
                  new URLContent(siblingFile.toURI().toURL()));
            }
          }
        } catch (URISyntaxException ex) {
          IOException ex2 = new IOException();
          ex2.initCause(ex);
          throw ex2;
        }
      }
    } else {
      writeZipEntry(zipOut, entryNameOrDirectory, urlContent);
    }
  }

  /**
   * Writes in <code>zipOut</code> stream one or more entries matching the content
   * <code>urlContent</code> coming from a home file.
   */
  protected void writeHomeZipEntries(ZipOutputStream zipOut,
                                     String entryNameOrDirectory,
                                     URLContent urlContent) throws IOException {
    String entryName = urlContent.getJAREntryName();
    int slashIndex = entryName.indexOf('/');
    // If content comes from a directory of a home file
    if (slashIndex > 0) {
      URL zipUrl = urlContent.getJAREntryURL();
      String entryDirectory = entryName.substring(0, slashIndex + 1);
      // Write in home stream each zipped stream entry that is stored in the same directory
      for (ContentDigestManager.ZipEntryData zipEntry : ContentDigestManager.getInstance().getZipURLEntries(urlContent)) {
        String zipEntryName = zipEntry.getName();
        if (zipEntryName.startsWith(entryDirectory)) {
          Content siblingContent = new URLContent(new URL("jar:" + zipUrl + "!/"
              + URLEncoder.encode(zipEntryName, "UTF-8").replace("+", "%20")));
          writeZipEntry(zipOut, (entryNameOrDirectory != null ? entryNameOrDirectory + "/" : "") + zipEntryName.substring(slashIndex + 1),
              siblingContent);
        }
      }
    } else {
      writeZipEntry(zipOut, entryNameOrDirectory, urlContent);
    }
  }

  /**
   * Writes in <code>zipOut</code> stream all the sibling files of the zipped
   * <code>urlContent</code>.
   */
  protected void writeZipEntries(ZipOutputStream zipOut,
                                 String directory,
                                 URLContent urlContent) throws IOException {
    // Write in alphabetic order each zipped stream entry in out stream
    for (ContentDigestManager.ZipEntryData zipEntry : ContentDigestManager.getInstance().getZipURLEntries(urlContent)) {
      String zipEntryName = zipEntry.getName();
      Content siblingContent = new URLContent(new URL("jar:" + urlContent.getJAREntryURL() + "!/"
          + URLEncoder.encode(zipEntryName, "UTF-8").replace("+", "%20")));
      writeZipEntry(zipOut, (directory != null ? directory + "/" : "") + zipEntryName, siblingContent);
    }
  }

  /**
   * Writes in <code>zipOut</code> stream a new entry named <code>entryName</code> that
   * contains a given <code>content</code>.
   */
  protected void writeZipEntry(ZipOutputStream zipOut, String entryName, Content content) throws IOException {
    checkCurrentThreadIsntInterrupted();
    byte [] buffer = new byte [8192];
    InputStream contentIn = null;
    try {
      ZipEntry entry = new ZipEntry(entryName != null ? entryName : "0");
      // Force entry time to get the same output each time a ZIP is uploaded to server
      entry.setTime(1163631600000L);
      zipOut.putNextEntry(entry);
      contentIn = content.openStream();
      int size;
      while ((size = contentIn.read(buffer)) != -1) {
        zipOut.write(buffer, 0, size);
      }
      zipOut.closeEntry();
    } finally {
      if (contentIn != null) {
        contentIn.close();
      }
    }
  }
}