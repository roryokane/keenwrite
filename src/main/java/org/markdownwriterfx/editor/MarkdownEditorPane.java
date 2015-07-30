/*
 * Copyright (c) 2015 Karl Tauber <karl at jformdesigner dot com>
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

package org.markdownwriterfx.editor;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.undo.UndoManager;
import org.fxmisc.wellbehaved.event.EventHandlerHelper;
import org.markdownwriterfx.util.Utils;
import org.pegdown.Extensions;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;

/**
 * Markdown editor pane.
 *
 * Uses pegdown (https://github.com/sirthias/pegdown) for parsing markdown.
 *
 * @author Karl Tauber
 */
public class MarkdownEditorPane
{
	private final StyleClassedTextArea textArea;
	private PegDownProcessor pegDownProcessor;

	public MarkdownEditorPane() {
		textArea = new StyleClassedTextArea(false);
		textArea.setWrapText(true);
		textArea.getStyleClass().add("markdown-editor");
		textArea.getStylesheets().add("org/markdownwriterfx/editor/MarkdownEditor.css");

		textArea.textProperty().addListener((observable, oldText, newText) -> {
			RootNode astRoot = parseMarkdown(newText);
			applyHighlighting(astRoot);
			markdownAST.set(astRoot);
		});

		// search for vertical scrollbar and add change listener to update 'scrollY' property
		textArea.getChildrenUnmodifiable().addListener((InvalidationListener) e -> {
			ScrollBar vScrollBar = Utils.findVScrollBar(textArea);
			if (vScrollBar != null) {
				vScrollBar.valueProperty().addListener((observable, oldValue, newValue) -> {
					double value = newValue.doubleValue();
					double maxValue = vScrollBar.maxProperty().get();
					scrollY.set((maxValue != 0) ? Math.min(Math.max(value / maxValue, 0), 1) : 0);
				});
			}
		});
	}

	public void installEditorShortcuts(EventHandler<KeyEvent> editorShortcuts) {
		EventHandlerHelper.install(textArea.onKeyPressedProperty(), editorShortcuts);
	}

	public Node getNode() {
		return textArea;
	}

	public UndoManager getUndoManager() {
		return textArea.getUndoManager();
	}

	public void requestFocus() {
		Platform.runLater(() -> textArea.requestFocus());
	}

	// 'markdown' property
	public String getMarkdown() { return textArea.getText(); }
	public void setMarkdown(String markdown) { textArea.replaceText(markdown); textArea.selectRange(0, 0); }
	public ObservableValue<String> markdownProperty() { return textArea.textProperty(); }

	// 'markdownAST' property
	private final ReadOnlyObjectWrapper<RootNode> markdownAST = new ReadOnlyObjectWrapper<>();
	public RootNode getMarkdownAST() { return markdownAST.get(); }
	public ReadOnlyObjectProperty<RootNode> markdownASTProperty() { return markdownAST.getReadOnlyProperty(); }

	// 'scrollY' property
	private final ReadOnlyDoubleWrapper scrollY = new ReadOnlyDoubleWrapper();
	public double getScrollY() { return scrollY.get(); }
	public ReadOnlyDoubleProperty scrollYProperty() { return scrollY.getReadOnlyProperty(); }

	private RootNode parseMarkdown(String text) {
		if(pegDownProcessor == null)
			pegDownProcessor = new PegDownProcessor(Extensions.ALL);
		return pegDownProcessor.parseMarkdown(text.toCharArray());
	}

	private void applyHighlighting(RootNode astRoot) {
		MarkdownSyntaxHighlighter.highlight(textArea, astRoot);
	}

	public void surroundSelection(String leading, String trailing) {
		// Note: not using textArea.insertText() to insert leading and trailing
		//       because this would add two changes to undo history

		IndexRange selection = textArea.getSelection();
		int start = selection.getStart();
		int end = selection.getEnd();

		String selectedText = textArea.getSelectedText();

		// remove leading and trailing whitespaces from selected text
		String trimmedSelectedText = selectedText.trim();
		if (trimmedSelectedText.length() < selectedText.length()) {
			start += selectedText.indexOf(trimmedSelectedText);
			end = start + trimmedSelectedText.length();
		}

		textArea.replaceText(start, end, leading + trimmedSelectedText + trailing);
		textArea.selectRange(start + leading.length(), end + leading.length());
	}
}