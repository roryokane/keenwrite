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
package com.keenwrite.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Observer;

/**
 * Listens for changes to file system files and directories.
 */
public interface Snitch extends Service, Runnable {

  /**
   * Adds an observer to the set of observers for this object, provided that it
   * is not the same as some observer already in the set. The order in which
   * notifications will be delivered to multiple observers is not specified.
   *
   * @param o The object to receive changed events for when monitored files
   *          are changed.
   */
  void addObserver( Observer o );

  /**
   * Listens for changes to the path. If the path specifies a file, then only
   * notifications pertaining to that file are sent. Otherwise, change events
   * for the directory that contains the file are sent. This method must allow
   * for multiple calls to the same file without incurring additional listeners
   * or events.
   *
   * @param file Send notifications when this file changes, can be null.
   * @throws IOException Couldn't create a watcher for the given file.
   */
  void listen( Path file ) throws IOException;

  /**
   * Removes the given file from the notifications list.
   *
   * @param file The file to stop monitoring for any changes, can be null.
   */
  void ignore( Path file );

  /**
   * Stop listening for events.
   */
  void stop();
}
