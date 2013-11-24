/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package retrofit.mime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public final class MultipartTypedOutput implements TypedOutput
{

  private static final class MimePart
  {
    private final TypedOutput body;
    private final String      name;
    private final boolean     isFirst;
    private final String      boundary;

    private byte[]  partBoundary;
    private byte[]  partHeader;
    private boolean isBuilt;

    public MimePart(String name, TypedOutput body, String boundary, boolean isFirst)
    {
      this.name = name;
      this.body = body;
      this.isFirst = isFirst;
      this.boundary = boundary;
    }

    public void writeTo(OutputStream out) throws IOException
    {
      build();
      out.write(partBoundary);
      out.write(partHeader);
      body.writeTo(out);
    }

    public long size()
    {
      build();
      if (body.length() > -1)
      {
        return body.length() + partBoundary.length + partHeader.length;
      }
      else
      {
        return -1;
      }
    }

    private void build()
    {
      if (isBuilt)
      {
        return;
      }
      partBoundary = buildBoundary(boundary, isFirst, false);
      partHeader = buildHeader(name, body);
      isBuilt = true;
    }
  }

  private final List<MimePart> mimeParts = new LinkedList<MimePart>();

  private final byte[] footer;
  private final String boundary;
  private       long   length;

  public MultipartTypedOutput()
  {
    this(UUID.randomUUID().toString());
  }

  MultipartTypedOutput(String boundary)
  {
    this.boundary = boundary;
    footer = buildBoundary(boundary, false, true);
    length = footer.length;
  }

  List<byte[]> getParts() throws IOException
  {
    List<byte[]> parts = new ArrayList<byte[]>(mimeParts.size());
    for (MimePart part : mimeParts)
    {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      part.writeTo(bos);
      parts.add(bos.toByteArray());
    }
    return parts;
  }

  public void addPart(String name, TypedOutput body)
  {
    if (name == null)
    {
      throw new NullPointerException("Part name must not be null.");
    }
    if (body == null)
    {
      throw new NullPointerException("Part body must not be null.");
    }

    MimePart part = new MimePart(name, body, boundary, mimeParts.isEmpty());
    mimeParts.add(part);

    length += part.size();
  }

  public int getPartCount()
  {
    return mimeParts.size();
  }

  @Override
  public String fileName()
  {
    return null;
  }

  @Override
  public String mimeType()
  {
    return "multipart/form-data; boundary=" + boundary;
  }

  @Override
  public long length()
  {
    return length;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException
  {
    for (MimePart part : mimeParts)
    {
      part.writeTo(out);
    }
    out.write(footer);
  }

  private static byte[] buildBoundary(String boundary, boolean first, boolean last)
  {
    try
    {
      StringBuilder sb = new StringBuilder();
      if (!first)
      {
        sb.append("\r\n");
      }
      sb.append("--");
      sb.append(boundary);
      if (last)
      {
        sb.append("--");
      }
      else
      {
        sb.append("\r\n");
      }
      return sb.toString().getBytes("UTF-8");
    }
    catch (IOException ex)
    {
      throw new RuntimeException("Unable to write multipart boundary", ex);
    }
  }

  private static byte[] buildHeader(String name, TypedOutput value)
  {
    try
    {
      StringBuilder headers = new StringBuilder();
      headers.append("Content-Disposition: form-data; name=\"");
      headers.append(name);
      if (value.fileName() != null)
      {
        headers.append("\"; filename=\"");
        headers.append(value.fileName());
      }
      headers.append("\"\r\nContent-Type: ");
      headers.append(value.mimeType());
      if (value.length() != -1)
      {
        headers.append("\r\nContent-Length: ").append(value.length());
      }
      headers.append("\r\nContent-Transfer-Encoding: binary\r\n\r\n");
      return headers.toString().getBytes("UTF-8");
    }
    catch (IOException ex)
    {
      throw new RuntimeException("Unable to write multipart header", ex);
    }
  }
}
