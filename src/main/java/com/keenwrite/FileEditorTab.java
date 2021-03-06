/*
 * Copyright 2020 Karl Tauber and White Magic Software, Ltd.
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
package com.keenwrite;

import com.keenwrite.editors.EditorPane;
import com.keenwrite.editors.markdown.MarkdownEditorPane;
import com.keenwrite.service.events.Notification;
import com.keenwrite.service.events.Notifier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;
import javafx.stage.Window;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.undo.UndoManager;
import org.jetbrains.annotations.NotNull;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.keenwrite.Messages.get;
import static com.keenwrite.StatusBarNotifier.clue;
import static com.keenwrite.StatusBarNotifier.getNotifier;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;
import static javafx.application.Platform.runLater;

/**
 * Editor for a single file.
 */
public final class FileEditorTab extends Tab {

  private final MarkdownEditorPane mEditorPane = new MarkdownEditorPane();

  private final ReadOnlyBooleanWrapper mModified = new ReadOnlyBooleanWrapper();
  private final BooleanProperty canUndo = new SimpleBooleanProperty();
  private final BooleanProperty canRedo = new SimpleBooleanProperty();

  /**
   * Character encoding used by the file (or default encoding if none found).
   */
  private Charset mEncoding = UTF_8;

  /**
   * File to load into the editor.
   */
  private Path mPath;

  public FileEditorTab( final Path path ) {
    setPath( path );

    mModified.addListener( ( observable, oldPath, newPath ) -> updateTab() );

    setOnSelectionChanged( e -> {
      if( isSelected() ) {
        runLater( this::activated );
        requestFocus();
      }
    } );
  }

  private void updateTab() {
    setText( getTabTitle() );
    setGraphic( getModifiedMark() );
    setTooltip( getTabTooltip() );
  }

  /**
   * Returns the base filename (without the directory names).
   *
   * @return The untitled text if the path hasn't been set.
   */
  private String getTabTitle() {
    return getPath().getFileName().toString();
  }

  /**
   * Returns the full filename represented by the path.
   *
   * @return The untitled text if the path hasn't been set.
   */
  private Tooltip getTabTooltip() {
    final Path filePath = getPath();
    return new Tooltip( filePath == null ? "" : filePath.toString() );
  }

  /**
   * Returns a marker to indicate whether the file has been modified.
   *
   * @return "*" when the file has changed; otherwise null.
   */
  private Text getModifiedMark() {
    return isModified() ? new Text( "*" ) : null;
  }

  /**
   * Called when the user switches tab.
   */
  private void activated() {
    // Tab is closed or no longer active.
    if( getTabPane() == null || !isSelected() ) {
      return;
    }

    // If the tab is devoid of content, load it.
    if( getContent() == null ) {
      readFile();
      initLayout();
      initUndoManager();
    }
  }

  private void initLayout() {
    setContent( getScrollPane() );
  }

  /**
   * Tracks undo requests, but can only be called <em>after</em> load.
   */
  private void initUndoManager() {
    final UndoManager<?> undoManager = getUndoManager();
    undoManager.forgetHistory();

    // Bind the editor undo manager to the properties.
    mModified.bind( Bindings.not( undoManager.atMarkedPositionProperty() ) );
    canUndo.bind( undoManager.undoAvailableProperty() );
    canRedo.bind( undoManager.redoAvailableProperty() );
  }

  private void requestFocus() {
    getEditorPane().requestFocus();
  }

  /**
   * Searches from the caret position forward for the given string.
   *
   * @param needle The text string to match.
   */
  public void searchNext( final String needle ) {
    final String haystack = getEditorText();
    int index = haystack.indexOf( needle, getCaretPosition() );

    // Wrap around.
    if( index == -1 ) {
      index = haystack.indexOf( needle );
    }

    if( index >= 0 ) {
      setCaretPosition( index );
      getEditor().selectRange( index, index + needle.length() );
    }
  }

  /**
   * Gets a reference to the scroll pane that houses the editor.
   *
   * @return The editor's scroll pane, containing a vertical scrollbar.
   */
  public VirtualizedScrollPane<StyleClassedTextArea> getScrollPane() {
    return getEditorPane().getScrollPane();
  }

  /**
   * Returns the index into the text where the caret blinks happily away.
   *
   * @return A number from 0 to the editor's document text length.
   */
  public int getCaretPosition() {
    return getEditor().getCaretPosition();
  }

  /**
   * Moves the caret to a given offset.
   *
   * @param offset The new caret offset.
   */
  private void setCaretPosition( final int offset ) {
    getEditor().moveTo( offset );
    getEditor().requestFollowCaret();
  }

  /**
   * Returns the text area associated with this tab.
   *
   * @return A text editor.
   */
  private StyleClassedTextArea getEditor() {
    return getEditorPane().getEditor();
  }

  /**
   * Returns true if the given path exactly matches this tab's path.
   *
   * @param check The path to compare against.
   * @return true The paths are the same.
   */
  public boolean isPath( final Path check ) {
    final Path filePath = getPath();

    return filePath != null && filePath.equals( check );
  }

  /**
   * Reads the entire file contents from the path associated with this tab.
   */
  private void readFile() {
    final Path path = getPath();
    final File file = path.toFile();

    try {
      if( file.exists() ) {
        if( file.canWrite() && file.canRead() ) {
          final EditorPane pane = getEditorPane();
          pane.setText( asString( Files.readAllBytes( path ) ) );
          pane.scrollToTop();
        }
        else {
          final String msg = get( "FileEditor.loadFailed.reason.permissions" );
          clue( "FileEditor.loadFailed.message", file.toString(), msg );
        }
      }
    } catch( final Exception ex ) {
      clue( ex );
    }
  }

