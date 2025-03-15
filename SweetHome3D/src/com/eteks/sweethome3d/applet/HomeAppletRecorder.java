/*
 * HomeAppletRecorder.java 13 Oct 2008
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
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
package com.eteks.sweethome3d.applet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

import com.eteks.sweethome3d.io.ContentRecording;
import com.eteks.sweethome3d.io.DefaultHomeInputStream;
import com.eteks.sweethome3d.io.DefaultHomeOutputStream;
import com.eteks.sweethome3d.io.HomeInputStream;
import com.eteks.sweethome3d.io.HomeOnlineRecorder;
import com.eteks.sweethome3d.io.HomeOutputStream;
import com.eteks.sweethome3d.io.HomeXMLExporter;
import com.eteks.sweethome3d.io.HomeXMLHandler;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.InterruptedRecorderException;
import com.eteks.sweethome3d.model.RecorderException;

/**
 * Recorder that stores homes on a HTTP server.
 * @author Emmanuel Puybaret
 */
public class HomeAppletRecorder extends HomeOnlineRecorder {
  private final ContentRecording contentRecording;
  private HomeXMLHandler         xmlHandler;
  private HomeXMLExporter        xmlExporter;

  /**
   * Creates a recorder that will use the URLs in parameter to write, read and list homes.
   * Homes will be saved with Home Java serialized entry.
   * @see SweetHome3DApplet
   */
  public HomeAppletRecorder(String writeHomeURL,
                            String readHomeURL,
                            String listHomesURL) {
    this(writeHomeURL, readHomeURL, listHomesURL, true);
  }

  /**
   * Creates a recorder that will use the URLs in parameter to write, read and list homes.
   * @see SweetHome3DApplet
   */
  public HomeAppletRecorder(String writeHomeURL,
                            String readHomeURL,
                            String listHomesURL,
                            boolean includeTemporaryContent) {
    this(writeHomeURL, readHomeURL, listHomesURL,
        includeTemporaryContent
            ? ContentRecording.INCLUDE_TEMPORARY_CONTENT
            : ContentRecording.INCLUDE_ALL_CONTENT);
  }

  /**
   * Creates a recorder that will use the URLs in parameter to write, read and list homes.
   * @see SweetHome3DApplet
   */
  public HomeAppletRecorder(String writeHomeURL,
                            String readHomeURL,
                            String listHomesURL,
                            ContentRecording contentRecording) {
    this(writeHomeURL, readHomeURL, listHomesURL, null, contentRecording);
  }

  /**
   * Creates a recorder that will use the URLs in parameter to write, read, list and delete homes.
   * @see SweetHome3DApplet
   */
  public HomeAppletRecorder(String writeHomeURL,
                            String readHomeURL,
                            String listHomesURL,
                            String deleteHomeURL,
                            ContentRecording contentRecording) {
    this(writeHomeURL, readHomeURL, listHomesURL, deleteHomeURL, contentRecording, null, null);
  }

  /**
   * Creates a recorder that will use the URLs in parameter to write, read, list and delete homes.
   * If <code>xmlHandler</code> and <code>xmlExporter</code> are not null, this recorder
   * will write a Home.xml entry rather than a Home Java serialized entry in saveed files.
   * @see SweetHome3DApplet
   */
  public HomeAppletRecorder(String writeHomeURL,
                            String readHomeURL,
                            String listHomesURL,
                            String deleteHomeURL,
                            ContentRecording contentRecording,
                            HomeXMLHandler  xmlHandler,
                            HomeXMLExporter xmlExporter) {
    super(writeHomeURL, readHomeURL, listHomesURL, deleteHomeURL);
    this.contentRecording = contentRecording;
    this.xmlHandler = xmlHandler;
    this.xmlExporter = xmlExporter;
  }

  /**
   * Returns the filter output stream used to write a home in the output stream in parameter.
   */
  @Override
  protected HomeOutputStream createHomeOutputStream(OutputStream out) throws IOException {
    return new DefaultHomeOutputStream(out, 9, this.contentRecording, this.xmlHandler == null, this.xmlExporter);
  }

  /**
   * Returns the filter input stream used to read a home from the input stream in parameter.
   */
  @Override
  protected HomeInputStream createHomeInputStream(InputStream in) throws IOException {
    return new DefaultHomeInputStream(in, this.contentRecording, this.xmlHandler, null, false);
  }

  /**
   * Returns the length of the home data that will be saved by this recorder.
   */
  public long getHomeLength(Home home) throws RecorderException {
    try {
      LengthOutputStream out = new LengthOutputStream();
      HomeOutputStream homeOut = createHomeOutputStream(out);
      homeOut.writeHome(home);
      homeOut.flush();
      return out.getLength();
    } catch (InterruptedIOException ex) {
      throw new InterruptedRecorderException("Home length computing interrupted");
    } catch (IOException ex) {
      throw new RecorderException("Can't compute home length", ex);
    }
  }

  /**
   * An output stream used to evaluate the length of written data.
   */
  private class LengthOutputStream extends OutputStream {
    private long length;

    @Override
    public void write(int b) throws IOException {
      this.length++;
    }

    public long getLength() {
      return this.length;
    }
  }
}
