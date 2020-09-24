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
package com.keenwrite.preview;

import com.keenwrite.adapters.DocumentAdapter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingNode;
import javafx.scene.Node;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.net.URI;
import java.nio.file.Path;

import static com.keenwrite.Constants.*;
import static com.keenwrite.StatusBarNotifier.clue;
import static com.keenwrite.util.ProtocolResolver.getProtocol;
import static java.awt.Desktop.Action.BROWSE;
import static java.awt.Desktop.getDesktop;
import static java.lang.Math.max;
import static javax.swing.SwingUtilities.invokeLater;
import static org.xhtmlrenderer.swing.ImageResourceLoader.NO_OP_REPAINT_LISTENER;

/**
 * HTML preview pane is responsible for rendering an HTML document.
 */
public final class HTMLPreviewPane extends SwingNode {

  /**
   * Suppresses scrolling to the top on every key press.
   */
  private static class HTMLPanel extends XHTMLPanel {
    @Override
    public void resetScrollPosition() {
    }
  }

  /**
   * Suppresses scroll attempts until after the document has loaded.
   */
  private static final class DocumentEventHandler extends DocumentAdapter {
    private final BooleanProperty mReadyProperty = new SimpleBooleanProperty();

    public BooleanProperty readyProperty() {
      return mReadyProperty;
    }

    @Override
    public void documentStarted() {
      mReadyProperty.setValue( Boolean.FALSE );
    }

    @Override
    public void documentLoaded() {
      mReadyProperty.setValue( Boolean.TRUE );
    }
  }

  /**
   * Ensure that images are constrained to the panel width upon resizing.
   */
  private final class ResizeListener extends ComponentAdapter {
    @Override
    public void componentResized( final ComponentEvent e ) {
      setWidth( e );
    }

    @Override
    public void componentShown( final ComponentEvent e ) {
      setWidth( e );
    }

    /**
     * Sets the width of the {@link HTMLPreviewPane} so that images can be
     * scaled to fit. The scale factor is adjusted a bit below the full width
     * to prevent the horizontal scrollbar from appearing.
     *
     * @param event The component that defines the image scaling width.
     */
    private void setWidth( final ComponentEvent event ) {
      final int width = (int) (event.getComponent().getWidth() * .95);
      HTMLPreviewPane.this.mImageLoader.widthProperty().set( width );
    }
  }

  /**
   * Responsible for opening hyperlinks. External hyperlinks are opened in
   * the system's default browser; local file system links are opened in the
   * editor.
   */
  private static class HyperlinkListener extends LinkListener {
    @Override
    public void linkClicked( final BasicPanel panel, final String link ) {
      try {
        final var protocol = getProtocol( link );

        switch( protocol ) {
          case HTTP:
            final var desktop = getDesktop();

            if( desktop.isSupported( BROWSE ) ) {
              desktop.browse( new URI( link ) );
            }
            break;
          case FILE:
            // TODO: #88 -- publish a message to the event bus.
            break;
        }
      } catch( final Exception ex ) {
        clue( ex );
      }
    }
  }

  /**
   * Render CSS using points (pt) not pixels (px) to reduce the chance of
   * poor rendering.
   */
  private static final String HTML_PREFIX = "<!DOCTYPE html>"
      + "<html>"
      + "<head>"
      + "<link rel='stylesheet' href='" +
      HTMLPreviewPane.class.getResource( STYLESHEET_PREVIEW ) + "'/>"
      + "</head>"
      + "<body>";

  /**
   * Used to reset the {@link #mHtmlDocument} buffer so that the
   * {@link #HTML_PREFIX} need not be appended all the time.
   */
  private static final int HTML_PREFIX_LENGTH = HTML_PREFIX.length();

  private static final W3CDom W3C_DOM = new W3CDom();
  private static final XhtmlNamespaceHandler NS_HANDLER =
      new XhtmlNamespaceHandler();

  /**
   * The buffer is reused so that previous memory allocations need not repeat.
   */
  private final StringBuilder mHtmlDocument = new StringBuilder( 65536 );

  private final HTMLPanel mHtmlRenderer = new HTMLPanel();
  private final JScrollPane mScrollPane = new JScrollPane( mHtmlRenderer );
  private final DocumentEventHandler mDocHandler = new DocumentEventHandler();
  private final CustomImageLoader mImageLoader = new CustomImageLoader();

  private Path mPath = DEFAULT_DIRECTORY;

  /**
   * Creates a new preview pane that can scroll to the caret position within the
   * document.
   */
  public HTMLPreviewPane() {
    setStyle( "-fx-background-color: white;" );

    // No need to append same prefix each time the HTML content is updated.
    mHtmlDocument.append( HTML_PREFIX );

    // Inject an SVG renderer that produces high-quality SVG buffered images.
    final var factory = new ChainedReplacedElementFactory();
    factory.addFactory( new SvgReplacedElementFactory() );
    factory.addFactory( new SwingReplacedElementFactory(
        NO_OP_REPAINT_LISTENER, mImageLoader ) );

    final var context = getSharedContext();
    final var textRenderer = context.getTextRenderer();
    context.setReplacedElementFactory( factory );
    textRenderer.setSmoothingThreshold( 0 );

    setContent( mScrollPane );
    mHtmlRenderer.addDocumentListener( mDocHandler );
    mHtmlRenderer.addComponentListener( new ResizeListener() );

    // The default mouse click listener attempts navigation within the
    // preview panel. We want to usurp that behaviour to open the link in
    // a platform-specific browser.
    for( final var listener : mHtmlRenderer.getMouseTrackingListeners() ) {
      if( !(listener instanceof HoverListener) ) {
        mHtmlRenderer.removeMouseTrackingListener( (FSMouseListener) listener );
      }
    }

    mHtmlRenderer.addMouseTrackingListener( new HyperlinkListener() );
  }

