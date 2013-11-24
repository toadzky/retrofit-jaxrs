// Copyright 2012 Square, Inc.
package retrofit.mime;

import org.junit.Test;

import static org.fest.assertions.api.Assertions.assertThat;
import static retrofit.mime.MimeUtil.parseCharset;

public class MimeUtilTest
{
  @Test
  public void charsetParsing()
  {
    assertThat(parseCharset("text/plain;charset=utf-8")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; charset=utf-8")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain;  charset=utf-8")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; \tcharset=utf-8")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; \r\n\tcharset=utf-8")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; CHARSET=utf-8")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; charset=UTF-8")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; charset=\"\\u\\tf-\\8\"")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; charset=\"utf-8\"")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain;charset=utf-8;other=thing")).isEqualToIgnoringCase("UTF-8");
    assertThat(parseCharset("text/plain; notthecharset=utf-16;")).isEqualToIgnoringCase("UTF-8");
  }
}
