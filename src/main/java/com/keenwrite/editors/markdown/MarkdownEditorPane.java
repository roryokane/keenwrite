/*
 * Copyright 2020 Karl Tauber and White Magic Software, Ltd.
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
package com.keenwrite.editors.markdown;

import com.keenwrite.dialogs.ImageDialog;
import com.keenwrite.dialogs.LinkDialog;
import com.keenwrite.editors.EditorPane;
import com.keenwrite.processors.markdown.BlockExtension;
import com.keenwrite.processors.markdown.MarkdownProcessor;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.html.MutableAttributes;
import javafx.scene.control.Dialog;
import javafx.scene.control.IndexRange;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.keenwrite.Constants.STYLESHEET_MARKDOWN;
import static com.keenwrite.util.Utils.ltrim;
import static com.keenwrite.util.Utils.rtrim;
import static javafx.scene.input.KeyCode.ENTER;
import static javafx.scene.input.KeyCombination.CONTROL_DOWN;
import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;

/**
 * Provides the ability to edit a text document.
 */
public class MarkdownEditorPane extends EditorPane {
  private static final Pattern PATTERN_AUTO_INDENT = Pattern.compile(
      "(\\s*[*+-]\\s+|\\s*[0-9]+\\.\\s+|\\s+)(.*)" );

  /**
   * Any of these followed by a space and a letter produce a line
   * by themselves. The ">" need not be followed by a space.
   */
  private static final Pattern PATTERN_NEW_LINE = Pattern.compile(
      "^>|(((#+)|([*+\\-])|([1-9]\\.))\\s+).+" );

  public MarkdownEditorPane() {
    initEditor();
  }

  private void initEditor() {
    final StyleClassedTextArea textArea = getEditor();

    textArea.setWrapText( true );
    textArea.getStyleClass().add( "markdown-editor" );
    textArea.getStylesheets().add( STYLESHEET_MARKDOWN );

    addKeyboardListener( keyPressed( ENTER ), this::enterPressed );
    addKeyboardListener( keyPressed( KeyCode.X, CONTROL_DOWN ), this::cut );
  }

  public void insertLink() {
    insertObject( createLinkDialog() );
  }

  public void insertImage() {
    insertObject( createImageDialog() );
  }

  /**
   * Returns the editor's paragraph number that will be close to its HTML
   * paragraph ID. Ultimately this solution is flawed because there isn't
   * a straightforward correlation between the document being edited and
   * what is rendered. XML documents transformed through stylesheets have
   * no readily determined correlation. Images, tables, and other
   * objects affect the relative location of the current paragraph being
   * edited with respect to the preview pane.
   * <p>
   * See
   * {@link BlockExtension.IdAttributeProvider#setAttributes(Node, AttributablePart, MutableAttributes)}}
   * for details.
   * </p>
   * <p>
   * Injecting a token into the document, as per a previous version of the
   * application, can instruct the preview pane where to shift the viewport.
   * </p>
   *
   * @param paraIndex The paragraph index from the editor pane to scroll to
   *                  in the preview pane, which  will be approximated if an
   *                  equivalent cannot be found.
   * @return A unique identifier that correlates to an equivalent paragraph
   * number once the Markdown is rendered into HTML.
   */
  public int approximateParagraphId( final int paraIndex ) {
    final StyleClassedTextArea editor = getEditor();
    final List<String> lines = new ArrayList<>( 4096 );

    int i = 0;
    String prevText = "";
    boolean withinFencedBlock = false;
    boolean withinCodeBlock = false;

    for( final var p : editor.getParagraphs() ) {
      if( i > paraIndex ) {
        break;
      }

      final String text = p.getText().replace( '>', ' ' );
      if( text.startsWith( "```" ) ) {
        if( withinFencedBlock = !withinFencedBlock ) {
          lines.add( text );
        }
      }

      if( !withinFencedBlock ) {
        final boolean foundCodeBlock = text.startsWith( "    " );

        if( foundCodeBlock && !withinCodeBlock ) {
          lines.add( text );
          withinCodeBlock = true;
        }
        else if( !foundCodeBlock ) {
          withinCodeBlock = false;
        }
      }

      if( !withinFencedBlock && !withinCodeBlock &&
          ((!text.isBlank() && prevText.isBlank()) ||
              PATTERN_NEW_LINE.matcher( text ).matches()) ) {
        lines.add( text );
      }

      prevText = text;
      i++;
    }

    // Scrolling index is 1-based.
    return Math.max( lines.size() - 1, 0 );
  }

  /**
   * Gets the index of the paragraph where the caret is positioned.
   *
   * @return The paragraph number for the caret.
   */
  public int getCurrentParagraphIndex() {
    return getEditor().getCurrentParagraph();
  }

  /**
   * @param leading  Characters to insert at the beginning of the current
   *                 selection (or paragraph).
   * @param trailing Characters to insert at the end of the current selection
   *                 (or paragraph).
   */
  public void surroundSelection( final String leading, final String trailing ) {
    surroundSelection( leading, trailing, null );
  }

