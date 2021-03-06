/*
 * Copyright 2020 White Magic Software, Ltd.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.keenwrite.sigils;

import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;

/**
 * Brackets definition keys with token delimiters.
 */
public class YamlSigilOperator extends SigilOperator {
  public static final char KEY_SEPARATOR_DEF = '.';

  private static final String mDelimiterBegan =
      getUserPreferences().getDefDelimiterBegan();
  private static final String mDelimiterEnded =
      getUserPreferences().getDefDelimiterEnded();

  /**
   * Non-greedy match of key names delimited by definition tokens.
   */
  private static final String REGEX =
      format( "(%s.*?%s)", quote( mDelimiterBegan ), quote( mDelimiterEnded ) );

  /**
   * Compiled regular expression for matching delimited references.
   */
  public static final Pattern REGEX_PATTERN = compile( REGEX );

  /**
   * Returns the given {@link String} verbatim because variables in YAML
   * documents and plain Markdown documents already have the appropriate
   * tokenizable syntax wrapped around the text.
   *
   * @param key Returned verbatim.
   */
  @Override
  public String apply( final String key ) {
    return key;
  }

  /**
   * Adds delimiters to the given key.
   *
   * @param key The key to adorn with start and stop definition tokens.
   * @return The given key bracketed by definition token symbols.
   */
  public static String entoken( final String key ) {
    assert key != null;
    return mDelimiterBegan + key + mDelimiterEnded;
  }

  /**
   * Removes start and stop definition key delimiters from the given key. This
   * method does not check for delimiters, only that there are sufficient
   * characters to remove from either end of the given key.
   *
   * @param key The key adorned with start and stop definition tokens.
   * @return The given key with the delimiters removed.
   */
  public static String detoken( final String key ) {
    final int beganLen = mDelimiterBegan.length();
    final int endedLen = mDelimiterEnded.length();

    return key.length() > beganLen + endedLen
        ? key.substring( beganLen, key.length() - endedLen )
        : key;
  }
}