  /**
   * Updates the internal HTML source, loads it into the preview pane, then
   * scrolls to the caret position.
   *
   * @param html The new HTML document to display.
   */
  public void process( final String html ) {
    final var docJsoup = Jsoup.parse( decorate( html ) );
    final var docW3c = W3C_DOM.fromJsoup( docJsoup );

    // Access to a Swing component must occur from the Event Dispatch
    // Thread (EDT) according to Swing threading restrictions.
    invokeLater(
        () -> mHtmlRenderer.setDocument( docW3c, getBaseUrl(), NS_HANDLER )
    );
  }

  /**
   * Clears the preview pane by rendering an empty string.
   */
  public void clear() {
    process( "" );
  }

  /**
   * Scrolls to an anchor link. The anchor links are injected when the
   * HTML document is created.
   *
   * @param id The unique anchor link identifier.
   */
  public void tryScrollTo( final int id ) {
    final ChangeListener<Boolean> listener = new ChangeListener<>() {
      @Override
      public void changed(
          final ObservableValue<? extends Boolean> observable,
          final Boolean oldValue,
          final Boolean newValue ) {
        if( newValue ) {
          scrollTo( id );

          mDocHandler.readyProperty().removeListener( this );
        }
      }
    };

    mDocHandler.readyProperty().addListener( listener );
  }

  /**
   * Scrolls to the closest element matching the given identifier without
   * waiting for the document to be ready. Be sure the document is ready
   * before calling this method.
   *
   * @param id Paragraph index.
   */
  public void scrollTo( final int id ) {
    if( id < 2 ) {
      scrollToTop();
    }
    else {
      Box box = findPrevBox( id );
      box = box == null ? findNextBox( id + 1 ) : box;

      if( box == null ) {
        scrollToBottom();
      }
      else {
        scrollTo( box );
      }
    }
  }

  private Box findPrevBox( final int id ) {
    int prevId = id;
    Box box = null;

    while( prevId > 0 && (box = getBoxById( PARAGRAPH_ID_PREFIX + prevId )) == null ) {
      prevId--;
    }

    return box;
  }

  private Box findNextBox( final int id ) {
    int nextId = id;
    Box box = null;

    while( nextId - id < 5 &&
        (box = getBoxById( PARAGRAPH_ID_PREFIX + nextId )) == null ) {
      nextId++;
    }

    return box;
  }

  private void scrollTo( final Point point ) {
    invokeLater( () -> mHtmlRenderer.scrollTo( point ) );
  }

  private void scrollTo( final Box box ) {
    scrollTo( createPoint( box ) );
  }

  private void scrollToY( final int y ) {
    scrollTo( new Point( 0, y ) );
  }

  private void scrollToTop() {
    scrollToY( 0 );
  }

  private void scrollToBottom() {
    scrollToY( mHtmlRenderer.getHeight() );
  }

  private Box getBoxById( final String id ) {
    return getSharedContext().getBoxById( id );
  }

  private String decorate( final String html ) {
    // Trim the HTML back to only the prefix.
    mHtmlDocument.setLength( HTML_PREFIX_LENGTH );

    // Write the HTML body element followed by closing tags.
    return mHtmlDocument.append( html ).toString();
  }

  public Path getPath() {
    return mPath;
  }

  public void setPath( final Path path ) {
    assert path != null;
    mPath = path;
  }

  /**
   * Content to embed in a panel.
   *
   * @return The content to display to the user.
   */
  public Node getNode() {
    return this;
  }

  public JScrollPane getScrollPane() {
    return mScrollPane;
  }

  public JScrollBar getVerticalScrollBar() {
    return getScrollPane().getVerticalScrollBar();
  }

  /**
   * Creates a {@link Point} to use as a reference for scrolling to the area
   * described by the given {@link Box}. The {@link Box} coordinates are used
   * to populate the {@link Point}'s location, with minor adjustments for
   * vertical centering.
   *
   * @param box The {@link Box} that represents a scrolling anchor reference.
   * @return A coordinate suitable for scrolling to.
   */
  private Point createPoint( final Box box ) {
    assert box != null;

    int x = box.getAbsX();

    // Scroll back up by half the height of the scroll bar to keep the typing
    // area within the view port. Otherwise the view port will have jumped too
    // high up and the whatever gets typed won't be visible.
    int y = max(
        box.getAbsY() - (mScrollPane.getVerticalScrollBar().getHeight() / 2),
        0 );

    if( !box.getStyle().isInline() ) {
      final var margin = box.getMargin( mHtmlRenderer.getLayoutContext() );
      x += margin.left();
      y += margin.top();
    }

    return new Point( x, y );
  }

  private String getBaseUrl() {
    final Path basePath = getPath();
    final Path parent = basePath == null ? null : basePath.getParent();

    return parent == null ? "" : parent.toUri().toString();
  }

  private SharedContext getSharedContext() {
    return mHtmlRenderer.getSharedContext();
  }
}