  /**
   * @param leading  Characters to insert at the beginning of the current
   *                 selection (or paragraph).
   * @param trailing Characters to insert at the end of the current selection
   *                 (or paragraph).
   * @param hint     Instructional text inserted within the leading and
   *                 trailing characters, provided no text is selected.
   */
  public void surroundSelection(
      String leading, String trailing, final String hint ) {
    final StyleClassedTextArea textArea = getEditor();

    // Note: not using textArea.insertText() to insert leading and trailing
    // because this would add two changes to undo history
    final IndexRange selection = textArea.getSelection();
    int start = selection.getStart();
    int end = selection.getEnd();

    final String selectedText = textArea.getSelectedText();

    String trimmedText = selectedText.trim();
    if( trimmedText.length() < selectedText.length() ) {
      start += selectedText.indexOf( trimmedText );
      end = start + trimmedText.length();
    }

    // remove leading whitespaces from leading text if selection starts at zero
    if( start == 0 ) {
      leading = ltrim( leading );
    }

    // remove trailing whitespaces from trailing text if selection ends at
    // text end
    if( end == textArea.getLength() ) {
      trailing = rtrim( trailing );
    }

    // remove leading line separators from leading text
    // if there are line separators before the selected text
    if( leading.startsWith( "\n" ) ) {
      for( int i = start - 1; i >= 0 && leading.startsWith( "\n" ); i-- ) {
        if( !"\n".equals( textArea.getText( i, i + 1 ) ) ) {
          break;
        }

        leading = leading.substring( 1 );
      }
    }

    // remove trailing line separators from trailing or leading text
    // if there are line separators after the selected text
    final boolean trailingIsEmpty = trailing.isEmpty();
    String str = trailingIsEmpty ? leading : trailing;

    if( str.endsWith( "\n" ) ) {
      final int length = textArea.getLength();

      for( int i = end; i < length && str.endsWith( "\n" ); i++ ) {
        if( !"\n".equals( textArea.getText( i, i + 1 ) ) ) {
          break;
        }

        str = str.substring( 0, str.length() - 1 );
      }

      if( trailingIsEmpty ) {
        leading = str;
      }
      else {
        trailing = str;
      }
    }

    int selStart = start + leading.length();
    int selEnd = end + leading.length();

    // insert hint text if selection is empty
    if( hint != null && trimmedText.isEmpty() ) {
      trimmedText = hint;
      selEnd = selStart + hint.length();
    }

    // prevent undo merging with previous text entered by user
    getUndoManager().preventMerge();

    // replace text and update selection
    textArea.replaceText( start, end, leading + trimmedText + trailing );
    textArea.selectRange( selStart, selEnd );
  }

  private void enterPressed( final KeyEvent e ) {
    final StyleClassedTextArea textArea = getEditor();
    final String currentLine =
        textArea.getText( textArea.getCurrentParagraph() );
    final Matcher matcher = PATTERN_AUTO_INDENT.matcher( currentLine );

    String newText = "\n";

    if( matcher.matches() ) {
      if( !matcher.group( 2 ).isEmpty() ) {
        // indent new line with same whitespace characters and list markers
        // as current line
        newText = newText.concat( matcher.group( 1 ) );
      }
      else {
        // current line contains only whitespace characters and list markers
        // --> empty current line
        final int caretPosition = textArea.getCaretPosition();
        textArea.selectRange( caretPosition - currentLine.length(),
                              caretPosition );
      }
    }

    textArea.replaceSelection( newText );

    // Ensure that the window scrolls when Enter is pressed at the bottom of
    // the pane.
    textArea.requestFollowCaret();
  }

  private void cut( final KeyEvent event ) {
    super.cut();
  }

  /**
   * Returns one of: selected text, word under cursor, or parsed hyperlink from
   * the markdown AST.
   *
   * @return An instance containing the link URL and display text.
   */
  private HyperlinkModel getHyperlink() {
    final StyleClassedTextArea textArea = getEditor();
    final String selectedText = textArea.getSelectedText();

    // Get the current paragraph, convert to Markdown nodes.
    final MarkdownProcessor mp = MarkdownProcessor.create();
    final int p = textArea.getCurrentParagraph();
    final String paragraph = textArea.getText( p );
    final Node node = mp.toNode( paragraph );
    final LinkVisitor visitor = new LinkVisitor( textArea.getCaretColumn() );
    final Link link = visitor.process( node );

    if( link != null ) {
      textArea.selectRange( p, link.getStartOffset(), p, link.getEndOffset() );
    }

    return createHyperlinkModel(
        link, selectedText, "https://localhost"
    );
  }

  @SuppressWarnings("SameParameterValue")
  private HyperlinkModel createHyperlinkModel(
      final Link link, final String selection, final String url ) {

    return link == null
        ? new HyperlinkModel( selection, url )
        : new HyperlinkModel( link );
  }

  private Path getParentPath() {
    final Path path = getPath();
    return (path != null) ? path.getParent() : null;
  }

  private Dialog<String> createLinkDialog() {
    return new LinkDialog( getWindow(), getHyperlink() );
  }

  private Dialog<String> createImageDialog() {
    return new ImageDialog( getWindow(), getParentPath() );
  }

  private void insertObject( final Dialog<String> dialog ) {
    dialog.showAndWait().ifPresent(
        result -> getEditor().replaceSelection( result )
    );
  }

  private Window getWindow() {
    return getScrollPane().getScene().getWindow();
  }
}