  /**
   * Saves the entire file contents from the path associated with this tab.
   *
   * @return true The file has been saved.
   */
  public boolean save() {
    try {
      final EditorPane editor = getEditorPane();
      Files.write( getPath(), asBytes( editor.getText() ) );
      editor.getUndoManager().mark();
      return true;
    } catch( final Exception ex ) {
      return popupAlert(
          "FileEditor.saveFailed.title",
          "FileEditor.saveFailed.message",
          ex
      );
    }
  }

  /**
   * Creates an alert dialog and waits for it to close.
   *
   * @param titleKey   Resource bundle key for the alert dialog title.
   * @param messageKey Resource bundle key for the alert dialog message.
   * @param e          The unexpected happening.
   * @return false
   */
  @SuppressWarnings("SameParameterValue")
  private boolean popupAlert(
      final String titleKey, final String messageKey, final Exception e ) {
    final Notifier service = getNotifier();
    final Path filePath = getPath();

    final Notification message = service.createNotification(
        get( titleKey ),
        get( messageKey ),
        filePath == null ? "" : filePath,
        e.getMessage()
    );

    try {
      service.createError( getWindow(), message ).showAndWait();
    } catch( final Exception ex ) {
      clue( ex );
    }

    return false;
  }

  private Window getWindow() {
    final Scene scene = getEditorPane().getScene();

    if( scene == null ) {
      throw new UnsupportedOperationException( "No scene window available" );
    }

    return scene.getWindow();
  }

  /**
   * Returns a best guess at the file encoding. If the encoding could not be
   * detected, this will return the default charset for the JVM.
   *
   * @param bytes The bytes to perform character encoding detection.
   * @return The character encoding.
   */
  private Charset detectEncoding( final byte[] bytes ) {
    final var detector = new UniversalDetector( null );
    detector.handleData( bytes, 0, bytes.length );
    detector.dataEnd();

    final String charset = detector.getDetectedCharset();

    return charset == null
        ? Charset.defaultCharset()
        : Charset.forName( charset.toUpperCase( ENGLISH ) );
  }

  /**
   * Converts the given string to an array of bytes using the encoding that was
   * originally detected (if any) and associated with this file.
   *
   * @param text The text to convert into the original file encoding.
   * @return A series of bytes ready for writing to a file.
   */
  private byte[] asBytes( final String text ) {
    return text.getBytes( getEncoding() );
  }

  /**
   * Converts the given bytes into a Java String. This will call setEncoding
   * with the encoding detected by the CharsetDetector.
   *
   * @param text The text of unknown character encoding.
   * @return The text, in its auto-detected encoding, as a String.
   */
  private String asString( final byte[] text ) {
    setEncoding( detectEncoding( text ) );
    return new String( text, getEncoding() );
  }

  /**
   * Returns the path to the file being edited in this tab.
   *
   * @return A non-null instance.
   */
  public Path getPath() {
    return mPath;
  }

  /**
   * Sets the path to a file for editing and then updates the tab with the
   * file contents.
   *
   * @param path A non-null instance.
   */
  public void setPath( final Path path ) {
    assert path != null;
    mPath = path;

    updateTab();
  }

  public boolean isModified() {
    return mModified.get();
  }

  ReadOnlyBooleanProperty modifiedProperty() {
    return mModified.getReadOnlyProperty();
  }

  BooleanProperty canUndoProperty() {
    return this.canUndo;
  }

  BooleanProperty canRedoProperty() {
    return this.canRedo;
  }

  private UndoManager<?> getUndoManager() {
    return getEditorPane().getUndoManager();
  }

  /**
   * Forwards to the editor pane's listeners for text change events.
   *
   * @param listener The listener to notify when the text changes.
   */
  public void addTextChangeListener( final ChangeListener<String> listener ) {
    getEditorPane().addTextChangeListener( listener );
  }

  /**
   * Forwards to the editor pane's listeners for caret change events.
   *
   * @param listener Notified when the caret position changes.
   */
  public void addCaretPositionListener(
      final ChangeListener<? super Integer> listener ) {
    getEditorPane().addCaretPositionListener( listener );
  }

  /**
   * Forwards to the editor pane's listeners for paragraph index change events.
   *
   * @param listener Notified when the caret's paragraph index changes.
   */
  public void addCaretParagraphListener(
      final ChangeListener<? super Integer> listener ) {
    getEditorPane().addCaretParagraphListener( listener );
  }

  public <T extends Event> void addEventFilter(
      final EventType<T> eventType,
      final EventHandler<? super T> eventFilter ) {
    getEditor().addEventFilter( eventType, eventFilter );
  }

  /**
   * Forwards the request to the editor pane.
   *
   * @return The text to process.
   */
  public String getEditorText() {
    return getEditorPane().getText();
  }

  /**
   * Returns the editor pane, or creates one if it doesn't yet exist.
   *
   * @return The editor pane, never null.
   */
  @NotNull
  public MarkdownEditorPane getEditorPane() {
    return mEditorPane;
  }

  /**
   * Returns the encoding for the file, defaulting to UTF-8 if it hasn't been
   * determined.
   *
   * @return The file encoding or UTF-8 if unknown.
   */
  private Charset getEncoding() {
    return mEncoding;
  }

  private void setEncoding( final Charset encoding ) {
    assert encoding != null;
    mEncoding = encoding;
  }

  /**
   * Returns the tab title, without any modified indicators.
   *
   * @return The tab title.
   */
  @Override
  public String toString() {
    return getTabTitle();
  }
}
