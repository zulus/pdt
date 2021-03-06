/*******************************************************************************
 * Copyright (c) 2009, 2016, 2017 IBM Corporation and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Zend Technologies
 *******************************************************************************/
package org.eclipse.php.internal.ui.editor;

import java.io.IOException;
import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.util.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.dltk.core.*;
import org.eclipse.dltk.internal.ui.actions.CompositeActionGroup;
import org.eclipse.dltk.internal.ui.editor.*;
import org.eclipse.dltk.internal.ui.text.ScriptWordFinder;
import org.eclipse.dltk.ui.DLTKUIPlugin;
import org.eclipse.dltk.ui.actions.IScriptEditorActionDefinitionIds;
import org.eclipse.dltk.ui.text.folding.IFoldingStructureProvider;
import org.eclipse.dltk.ui.text.folding.IFoldingStructureProviderExtension;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.jface.action.*;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.*;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.link.*;
import org.eclipse.jface.text.link.LinkedModeUI.ExitFlags;
import org.eclipse.jface.text.link.LinkedModeUI.IExitPolicy;
import org.eclipse.jface.text.source.*;
import org.eclipse.jface.text.source.ImageUtilities;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.php.core.PHPToolkitUtil;
import org.eclipse.php.core.ast.nodes.*;
import org.eclipse.php.internal.core.PHPCoreConstants;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.ast.locator.PHPElementConciliator;
import org.eclipse.php.internal.core.corext.dom.NodeFinder;
import org.eclipse.php.internal.core.documentModel.dom.IImplForPHP;
import org.eclipse.php.internal.core.documentModel.parser.PHPSourceParser;
import org.eclipse.php.internal.core.documentModel.partitioner.PHPPartitionTypes;
import org.eclipse.php.internal.core.format.FormatterUtils;
import org.eclipse.php.internal.core.format.PHPHeuristicScanner;
import org.eclipse.php.internal.core.format.Symbols;
import org.eclipse.php.internal.core.preferences.IPreferencesPropagatorListener;
import org.eclipse.php.internal.core.preferences.PreferencePropagatorFactory;
import org.eclipse.php.internal.core.preferences.PreferencesPropagator;
import org.eclipse.php.internal.core.preferences.PreferencesPropagatorEvent;
import org.eclipse.php.internal.core.project.PHPVersionChangedHandler;
import org.eclipse.php.internal.core.search.IOccurrencesFinder;
import org.eclipse.php.internal.core.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.php.internal.core.search.OccurrencesFinderFactory;
import org.eclipse.php.internal.ui.*;
import org.eclipse.php.internal.ui.actions.*;
import org.eclipse.php.internal.ui.actions.GotoMatchingBracketAction;
import org.eclipse.php.internal.ui.autoEdit.TabAutoEditStrategy;
import org.eclipse.php.internal.ui.autoEdit.TypingPreferences;
import org.eclipse.php.internal.ui.editor.configuration.PHPStructuredTextViewerConfiguration;
import org.eclipse.php.internal.ui.folding.IStructuredTextFoldingProvider;
import org.eclipse.php.internal.ui.folding.PHPFoldingStructureProviderProxy;
import org.eclipse.php.internal.ui.preferences.PreferenceConstants;
import org.eclipse.php.internal.ui.text.DocumentCharacterIterator;
import org.eclipse.php.internal.ui.text.PHPWordIterator;
import org.eclipse.php.internal.ui.util.DocumentModelUtils;
import org.eclipse.php.internal.ui.util.PHPPluginImages;
import org.eclipse.php.internal.ui.viewsupport.ISelectionListenerWithAST;
import org.eclipse.php.internal.ui.viewsupport.SelectionListenerWithASTManager;
import org.eclipse.php.ui.editor.SharedASTProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.*;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.ui.StructuredTextEditor;
import org.eclipse.wst.sse.ui.internal.IStructuredTextEditorActionConstants;
import org.eclipse.wst.sse.ui.internal.SSEUIPlugin;
import org.eclipse.wst.sse.ui.internal.StorageModelProvider;
import org.eclipse.wst.sse.ui.internal.StructuredTextViewer;
import org.eclipse.wst.sse.ui.internal.actions.ActionDefinitionIds;
import org.eclipse.wst.sse.ui.internal.contentassist.StructuredContentAssistant;
import org.eclipse.wst.sse.ui.internal.contentoutline.ConfigurableContentOutlinePage;
import org.eclipse.wst.sse.ui.internal.reconcile.ReconcileAnnotationKey;
import org.eclipse.wst.sse.ui.internal.reconcile.TemporaryAnnotation;

public class PHPStructuredEditor extends StructuredTextEditor {

	private static final String ORG_ECLIPSE_PHP_UI_ACTIONS_OPEN_FUNCTIONS_MANUAL_ACTION = "org.eclipse.php.ui.actions.OpenFunctionsManualAction"; //$NON-NLS-1$
	private static final String FORMATTER_PLUGIN_ID = "org.eclipse.php.formatter.core"; //$NON-NLS-1$

	private IContentOutlinePage fPHPOutlinePage;
	protected PHPPairMatcher fBracketMatcher = new PHPPairMatcher(BRACKETS);
	private CompositeActionGroup fContextMenuGroup;
	private CompositeActionGroup fActionGroups;

	private long fLastActionsUpdate = 0L;

	/** Indicates whether the structure editor is displaying an external file */
	protected boolean isExternal = false;

	/** The editor's save policy */
	protected ISavePolicy fSavePolicy = null;

	/**
	 * The internal shell activation listener for updating occurrences.
	 * 
	 * @since 3.4
	 */
	private ISelectionListenerWithAST fPostSelectionListenerWithAST;
	private OccurrencesFinderJob fOccurrencesFinderJob;
	/** The occurrences finder job canceler */
	private OccurrencesFinderJobCanceler fOccurrencesFinderJobCanceler;

	/**
	 * The cached selected range.
	 * 
	 * @see ITextViewer#getSelectedRange()
	 * @since 3.3
	 */
	private Point fCachedSelectedRange;

	/**
	 * The selection used when forcing occurrence marking through code.
	 * 
	 * @since 3.4
	 */
	private ISelection fForcedMarkOccurrencesSelection;
	/**
	 * The document modification stamp at the time when the last occurrence
	 * marking took place.
	 * 
	 * @since 3.4
	 */
	private long fMarkOccurrenceModificationStamp = IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
	/**
	 * The region of the word under the caret used to when computing the current
	 * occurrence markings.
	 * 
	 * @since 3.4
	 */
	private IRegion fMarkOccurrenceTargetRegion;

	/**
	 * Holds the current occurrence annotations.
	 * 
	 * @since 3.4
	 */
	private Annotation[] fOccurrenceAnnotations = null;
	/**
	 * Tells whether all occurrences of the element at the current caret
	 * location are automatically marked in this editor.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkOccurrenceAnnotations;
	/**
	 * Tells whether the occurrence annotations are sticky i.e. whether they
	 * stay even if there's no valid Java element at the current caret position.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fStickyOccurrenceAnnotations;
	/**
	 * Tells whether to mark type occurrences in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkTypeOccurrences;
	/**
	 * Tells whether to mark method and declaration occurrences in this editor.
	 * Only valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkMethodOccurrences;
	/**
	 * Tells whether to mark function occurrences in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkFunctionOccurrences;
	/**
	 * Tells whether to mark constant occurrences in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkConstantOccurrences;
	/**
	 * Tells whether to mark field global variable in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkGlobalVariableOccurrences;
	/**
	 * Tells whether to mark local variable occurrences in this editor. Only
	 * valid if {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkLocalVariableOccurrences;
	/**
	 * Tells whether to mark exception occurrences in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkExceptions;
	/**
	 * Tells whether to mark method exits in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkMethodExitPoints;

	/**
	 * Tells whether to mark targets of <code>break</code> and
	 * <code>continue</code> statements in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkBreakContinueTargets;

	/**
	 * Tells whether to mark implementors in this editor. Only valid if
	 * {@link #fMarkOccurrenceAnnotations} is <code>true</code>.
	 * 
	 * @since 3.4
	 */
	private boolean fMarkImplementors;

	/**
	 * The override and implements indicator manager for this editor.
	 * 
	 * @since 3.0
	 */
	protected OverrideIndicatorManager fOverrideIndicatorManager;

	/**
	 * we use this for updating the code folding listeners and other things
	 */
	private PHPFoldingStructureProviderProxy fProjectionModelUpdater;

	/**
	 * mark if we have installed the projectionSupport.
	 */
	private boolean projectionSupportInstalled = false;;

	private PHPEditorErrorTickUpdater fPHPEditorErrorTickUpdater;
	/** The bracket inserter. */
	private final BracketInserter fBracketInserter = new BracketInserter();

	private class ExitPolicy implements IExitPolicy {

		final char fExitCharacter;
		final char fEscapeCharacter;
		final Stack<BracketLevel> fStack;
		final int fSize;

		public ExitPolicy(char exitCharacter, char escapeCharacter, Stack<BracketLevel> stack) {
			fExitCharacter = exitCharacter;
			fEscapeCharacter = escapeCharacter;
			fStack = stack;
			fSize = fStack.size();
		}

		@Override
		public ExitFlags doExit(LinkedModeModel model, VerifyEvent event, int offset, int length) {

			if (fSize == fStack.size() && !isMasked(offset)) {
				if (event.character == fExitCharacter) {
					BracketLevel level = fStack.peek();
					if (level.fFirstPosition.offset > offset || level.fSecondPosition.offset < offset) {
						return null;
					}
					if (level.fSecondPosition.offset == offset && length == 0) {
						// don't enter the character if if its the closing peer
						return new ExitFlags(ILinkedModeListener.UPDATE_CARET, false);
					}
				}
				// when entering an anonymous class between the parenthesis', we
				// don't want
				// to jump after the closing parenthesis when return is pressed
				if (event.character == SWT.CR && offset > 0) {
					ISourceViewer sourceViewer = getSourceViewer();
					IDocument document = sourceViewer.getDocument();
					try {
						if (document.getChar(offset - 1) == '{') {
							return new ExitFlags(ILinkedModeListener.EXIT_ALL, true);
						}

						// see bug 308217: while overriding a method and using
						// '(' followed by parameter type to filter the content
						// assist proposals, if ')' is added
						// automatically on typing '(', pressing return key
						// should not result in jumping after ')' instead of
						// applying the selected proposal.
						// if (document.getChar(offset) == ')' && sourceViewer
						// instanceof AdaptedSourceViewer &&
						// ((AdaptedSourceViewer)
						// sourceViewer).getContentAssistant() instanceof
						// ContentAssistant) {
						// ContentAssistant contentAssistant= (ContentAssistant)
						// ((AdaptedSourceViewer)
						// sourceViewer).getContentAssistant();
						// IContentAssistProcessor processor=
						// contentAssistant.getContentAssistProcessor(IDocument.DEFAULT_CONTENT_TYPE);
						// if (processor instanceof ContentAssistProcessor) {
						// ICompletionProposal proposal=
						// ((ContentAssistProcessor)
						// processor).getSelectedProposal();
						// if (proposal instanceof OverrideCompletionProposal) {
						// return new ExitFlags(ILinkedModeListener.EXIT_ALL,
						// true);
						// }
						// }
						// }
					} catch (BadLocationException e) {
					}
				}
			}
			return null;
		}

		private boolean isMasked(int offset) {
			IDocument document = getSourceViewer().getDocument();
			try {
				return fEscapeCharacter == document.getChar(offset - 1);
			} catch (BadLocationException e) {
			}
			return false;
		}
	}

	private static class BracketLevel {
		LinkedModeUI fUI;
		Position fFirstPosition;
		Position fSecondPosition;
	}

	/**
	 * Position updater that takes any changes at the borders of a position to
	 * not belong to the position.
	 *
	 * @since 3.0
	 */
	private static class ExclusivePositionUpdater implements IPositionUpdater {

		/** The position category. */
		private final String fCategory;

		/**
		 * Creates a new updater for the given <code>category</code>.
		 *
		 * @param category
		 *            the new category.
		 */
		public ExclusivePositionUpdater(String category) {
			fCategory = category;
		}

		@Override
		public void update(DocumentEvent event) {

			int eventOffset = event.getOffset();
			int eventOldLength = event.getLength();
			int eventNewLength = event.getText() == null ? 0 : event.getText().length();
			int deltaLength = eventNewLength - eventOldLength;

			try {
				Position[] positions = event.getDocument().getPositions(fCategory);

				for (int i = 0; i != positions.length; i++) {

					Position position = positions[i];

					if (position.isDeleted()) {
						continue;
					}

					int offset = position.getOffset();
					int length = position.getLength();
					int end = offset + length;

					if (offset >= eventOffset + eventOldLength) {
						// position comes
						// after change - shift
						position.setOffset(offset + deltaLength);
					} else if (end <= eventOffset) {
						// position comes way before change -
						// leave alone
					} else if (offset <= eventOffset && end >= eventOffset + eventOldLength) {
						// event completely internal to the position - adjust
						// length
						position.setLength(length + deltaLength);
					} else if (offset < eventOffset) {
						// event extends over end of position - adjust length
						int newEnd = eventOffset;
						position.setLength(newEnd - offset);
					} else if (end > eventOffset + eventOldLength) {
						// event extends from before position into it - adjust
						// offset
						// and length
						// offset becomes end of event, length adjusted
						// accordingly
						int newOffset = eventOffset + eventNewLength;
						position.setOffset(newOffset);
						position.setLength(end - newOffset);
					} else {
						// event consumes the position - delete it
						position.delete();
					}
				}
			} catch (BadPositionCategoryException e) {
				// ignore and return
			}
		}

	}

	private class BracketInserter implements VerifyKeyListener, ILinkedModeListener {

		private boolean fCloseBrackets = true;
		private boolean fCloseStrings = true;
		private final String CATEGORY = toString();
		private final IPositionUpdater fUpdater = new ExclusivePositionUpdater(CATEGORY);
		private final Stack<BracketLevel> fBracketLevelStack = new Stack<>();

		public void setCloseBracketsEnabled(boolean enabled) {
			fCloseBrackets = enabled;
		}

		public void setCloseStringsEnabled(boolean enabled) {
			fCloseStrings = enabled;
		}

		private boolean isMultilineSelection() {
			ISelection selection = getSelectionProvider().getSelection();
			if (selection instanceof ITextSelection) {
				ITextSelection ts = (ITextSelection) selection;
				return ts.getStartLine() != ts.getEndLine();
			}
			return false;
		}

		@Override
		public void verifyKey(VerifyEvent event) {

			// early pruning to slow down normal typing as little as possible
			if (!event.doit || getInsertMode() != SMART_INSERT
					|| isBlockSelectionModeEnabled() && isMultilineSelection()) {
				return;
			}
			switch (event.character) {
			case '(':
			case '[':
			case '\'':
			case '\"':
			case '`':
				break;
			default:
				return;
			}

			final ISourceViewer sourceViewer = getSourceViewer();
			IDocument document = sourceViewer.getDocument();

			final Point selection = sourceViewer.getSelectedRange();
			final int offset = selection.x;
			final int length = selection.y;

			try {
				IRegion startLine = document.getLineInformationOfOffset(offset);
				IRegion endLine = document.getLineInformationOfOffset(offset + length);

				PHPHeuristicScanner scanner = PHPHeuristicScanner.createHeuristicScanner(document, offset, true);
				int nextToken = scanner.nextToken(offset + length, endLine.getOffset() + endLine.getLength());
				String next = nextToken == Symbols.TokenEOF ? null
						: document.get(offset, scanner.getPosition() - offset).trim();
				int prevToken = scanner.previousToken(offset - 1, startLine.getOffset() - 1);
				int prevTokenOffset = scanner.getPosition() + 1;
				String previous = prevToken == Symbols.TokenEOF ? null
						: document.get(prevTokenOffset, offset - prevTokenOffset).trim();

				switch (event.character) {
				case '(':
					if (!fCloseBrackets || nextToken == Symbols.TokenLPAREN || nextToken == Symbols.TokenIDENT
							|| next != null && next.length() > 1) {
						return;
					}
					break;
				case '[':
					if (!fCloseBrackets || nextToken == Symbols.TokenIDENT || next != null && next.length() > 1) {
						return;
					}
					break;

				case '\'':
				case '"':
				case '`':
					if (!fCloseStrings || nextToken == Symbols.TokenIDENT || prevToken == Symbols.TokenIDENT
							|| next != null && next.length() > 1 || previous != null && previous.length() > 1) {
						return;
					}
					break;

				default:
					return;
				}

				if (!(document instanceof IStructuredDocument)) {
					return;
				}
				String partitionType = FormatterUtils.getPartitionType((IStructuredDocument) document, offset);
				if (!PHPPartitionTypes.PHP_DEFAULT.equals(partitionType)) {
					return;
				}

				if (!validateEditorInputState()) {
					return;
				}

				final char character = event.character;
				final char closingCharacter = getPeerCharacter(character);
				final StringBuilder buffer = new StringBuilder();
				buffer.append(character);
				buffer.append(closingCharacter);

				document.replace(offset, length, buffer.toString());

				BracketLevel level = new BracketLevel();
				fBracketLevelStack.push(level);

				LinkedPositionGroup group = new LinkedPositionGroup();
				group.addPosition(new LinkedPosition(document, offset + 1, 0, LinkedPositionGroup.NO_STOP));

				LinkedModeModel model = new LinkedModeModel();
				model.addLinkingListener(this);
				model.addGroup(group);
				model.forceInstall();

				// set up position tracking for our magic peers
				if (fBracketLevelStack.size() == 1) {
					document.addPositionCategory(CATEGORY);
					document.addPositionUpdater(fUpdater);
				}
				level.fFirstPosition = new Position(offset, 1);
				level.fSecondPosition = new Position(offset + 1, 1);
				document.addPosition(CATEGORY, level.fFirstPosition);
				document.addPosition(CATEGORY, level.fSecondPosition);

				level.fUI = new EditorLinkedModeUI(model, sourceViewer);
				level.fUI.setSimpleMode(true);
				level.fUI.setExitPolicy(
						new ExitPolicy(closingCharacter, getEscapeCharacter(closingCharacter), fBracketLevelStack));
				level.fUI.setExitPosition(sourceViewer, offset + 2, 0, Integer.MAX_VALUE);
				level.fUI.setCyclingMode(LinkedModeUI.CYCLE_NEVER);
				level.fUI.enter();

				IRegion newSelection = level.fUI.getSelectedRegion();
				sourceViewer.setSelectedRange(newSelection.getOffset(), newSelection.getLength());

				event.doit = false;

			} catch (BadLocationException e) {
				PHPUiPlugin.log(e);
			} catch (BadPositionCategoryException e) {
				PHPUiPlugin.log(e);
			}
		}

		@Override
		public void left(LinkedModeModel environment, int flags) {

			final BracketLevel level = fBracketLevelStack.pop();

			if (flags != ILinkedModeListener.EXTERNAL_MODIFICATION) {
				return;
			}

			// remove brackets
			final ISourceViewer sourceViewer = getSourceViewer();
			final IDocument document = sourceViewer.getDocument();
			if (document instanceof IDocumentExtension) {
				IDocumentExtension extension = (IDocumentExtension) document;
				extension.registerPostNotificationReplace(null, new IDocumentExtension.IReplace() {

					@Override
					public void perform(IDocument d, IDocumentListener owner) {
						if ((level.fFirstPosition.isDeleted || level.fFirstPosition.length == 0)
								&& !level.fSecondPosition.isDeleted
								&& level.fSecondPosition.offset == level.fFirstPosition.offset) {
							try {
								document.replace(level.fSecondPosition.offset, level.fSecondPosition.length, ""); //$NON-NLS-1$
							} catch (BadLocationException e) {
								PHPUiPlugin.log(e);
							}
						}

						if (fBracketLevelStack.size() == 0) {
							document.removePositionUpdater(fUpdater);
							try {
								document.removePositionCategory(CATEGORY);
							} catch (BadPositionCategoryException e) {
								PHPUiPlugin.log(e);
							}
						}
					}
				});
			}

		}

		@Override
		public void suspend(LinkedModeModel environment) {
		}

		@Override
		public void resume(LinkedModeModel environment, int flags) {
		}
	}

	/**
	 * The editor selection changed listener.
	 */
	private IPreferencesPropagatorListener fPhpVersionListener;
	private IPreferencesPropagatorListener fFormatterProfileListener;
	private IPreferenceChangeListener fPreferencesListener;

	private void doSelectionChanged(ISelection selection) {
		ISourceReference reference = null;
		Iterator<?> iter = ((IStructuredSelection) selection).iterator();
		while (iter.hasNext()) {
			Object o = iter.next();
			if (o instanceof ISourceReference) {
				reference = (ISourceReference) o;
				break;
			}
		}
		if (!isActivePart() && PHPUiPlugin.getActivePage() != null) {
			PHPUiPlugin.getActivePage().bringToTop(this);
		}
		setSelection(reference, !isActivePart());

	}

	protected void doSelectionChanged(SelectionChangedEvent event) {
		ISelection selection = event.getSelection();
		doSelectionChanged(selection);
	}

	protected void doSelectionChanged(DoubleClickEvent event) {
		ISelection selection = event.getSelection();
		doSelectionChanged(selection);
	}

	/**
	 * Cancels the occurrences finder job upon document changes.
	 * 
	 * @since 3.0
	 */
	class OccurrencesFinderJobCanceler implements IDocumentListener, ITextInputListener {

		public void install() {
			ISourceViewer sourceViewer = getSourceViewer();
			if (sourceViewer == null) {
				return;
			}

			StyledText text = sourceViewer.getTextWidget();
			if (text == null || text.isDisposed()) {
				return;
			}

			sourceViewer.addTextInputListener(this);

			IDocument document = sourceViewer.getDocument();
			if (document != null) {
				document.addDocumentListener(this);
			}
		}

		public void uninstall() {
			ISourceViewer sourceViewer = getSourceViewer();
			if (sourceViewer != null) {
				sourceViewer.removeTextInputListener(this);
			}

			IDocumentProvider documentProvider = getDocumentProvider();
			if (documentProvider != null) {
				IDocument document = documentProvider.getDocument(getEditorInput());
				if (document != null) {
					document.removeDocumentListener(this);
				}
			}
		}

		/*
		 * @see
		 * org.eclipse.jface.text.IDocumentListener#documentAboutToBeChanged
		 * (org.eclipse.jface.text.DocumentEvent)
		 */
		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			if (fOccurrencesFinderJob != null) {
				fOccurrencesFinderJob.doCancel();
			}
		}

		/*
		 * @see
		 * org.eclipse.jface.text.IDocumentListener#documentChanged(org.eclipse
		 * .jface.text.DocumentEvent)
		 */
		@Override
		public void documentChanged(DocumentEvent event) {
		}

		/*
		 * @see org.eclipse.jface.text.ITextInputListener#
		 * inputDocumentAboutToBeChanged (org.eclipse.jface.text.IDocument,
		 * org.eclipse.jface.text.IDocument)
		 */
		@Override
		public void inputDocumentAboutToBeChanged(IDocument oldInput, IDocument newInput) {
			if (oldInput == null) {
				return;
			}

			oldInput.removeDocumentListener(this);
		}

		/*
		 * @see
		 * org.eclipse.jface.text.ITextInputListener#inputDocumentChanged(org
		 * .eclipse.jface.text.IDocument, org.eclipse.jface.text.IDocument)
		 */
		@Override
		public void inputDocumentChanged(IDocument oldInput, IDocument newInput) {
			if (newInput == null) {
				return;
			}
			newInput.addDocumentListener(this);
		}
	}

	/**
	 * Finds and marks occurrence annotations.
	 * 
	 * @since 3.0
	 */
	class OccurrencesFinderJob extends Job {

		private final IDocument fDocument;
		private final ISelection fSelection;
		private final ISelectionValidator fPostSelectionValidator;
		private boolean fCanceled = false;
		private final OccurrenceLocation[] fLocations;

		public OccurrencesFinderJob(IDocument document, OccurrenceLocation[] locations, ISelection selection) {
			super("mark occurrences job name"); // TODO should //$NON-NLS-1$
												// externals
			fDocument = document;
			fSelection = selection;
			fLocations = locations;

			if (getSelectionProvider() instanceof ISelectionValidator) {
				fPostSelectionValidator = (ISelectionValidator) getSelectionProvider();
			} else {
				fPostSelectionValidator = null;
			}
		}

		// cannot use cancel() because it is declared final
		void doCancel() {
			fCanceled = true;
			cancel();
		}

		private boolean isCanceled(IProgressMonitor progressMonitor) {
			return fCanceled || progressMonitor.isCanceled() || fPostSelectionValidator != null
					&& !(fPostSelectionValidator.isValid(fSelection) || fForcedMarkOccurrencesSelection == fSelection)
					|| LinkedModeModel.hasInstalledModel(fDocument);
		}

		/*
		 * @see Job#run(org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public IStatus run(IProgressMonitor progressMonitor) {
			if (isCanceled(progressMonitor)) {
				return Status.CANCEL_STATUS;
			}

			ITextViewer textViewer = getTextViewer();
			if (textViewer == null) {
				return Status.CANCEL_STATUS;
			}

			IDocument document = textViewer.getDocument();
			if (document == null) {
				return Status.CANCEL_STATUS;
			}

			IDocumentProvider documentProvider = getDocumentProvider();
			if (documentProvider == null) {
				return Status.CANCEL_STATUS;
			}

			IAnnotationModel annotationModel = documentProvider.getAnnotationModel(getEditorInput());
			if (annotationModel == null) {
				return Status.CANCEL_STATUS;
			}

			// Add occurrence annotations
			int length = fLocations.length;
			Map<Annotation, Position> annotationMap = new HashMap<>(length);
			for (int i = 0; i < length; i++) {

				if (isCanceled(progressMonitor)) {
					return Status.CANCEL_STATUS;
				}

				OccurrenceLocation location = fLocations[i];
				Position position = new Position(location.getOffset(), location.getLength());

				String description = location.getDescription();
				String annotationType = (location.getFlags() == IOccurrencesFinder.F_WRITE_OCCURRENCE)
						? "org.eclipse.php.ui.occurrences.write" //$NON-NLS-1$
						: "org.eclipse.php.ui.occurrences"; //$NON-NLS-1$

				// create an annotation to mark the occurrence
				ReconcileAnnotationKey reconcileAnnotationKey = new ReconcileAnnotationKey(null,
						PHPPartitionTypes.PHP_DEFAULT, ReconcileAnnotationKey.TOTAL);
				TemporaryAnnotation annotation = new TemporaryAnnotation(position, annotationType, description,
						reconcileAnnotationKey) {

					// Supply an occurrence image to display in the vertical
					// ruler
					@Override
					public void paint(GC gc, Canvas canvas, Rectangle r) {
						ImageUtilities.drawImage(
								PHPUiPlugin.getImageDescriptorRegistry().get(PHPPluginImages.DESC_OBJS_OCCURRENCES), gc,
								canvas, r, SWT.CENTER, SWT.TOP);
					}

				};
				annotationMap.put(annotation, position);
			}

			if (isCanceled(progressMonitor)) {
				return Status.CANCEL_STATUS;
			}

			synchronized (getLockObject(annotationModel)) {
				if (annotationModel instanceof IAnnotationModelExtension) {
					((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations,
							annotationMap);
				} else {
					removeOccurrenceAnnotations();
					for (Map.Entry<Annotation, Position> entry : annotationMap.entrySet()) {
						annotationModel.addAnnotation(entry.getKey(), entry.getValue());
					}
				}
				fOccurrenceAnnotations = annotationMap.keySet().toArray(new Annotation[annotationMap.keySet().size()]);
			}

			return Status.OK_STATUS;
		}
	}

	private void initPHPVersionsListener() {
		if (fPhpVersionListener != null) {
			return;
		}

		fPhpVersionListener = new IPreferencesPropagatorListener() {
			@Override
			public void preferencesEventOccured(PreferencesPropagatorEvent event) {
				// get the structured document and go over its regions
				// in case of PhpScriptRegion reparse the region text
				StructuredTextViewer textViewer = getTextViewer();
				if (textViewer instanceof PHPStructuredTextViewer) {
					DocumentModelUtils.reparseAndReconcileDocument((PHPStructuredTextViewer) textViewer, getProject());
				} else {
					IDocumentProvider documentProvider = getDocumentProvider();
					IDocument doc = documentProvider != null ? documentProvider.getDocument(getEditorInput()) : null;
					DocumentModelUtils.reparseDocument(doc, getProject());
				}
			}

			@Override
			public IProject getProject() {
				IScriptProject scriptProject = PHPStructuredEditor.this.getProject();
				if (scriptProject != null) {
					return scriptProject.getProject();
				}
				return null;
			}
		};

		PHPVersionChangedHandler.getInstance().addPHPVersionChangedListener(fPhpVersionListener);

		fPreferencesListener = new IPreferenceChangeListener() {

			@Override
			public void preferenceChange(PreferenceChangeEvent event) {
				String property = event.getKey();
				if (PHPCoreConstants.CODEASSIST_AUTOACTIVATION.equals(property)
						|| PHPCoreConstants.CODEASSIST_AUTOACTIVATION_DELAY.equals(property)
						|| PHPCoreConstants.CODEASSIST_AUTOINSERT.equals(property)) {
					ISourceViewer sourceViewer = getSourceViewer();
					if (sourceViewer != null) {
						PHPStructuredTextViewerConfiguration configuration = (PHPStructuredTextViewerConfiguration) getSourceViewerConfiguration();
						if (configuration != null) {
							StructuredContentAssistant contentAssistant = (StructuredContentAssistant) configuration
									.getPHPContentAssistant(sourceViewer);

							IPreferencesService preferencesService = Platform.getPreferencesService();
							contentAssistant.enableAutoActivation(preferencesService.getBoolean(PHPCorePlugin.ID,
									PHPCoreConstants.CODEASSIST_AUTOACTIVATION, false, null));
							contentAssistant.setAutoActivationDelay(preferencesService.getInt(PHPCorePlugin.ID,
									PHPCoreConstants.CODEASSIST_AUTOACTIVATION_DELAY, 0, null));
							contentAssistant.enableAutoInsert(preferencesService.getBoolean(PHPCorePlugin.ID,
									PHPCoreConstants.CODEASSIST_AUTOINSERT, false, null));
						}
					}
				}
			}
		};

		InstanceScope.INSTANCE.getNode(PHPCorePlugin.ID).addPreferenceChangeListener(fPreferencesListener);
	}

	/** Cursor dependent actions. */
	private final List<String> fCursorActions = new ArrayList<>(5);

	public PHPStructuredEditor() {
		setDocumentProvider(DLTKUIPlugin.getDocumentProvider());
		fPHPEditorErrorTickUpdater = new PHPEditorErrorTickUpdater(this);
	}

	// added by zhaozw,or there will be a exception for files in the phar
	@Override
	protected void setDocumentProvider(IEditorInput input) {
		setDocumentProvider(DLTKUIPlugin.getDocumentProvider());
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		if (input instanceof IFileEditorInput) {
			// This is the existing workspace file
			final IFileEditorInput fileInput = (IFileEditorInput) input;
			input = new RefactorableFileEditorInput(fileInput.getFile());
		}
		super.init(site, input);
	}

	@Override
	protected void initializeEditor() {
		super.initializeEditor();

		IPreferenceStore store = createCombinedPreferenceStore();
		setPreferenceStore(store);

		fMarkOccurrenceAnnotations = store.getBoolean(PreferenceConstants.EDITOR_MARK_OCCURRENCES);
		fStickyOccurrenceAnnotations = store.getBoolean(PreferenceConstants.EDITOR_STICKY_OCCURRENCES);
		fMarkTypeOccurrences = store.getBoolean(PreferenceConstants.EDITOR_MARK_TYPE_OCCURRENCES);
		fMarkMethodOccurrences = store.getBoolean(PreferenceConstants.EDITOR_MARK_METHOD_OCCURRENCES);
		fMarkFunctionOccurrences = store.getBoolean(PreferenceConstants.EDITOR_MARK_FUNCTION_OCCURRENCES);
		fMarkConstantOccurrences = store.getBoolean(PreferenceConstants.EDITOR_MARK_CONSTANT_OCCURRENCES);
		fMarkGlobalVariableOccurrences = store.getBoolean(PreferenceConstants.EDITOR_MARK_GLOBAL_VARIABLE_OCCURRENCES);
		fMarkLocalVariableOccurrences = store.getBoolean(PreferenceConstants.EDITOR_MARK_LOCAL_VARIABLE_OCCURRENCES);
		fMarkImplementors = store.getBoolean(PreferenceConstants.EDITOR_MARK_IMPLEMENTORS);
		fMarkMethodExitPoints = store.getBoolean(PreferenceConstants.EDITOR_MARK_METHOD_EXIT_POINTS);
		fMarkBreakContinueTargets = store.getBoolean(PreferenceConstants.EDITOR_MARK_BREAK_CONTINUE_TARGETS);
	}

	/**
	 * Create a preference store that combines the source editor preferences
	 * with the base editor's preferences.
	 * 
	 * @return IPreferenceStore
	 */
	private IPreferenceStore createCombinedPreferenceStore() {
		IPreferenceStore sseEditorPrefs = SSEUIPlugin.getDefault().getPreferenceStore();
		IPreferenceStore baseEditorPrefs = EditorsUI.getPreferenceStore();
		IPreferenceStore phpEditorPrefs = PHPUiPlugin.getDefault().getPreferenceStore();
		return new ChainedPreferenceStore(new IPreferenceStore[] { sseEditorPrefs, baseEditorPrefs, phpEditorPrefs });
	}

	@Override
	public void dispose() {

		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer instanceof ITextViewerExtension) {
			((ITextViewerExtension) sourceViewer).removeVerifyKeyListener(fBracketInserter);
		}

		if (fContextMenuGroup != null) {
			fContextMenuGroup.dispose();
			fContextMenuGroup = null;
		}
		if (fActionGroups != null) {
			fActionGroups.dispose();
			fActionGroups = null;
		}
		if (fPhpVersionListener != null) {
			PHPVersionChangedHandler.getInstance().removePHPVersionChangedListener(fPhpVersionListener);
			fPhpVersionListener = null;
		}
		if (fFormatterProfileListener != null) {
			// workaround for bug 409116
			PreferencesPropagator propagator = PreferencePropagatorFactory.getPreferencePropagator(FORMATTER_PLUGIN_ID);
			propagator.removePropagatorListener(fFormatterProfileListener,
					"org.eclipse.php.formatter.core.formatter.tabulation.size"); //$NON-NLS-1$
			propagator.removePropagatorListener(fFormatterProfileListener, "formatterProfile"); //$NON-NLS-1$
			fFormatterProfileListener = null;
		}
		if (fPreferencesListener != null) {
			InstanceScope.INSTANCE.getNode(PHPCorePlugin.ID).removePreferenceChangeListener(fPreferencesListener);
		}

		// some things in the configuration need to clean
		// up after themselves
		uninstallOccurrencesFinder();
		uninstallOverrideIndicator();

		// remove the listener we added in createAction method

		if (getSelectionProvider() instanceof IPostSelectionProvider) {
			IPostSelectionProvider psp = (IPostSelectionProvider) getSelectionProvider();
			try {
				IAction action = getAction(IPHPEditorActionDefinitionIds.OPEN_TYPE_HIERARCHY);
				if (action instanceof ISelectionChangedListener) {
					psp.removePostSelectionChangedListener((ISelectionChangedListener) action);
				}
				action = getAction(IPHPEditorActionDefinitionIds.OPEN_CALL_HIERARCHY);
				if (action instanceof ISelectionChangedListener) {
					psp.removePostSelectionChangedListener((ISelectionChangedListener) action);
				}
			} catch (NullPointerException ex) {
				// NPE thrown by getAction in case when class has already been
				// disposed but dispose is called again.
			}
		}

		if (fProjectionModelUpdater != null) {
			fProjectionModelUpdater.uninstall();
			fProjectionModelUpdater = null;
		}

		if (fPHPEditorErrorTickUpdater != null) {
			fPHPEditorErrorTickUpdater.dispose();
			fPHPEditorErrorTickUpdater = null;
		}
		super.dispose();
	}

	/*
	 * @see AbstractTextEditor#editorContextMenuAboutToShow(IMenuManager)
	 */
	@Override
	public void editorContextMenuAboutToShow(IMenuManager menu) {
		super.editorContextMenuAboutToShow(menu);

		if (fContextMenuGroup != null) {
			ActionContext context = new ActionContext(getSelectionProvider().getSelection());
			fContextMenuGroup.setContext(context);
			fContextMenuGroup.fillContextMenu(menu);
			fContextMenuGroup.setContext(null);
		}
	}

	@Override
	protected void addSourceMenuActions(IMenuManager menu) {
		super.addSourceMenuActions(menu);
		IContributionItem find = menu.find(IStructuredTextEditorActionConstants.SOURCE_CONTEXT_MENU_ID);
		if (find instanceof MenuManager) {
			((MenuManager) find).setActionDefinitionId(PHPActionConstants.SOURCE_QUICK_MENU);
		}
	}

	@Override
	protected void addRefactorMenuActions(IMenuManager menu) {
		super.addRefactorMenuActions(menu);
		IContributionItem find = menu.find(IStructuredTextEditorActionConstants.REFACTOR_CONTEXT_MENU_ID);
		if (find instanceof MenuManager) {
			((MenuManager) find).setActionDefinitionId(PHPActionConstants.REFACTOR_QUICK_MENU);
		}
	}

	@Override
	protected void addContextMenuActions(final IMenuManager menu) {
		if (getSourceViewer().isEditable()) {
			final String openGroup = "group.open"; //$NON-NLS-1$
			menu.appendToGroup(ITextEditorActionConstants.GROUP_EDIT, new Separator(openGroup));
			IAction action = getAction(PHPStructuredEditor.ORG_ECLIPSE_PHP_UI_ACTIONS_OPEN_FUNCTIONS_MANUAL_ACTION);
			if (action != null) {
				menu.appendToGroup(openGroup, action);
			}
			action = getAction(IPHPEditorActionDefinitionIds.OPEN_DECLARATION);
			if (action != null) {
				menu.appendToGroup(openGroup, action);
			}
			action = getAction(IScriptEditorActionDefinitionIds.SHOW_OUTLINE);
			if (action != null) {
				menu.appendToGroup(openGroup, action);
			}
			action = getAction(IPHPEditorActionDefinitionIds.OPEN_TYPE_HIERARCHY);
			if (action != null) {
				menu.appendToGroup(openGroup, action);
			}
			action = getAction(IScriptEditorActionDefinitionIds.OPEN_HIERARCHY);
			if (action != null) {
				menu.appendToGroup(openGroup, action);
			}
			action = getAction(IPHPEditorActionDefinitionIds.OPEN_CALL_HIERARCHY);
			if (action != null) {
				menu.appendToGroup(openGroup, action);
			}
		}
	}

	@Override
	protected void createNavigationActions() {
		super.createNavigationActions();
		final StyledText textWidget = getSourceViewer().getTextWidget();

		IAction action = new SmartLineStartAction(textWidget, false);
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.LINE_START);
		setAction(ITextEditorActionDefinitionIds.LINE_START, action);

		action = new SmartLineStartAction(textWidget, true);
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_LINE_START);
		setAction(ITextEditorActionDefinitionIds.SELECT_LINE_START, action);

		action = new SmartLineEndAction(textWidget, false);
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.LINE_END);
		setAction(ITextEditorActionDefinitionIds.LINE_END, action);

		action = new SmartLineEndAction(textWidget, true);
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_LINE_END);
		setAction(ITextEditorActionDefinitionIds.SELECT_LINE_END, action);

		action = new NavigatePreviousSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.WORD_PREVIOUS);
		setAction(ITextEditorActionDefinitionIds.WORD_PREVIOUS, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.ARROW_LEFT, SWT.NULL);

		action = new NavigateNextSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.WORD_NEXT);
		setAction(ITextEditorActionDefinitionIds.WORD_NEXT, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.ARROW_RIGHT, SWT.NULL);

		action = new SelectPreviousSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_WORD_PREVIOUS);
		setAction(ITextEditorActionDefinitionIds.SELECT_WORD_PREVIOUS, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.SHIFT | SWT.ARROW_LEFT, SWT.NULL);

		action = new SelectNextSubWordAction();
		action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_WORD_NEXT);
		setAction(ITextEditorActionDefinitionIds.SELECT_WORD_NEXT, action);
		textWidget.setKeyBinding(SWT.CTRL | SWT.SHIFT | SWT.ARROW_RIGHT, SWT.NULL);
	}

	/**
	 * This action implements smart home. (Taken from JDT implementation)
	 * Instead of going to the start of a line it does the following: - if smart
	 * home/end is enabled and the caret is after the line's first
	 * non-whitespace then the caret is moved directly before it, taking PHPDoc
	 * and multi-line comments into account. - if the caret is before the line's
	 * first non-whitespace the caret is moved to the beginning of the line - if
	 * the caret is at the beginning of the line see first case.
	 */
	protected class SmartLineStartAction extends LineStartAction {

		private final boolean fDoSelect;

		/**
		 * Creates a new smart line start action
		 * 
		 * @param textWidget
		 *            the styled text widget
		 * @param doSelect
		 *            a boolean flag which tells if the text up to the beginning
		 *            of the line should be selected
		 */
		public SmartLineStartAction(final StyledText textWidget, final boolean doSelect) {
			super(textWidget, doSelect);
			fDoSelect = doSelect;
		}

		/*
		 * @seeorg.eclipse.ui.texteditor.AbstractTextEditor.LineStartAction#
		 * getLineStartPosition(java.lang.String, int, java.lang.String)
		 */
		@Override
		protected int getLineStartPosition(final IDocument document, final String line, final int length,
				final int offset) {

			String type = IDocument.DEFAULT_CONTENT_TYPE;
			try {
				type = TextUtilities.getContentType(document, PHPPartitionTypes.PHP_DEFAULT, offset, true);
			} catch (BadLocationException exception) {
				// Should not happen
			}

			int index = super.getLineStartPosition(document, line, length, offset);
			if (type.equals(PHPPartitionTypes.PHP_DOC) || type.equals(PHPPartitionTypes.PHP_MULTI_LINE_COMMENT)) {
				if (index < length - 1 && line.charAt(index) == '*' && line.charAt(index + 1) != '/') {
					do {
						++index;
					} while (index < length && Character.isWhitespace(line.charAt(index)));
				}
			} else {
				if (index < length - 1 && line.charAt(index) == '/' && line.charAt(index + 1) == '/') {
					index++;
					do {
						++index;
					} while (index < length && Character.isWhitespace(line.charAt(index)));
				}
			}
			return index;
		}

		private IPreferenceStore getPreferenceStore() {
			return PHPUiPlugin.getDefault().getPreferenceStore();
		}

		/*
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		@Override
		public void run() {
			boolean isSmartHomeEndEnabled = true;
			IPreferenceStore store = getPreferenceStore();
			if (store != null) {
				isSmartHomeEndEnabled = store.getBoolean(PreferenceConstants.USE_SMART_HOME_END);
			}

			ISourceViewer fSourceViewer = getSourceViewer();
			if (fSourceViewer == null) {
				return;
			}
			StyledText st = fSourceViewer.getTextWidget();
			if (st == null || st.isDisposed()) {
				return;
			}

			int caretOffset = st.getCaretOffset();
			int lineNumber = st.getLineAtOffset(caretOffset);
			int lineOffset = st.getOffsetAtLine(lineNumber);

			int lineLength;
			int caretOffsetInDocument;
			final IDocument document = fSourceViewer.getDocument();

			try {
				caretOffsetInDocument = widgetOffset2ModelOffset(fSourceViewer, caretOffset);
				lineLength = document.getLineInformationOfOffset(caretOffsetInDocument).getLength();
			} catch (BadLocationException ex) {
				return;
			}

			String line = ""; //$NON-NLS-1$
			if (lineLength > 0) {
				int end = lineOffset + lineLength - 1;
				end = Math.min(end, st.getCharCount() - 1);
				line = st.getText(lineOffset, end);
			}

			// Compute the line start offset
			int index = getLineStartPosition(document, line, lineLength, caretOffsetInDocument);

			// Remember current selection
			Point oldSelection = st.getSelection();

			// Compute new caret position
			int newCaretOffset = -1;
			if (isSmartHomeEndEnabled) {

				if (caretOffset - lineOffset == index) {
					// to beginning of line
					newCaretOffset = lineOffset;
				} else {
					// to beginning of text
					newCaretOffset = lineOffset + index;
				}

			} else {

				if (caretOffset > lineOffset) {
					// to beginning of line
					newCaretOffset = lineOffset;
				}
			}

			if (newCaretOffset == -1) {
				newCaretOffset = caretOffset;
			} else {
				st.setCaretOffset(newCaretOffset);
			}

			if (fDoSelect) {
				if (caretOffset < oldSelection.y) {
					st.setSelection(oldSelection.y, newCaretOffset);
				} else {
					st.setSelection(oldSelection.x, newCaretOffset);
				}
			} else {
				st.setSelection(newCaretOffset);
			}

			fireSelectionChanged(oldSelection);
		}
	}

	/**
	 * This action implements smart end. (Taken from
	 * org.eclipse.ui.texteditor.AbstractTextEditor.LineEndAction) Instead of
	 * going to the end of a line it does the following: - if smart home/end is
	 * enabled and the caret is before the line's last non-whitespace and then
	 * the caret is moved directly after it - if the caret is after last
	 * non-whitespace the caret is moved at the end of the line - if the caret
	 * is at the end of the line the caret is moved directly after the line's
	 * last non-whitespace character
	 */
	protected class SmartLineEndAction extends TextNavigationAction {

		/**
		 * boolean flag which tells if the text up to the line end should be
		 * selected.
		 */
		private final boolean fDoSelect;

		/**
		 * Create a new line end action.
		 * 
		 * @param textWidget
		 *            the styled text widget
		 * @param doSelect
		 *            a boolean flag which tells if the text up to the line end
		 *            should be selected
		 */
		public SmartLineEndAction(StyledText textWidget, boolean doSelect) {
			super(textWidget, ST.LINE_END);
			fDoSelect = doSelect;
		}

		private IPreferenceStore getPreferenceStore() {
			return PHPUiPlugin.getDefault().getPreferenceStore();
		}

		/*
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		@Override
		public void run() {
			boolean isSmartHomeEndEnabled = true;
			IPreferenceStore store = getPreferenceStore();
			if (store != null) {
				isSmartHomeEndEnabled = store.getBoolean(PreferenceConstants.USE_SMART_HOME_END);
			}

			ISourceViewer fSourceViewer = getSourceViewer();
			if (fSourceViewer == null) {
				return;
			}
			StyledText st = fSourceViewer.getTextWidget();
			if (st == null || st.isDisposed()) {
				return;
			}

			int caretOffset = st.getCaretOffset();
			int lineNumber = st.getLineAtOffset(caretOffset);
			int lineOffset = st.getOffsetAtLine(lineNumber);

			int lineLength;
			try {
				int caretOffsetInDocument = widgetOffset2ModelOffset(fSourceViewer, caretOffset);
				lineLength = fSourceViewer.getDocument().getLineInformationOfOffset(caretOffsetInDocument).getLength();
			} catch (BadLocationException ex) {
				return;
			}
			int lineEndOffset = lineOffset + lineLength;

			int delta = lineEndOffset - st.getCharCount();
			if (delta > 0) {
				lineEndOffset -= delta;
				lineLength -= delta;
			}

			String line = ""; //$NON-NLS-1$
			if (lineLength > 0) {
				line = st.getText(lineOffset, lineEndOffset - 1);
			}
			int i = lineLength - 1;
			while (i > -1 && Character.isWhitespace(line.charAt(i))) {
				i--;
			}
			i++;

			// Remember current selection
			Point oldSelection = st.getSelection();

			// Compute new caret position
			int newCaretOffset = -1;

			if (isSmartHomeEndEnabled) {

				if (caretOffset - lineOffset == i) {
					// to end of line
					newCaretOffset = lineEndOffset;
				} else {
					// to end of text
					newCaretOffset = lineOffset + i;
				}

			} else {

				if (caretOffset < lineEndOffset) {
					// to end of line
					newCaretOffset = lineEndOffset;
				}

			}

			if (newCaretOffset == -1) {
				newCaretOffset = caretOffset;
			}

			int offsetInLine = newCaretOffset - lineOffset;
			int lineFullLength = st.getLine(lineNumber).length();
			while (offsetInLine > lineFullLength) {
				--newCaretOffset;
				--offsetInLine;
			}

			st.setCaretOffset(newCaretOffset);

			if (fDoSelect) {
				if (caretOffset < oldSelection.y) {
					st.setSelection(oldSelection.y, newCaretOffset);
				} else {
					st.setSelection(oldSelection.x, newCaretOffset);
				}
			} else {
				st.setSelection(newCaretOffset);
			}

			fireSelectionChanged(oldSelection);
		}
	}

	/**
	 * Text navigation action to navigate to the next sub-word.
	 */
	protected abstract class NextSubWordAction extends TextNavigationAction {

		protected PHPWordIterator fIterator = new PHPWordIterator();

		/**
		 * Creates a new next sub-word action.
		 * 
		 * @param code
		 *            Action code for the default operation. Must be an action
		 *            code from
		 * @see org.eclipse.swt.custom.ST.
		 */
		protected NextSubWordAction(int code) {
			super(getSourceViewer().getTextWidget(), code);
		}

		private IPreferenceStore getPreferenceStore() {
			return PHPUiPlugin.getDefault().getPreferenceStore();
		}

		/*
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		@Override
		public void run() {
			// Check whether the feature is enabled in Preferences
			final IPreferenceStore store = getPreferenceStore();
			if (!store.getBoolean(PreferenceConstants.USE_SUB_WORD_NAVIGATION)) {
				super.run();
				return;
			}

			final ISourceViewer viewer = getSourceViewer();
			final IDocument document = viewer.getDocument();
			fIterator.setText((CharacterIterator) new DocumentCharacterIterator(document));
			int position = widgetOffset2ModelOffset(viewer, viewer.getTextWidget().getCaretOffset());
			if (position == -1) {
				return;
			}

			int next = findNextPosition(position);
			if (next != BreakIterator.DONE) {
				setCaretPosition(next);
				getTextWidget().showSelection();
				fireSelectionChanged();
			}

		}

		/**
		 * Finds the next position after the given position.
		 * 
		 * @param position
		 *            the current position
		 * @return the next position
		 */
		protected int findNextPosition(int position) {
			ISourceViewer viewer = getSourceViewer();
			int widget = -1;
			while (position != BreakIterator.DONE && widget == -1) { // TODO:
				// optimize
				position = fIterator.following(position);
				if (position != BreakIterator.DONE) {
					widget = modelOffset2WidgetOffset(viewer, position);
				}
			}
			return position;
		}

		/**
		 * Sets the caret position to the sub-word boundary given with
		 * <code>position</code>.
		 * 
		 * @param position
		 *            Position where the action should move the caret
		 */
		protected abstract void setCaretPosition(int position);
	}

	/**
	 * Text operation action to select the next sub-word.
	 */
	protected class SelectNextSubWordAction extends NextSubWordAction {

		/**
		 * Creates a new select next sub-word action.
		 */
		public SelectNextSubWordAction() {
			super(ST.SELECT_WORD_NEXT);
		}

		/*
		 * @see NextSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			final ISourceViewer viewer = getSourceViewer();
			if (viewer == null) {
				return;
			}

			final StyledText text = viewer.getTextWidget();
			if (text != null && !text.isDisposed()) {

				final Point selection = text.getSelection();
				final int caret = text.getCaretOffset();
				final int offset = modelOffset2WidgetOffset(viewer, position);

				if (caret == selection.x) {
					text.setSelectionRange(selection.y, offset - selection.y);
				} else {
					text.setSelectionRange(selection.x, offset - selection.x);
				}
			}
		}
	}

	/**
	 * Text navigation action to navigate to the previous sub-word.
	 */
	protected abstract class PreviousSubWordAction extends TextNavigationAction {

		protected PHPWordIterator fIterator = new PHPWordIterator();

		/**
		 * Creates a new previous sub-word action.
		 * 
		 * @param code
		 *            Action code for the default operation. Must be an action
		 *            code from
		 * @see org.eclipse.swt.custom.ST.
		 */
		protected PreviousSubWordAction(final int code) {
			super(getSourceViewer().getTextWidget(), code);
		}

		private IPreferenceStore getPreferenceStore() {
			return PHPUiPlugin.getDefault().getPreferenceStore();
		}

		/*
		 * @see org.eclipse.jface.action.IAction#run()
		 */
		@Override
		public void run() {
			// Check whether we are in a java code partition and the preference
			// is enabled
			final IPreferenceStore store = getPreferenceStore();
			if (!store.getBoolean(PreferenceConstants.USE_SUB_WORD_NAVIGATION)) {
				super.run();
				return;
			}

			final ISourceViewer viewer = getSourceViewer();
			final IDocument document = viewer.getDocument();
			fIterator.setText((CharacterIterator) new DocumentCharacterIterator(document));
			int position = widgetOffset2ModelOffset(viewer, viewer.getTextWidget().getCaretOffset());
			if (position == -1) {
				return;
			}

			int previous = findPreviousPosition(position);
			if (previous != BreakIterator.DONE) {
				setCaretPosition(previous);
				getTextWidget().showSelection();
				fireSelectionChanged();
			}

		}

		/**
		 * Finds the previous position before the given position.
		 * 
		 * @param position
		 *            the current position
		 * @return the previous position
		 */
		protected int findPreviousPosition(int position) {
			ISourceViewer viewer = getSourceViewer();
			int widget = -1;
			while (position != BreakIterator.DONE && widget == -1) { // TODO:
				// optimize
				position = fIterator.preceding(position);
				if (position != BreakIterator.DONE) {
					widget = modelOffset2WidgetOffset(viewer, position);
				}
			}
			return position;
		}

		/**
		 * Sets the caret position to the sub-word boundary given with
		 * <code>position</code>.
		 * 
		 * @param position
		 *            Position where the action should move the caret
		 */
		protected abstract void setCaretPosition(int position);
	}

	/**
	 * Text navigation action to navigate to the next sub-word.
	 * 
	 * @since 3.0
	 */
	protected class NavigateNextSubWordAction extends NextSubWordAction {

		/**
		 * Creates a new navigate next sub-word action.
		 */
		public NavigateNextSubWordAction() {
			super(ST.WORD_NEXT);
		}

		/*
		 * @see NextSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			getTextWidget().setCaretOffset(modelOffset2WidgetOffset(getSourceViewer(), position));
		}
	}

	/**
	 * Text operation action to select the previous sub-word.
	 * 
	 * @since 3.0
	 */
	protected class SelectPreviousSubWordAction extends PreviousSubWordAction {

		/**
		 * Creates a new select previous sub-word action.
		 */
		public SelectPreviousSubWordAction() {
			super(ST.SELECT_WORD_PREVIOUS);
		}

		/*
		 * @see PreviousSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			final ISourceViewer viewer = getSourceViewer();
			if (viewer == null) {
				return;
			}

			final StyledText text = viewer.getTextWidget();
			if (text != null && !text.isDisposed()) {

				final Point selection = text.getSelection();
				final int caret = text.getCaretOffset();
				final int offset = modelOffset2WidgetOffset(viewer, position);

				if (caret == selection.x) {
					text.setSelectionRange(selection.y, offset - selection.y);
				} else {
					text.setSelectionRange(selection.x, offset - selection.x);
				}
			}
		}
	}

	/**
	 * Text navigation action to navigate to the previous sub-word.
	 * 
	 * @since 3.0
	 */
	protected class NavigatePreviousSubWordAction extends PreviousSubWordAction {

		/**
		 * Creates a new navigate previous sub-word action.
		 */
		public NavigatePreviousSubWordAction() {
			super(ST.WORD_PREVIOUS);
		}

		/*
		 * @see PreviousSubWordAction#setCaretPosition(int)
		 */
		@Override
		protected void setCaretPosition(final int position) {
			getTextWidget().setCaretOffset(modelOffset2WidgetOffset(getSourceViewer(), position));
		}
	}

	@Override
	protected void createActions() {
		super.createActions();
		final ResourceBundle resourceBundle = PHPUIMessages.getResourceBundle();

		Action action = new GotoMatchingBracketAction(this);
		action.setActionDefinitionId(IPHPEditorActionDefinitionIds.GOTO_MATCHING_BRACKET);
		setAction(GotoMatchingBracketAction.GOTO_MATCHING_BRACKET, action);

		action = new OpenFunctionsManualAction(resourceBundle, this);
		action.setActionDefinitionId(IPHPEditorActionDefinitionIds.OPEN_PHP_MANUAL);
		setAction(ORG_ECLIPSE_PHP_UI_ACTIONS_OPEN_FUNCTIONS_MANUAL_ACTION, action);
		markAsCursorDependentAction(ORG_ECLIPSE_PHP_UI_ACTIONS_OPEN_FUNCTIONS_MANUAL_ACTION, true);

		action = new OpenDeclarationAction(resourceBundle, this);
		action.setActionDefinitionId(IPHPEditorActionDefinitionIds.OPEN_DECLARATION);
		setAction(IPHPEditorActionDefinitionIds.OPEN_DECLARATION, action);
		markAsCursorDependentAction(IPHPEditorActionDefinitionIds.OPEN_DECLARATION, true);

		action = new OpenTypeHierarchyAction(this);
		action.setActionDefinitionId(IPHPEditorActionDefinitionIds.OPEN_TYPE_HIERARCHY);
		setAction(IPHPEditorActionDefinitionIds.OPEN_TYPE_HIERARCHY, action);
		markAsCursorDependentAction(IPHPEditorActionDefinitionIds.OPEN_TYPE_HIERARCHY, true);
		// add selection changed listener for updating enabled status
		if (getSelectionProvider() instanceof IPostSelectionProvider) {
			IPostSelectionProvider psp = (IPostSelectionProvider) getSelectionProvider();
			psp.addPostSelectionChangedListener((OpenTypeHierarchyAction) action);
		}

		action = new OpenCallHierarchyAction(this);
		action.setActionDefinitionId(IPHPEditorActionDefinitionIds.OPEN_CALL_HIERARCHY);
		setAction(IPHPEditorActionDefinitionIds.OPEN_CALL_HIERARCHY, action);
		markAsCursorDependentAction(IPHPEditorActionDefinitionIds.OPEN_CALL_HIERARCHY, true);
		// add selection changed listener for updating enabled status
		if (getSelectionProvider() instanceof IPostSelectionProvider) {
			IPostSelectionProvider psp = (IPostSelectionProvider) getSelectionProvider();
			psp.addPostSelectionChangedListener((OpenCallHierarchyAction) action);
		}

		setAction(PHPActionConstants.SOURCE_QUICK_MENU, new PHPSourceQuickMenuAction(this));
		setAction(PHPActionConstants.REFACTOR_QUICK_MENU, new PHPRefactorQuickMenuAction(this));

		action = new TextOperationAction(DLTKEditorMessages.getBundleForConstructedKeys(), "OpenHierarchy.", this, //$NON-NLS-1$
				PHPStructuredTextViewer.SHOW_HIERARCHY, true);
		action.setActionDefinitionId(IScriptEditorActionDefinitionIds.OPEN_HIERARCHY);
		setAction(IScriptEditorActionDefinitionIds.OPEN_HIERARCHY, action);
		markAsCursorDependentAction(IScriptEditorActionDefinitionIds.OPEN_HIERARCHY, true);

		ResourceAction resAction = null;

		resAction = new TextOperationAction(DLTKEditorMessages.getBundleForConstructedKeys(), "ShowOutline.", this, //$NON-NLS-1$
				PHPStructuredTextViewer.SHOW_OUTLINE, true);
		resAction.setActionDefinitionId(IScriptEditorActionDefinitionIds.SHOW_OUTLINE);
		setAction(IScriptEditorActionDefinitionIds.SHOW_OUTLINE, resAction);

		if (isExternal) {
			// Override the way breakpoints are set on external files.
			action = new ToggleExternalBreakpointAction(this, getVerticalRuler());
			setAction(ActionDefinitionIds.TOGGLE_BREAKPOINTS, action);
			// StructuredTextEditor Action - manage breakpoints
			action = new ManageExternalBreakpointAction(this, getVerticalRuler());
			setAction(ActionDefinitionIds.MANAGE_BREAKPOINTS, action);
			// StructuredTextEditor Action - edit breakpoints
			action = new EditExternalBreakpointAction(this, getVerticalRuler());
			setAction(ActionDefinitionIds.EDIT_BREAKPOINTS, action);

			// Set the ruler double-click behavior.
			setAction(ITextEditorActionConstants.RULER_DOUBLE_CLICK,
					new ToggleExternalBreakpointAction(this, getVerticalRuler(), null));
		}

		// ActionGroup rg = new RefactorActionGroup(this,
		// ITextEditorActionConstants.GROUP_EDIT);
		ActionGroup jsg = new PHPSearchActionGroup(this);

		// We have to keep the context menu group separate to have better
		// control over positioning
		fActionGroups = new CompositeActionGroup(new ActionGroup[] { jsg });
		fContextMenuGroup = new CompositeActionGroup(new ActionGroup[] { jsg });
	}

	/**
	 * Returns the standard action group of this editor.
	 * 
	 * @return returns this editor's standard action group
	 */
	public ActionGroup getActionGroup() {
		return fActionGroups;
	}

	/**
	 * Jumps to the matching bracket.
	 */
	public void gotoMatchingBracket() {

		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer == null) {
			return;
		}
		IDocument document = sourceViewer.getDocument();
		if (document == null) {
			return;
		}

		IRegion selection = getSignedSelection(sourceViewer);

		int selectionLength = Math.abs(selection.getLength());
		if (selectionLength > 1) {
			setStatusLineErrorMessage(PHPUIMessages.GotoMatchingBracket_error_invalidSelection);
			sourceViewer.getTextWidget().getDisplay().beep();
			return;
		}

		// #26314
		int sourceCaretOffset = selection.getOffset() + selection.getLength();
		if (isSurroundedByBrackets(document, sourceCaretOffset)) {
			sourceCaretOffset -= selection.getLength();
		}

		IRegion region = fBracketMatcher.match(document, sourceCaretOffset);
		if (region == null) {
			setStatusLineErrorMessage(PHPUIMessages.GotoMatchingBracket_error_noMatchingBracket);
			sourceViewer.getTextWidget().getDisplay().beep();
			return;
		}

		int offset = region.getOffset();
		int length = region.getLength();

		if (length < 1) {
			return;
		}

		int anchor = fBracketMatcher.getAnchor();
		// http://dev.eclipse.org/bugs/show_bug.cgi?id=34195
		int targetOffset = ICharacterPairMatcher.RIGHT == anchor ? offset + 1 : offset + length;

		boolean visible = false;
		if (sourceViewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension = (ITextViewerExtension5) sourceViewer;
			visible = extension.modelOffset2WidgetOffset(targetOffset) > -1;
		} else {
			IRegion visibleRegion = sourceViewer.getVisibleRegion();
			// http://dev.eclipse.org/bugs/show_bug.cgi?id=34195
			visible = targetOffset >= visibleRegion.getOffset()
					&& targetOffset <= visibleRegion.getOffset() + visibleRegion.getLength();
		}

		if (!visible) {
			setStatusLineErrorMessage(PHPUIMessages.GotoMatchingBracket_error_bracketOutsideSelectedElement);
			sourceViewer.getTextWidget().getDisplay().beep();
			return;
		}

		if (selection.getLength() < 0) {
			targetOffset -= selection.getLength();
		}

		sourceViewer.setSelectedRange(targetOffset, selection.getLength());
		sourceViewer.revealRange(targetOffset, selection.getLength());
	}

	private static boolean isSurroundedByBrackets(IDocument document, int offset) {
		if (offset == 0 || offset == document.getLength()) {
			return false;
		}

		try {
			return isBracket(document.getChar(offset - 1)) && isBracket(document.getChar(offset));

		} catch (BadLocationException e) {
			return false;
		}
	}

	private static boolean isBracket(char character) {
		for (int i = 0; i != BRACKETS.length; ++i) {
			if (character == BRACKETS[i]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the signed current selection. The length will be negative if the
	 * resulting selection is right-to-left (RtoL).
	 * <p>
	 * The selection offset is model based.
	 * </p>
	 * 
	 * @param sourceViewer
	 *            the source viewer
	 * @return a region denoting the current signed selection, for a resulting
	 *         RtoL selections length is < 0
	 */
	protected IRegion getSignedSelection(ISourceViewer sourceViewer) {
		StyledText text = sourceViewer.getTextWidget();
		Point selection = text.getSelectionRange();

		if (text.getCaretOffset() == selection.x) {
			selection.x = selection.x + selection.y;
			selection.y = -selection.y;
		}

		selection.x = widgetOffset2ModelOffset(sourceViewer, selection.x);

		return new Region(selection.x, selection.y);
	}

	/**
	 * Return whether document folding should be enabled according to the
	 * preference store settings.
	 * 
	 * @return <code>true</code> if document folding should be enabled
	 */
	private boolean isFoldingEnabled() {
		IPreferenceStore store = getPreferenceStore();
		// check both preference store and vm argument
		return (store.getBoolean(PreferenceConstants.EDITOR_FOLDING_ENABLED));
	}

	/**
	 * install the old code folding instead of wtp's
	 */
	private void installProjectionSupport() {
		projectionSupportInstalled = true;
		ProjectionViewer projectionViewer = (ProjectionViewer) getSourceViewer();
		if (projectionViewer == null) {
			return;
		}

		fProjectionModelUpdater = new PHPFoldingStructureProviderProxy();
		if (fProjectionModelUpdater != null) {
			fProjectionModelUpdater.install(projectionViewer);
		}

		if (isFoldingEnabled()) {
			fProjectionModelUpdater.projectionEnabled();
		}
	}

	@Override
	public void createPartControl(final Composite parent) {
		super.createPartControl(parent);

		fBracketInserter.setCloseBracketsEnabled(TypingPreferences.closeBrackets);
		fBracketInserter.setCloseStringsEnabled(TypingPreferences.closeQuotes);
		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer instanceof ITextViewerExtension) {
			((ITextViewerExtension) sourceViewer).prependVerifyKeyListener(fBracketInserter);
		}

		// workaround for code folding
		if (isFoldingEnabled()) {
			installProjectionSupport();
		}
		// end
		if (isMarkingOccurrences()) {
			installOccurrencesFinder(true);
		}

		setHelpContextId(IPHPHelpContextIds.EDITOR_PREFERENCES);
	}

	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		IFile resource = null;
		isExternal = false;

		if (input instanceof IFileEditorInput) {
			// This is the existing workspace file
			final IFileEditorInput fileInput = (IFileEditorInput) input;
			resource = fileInput.getFile();
			if (getRefactorableFileEditorInput() != null && getRefactorableFileEditorInput().isRefactor()) {
				getRefactorableFileEditorInput().setRefactor(false);
				if (getDocumentProvider() != null) {
					getDocumentProvider().disconnect(getRefactorableFileEditorInput());
				}
				getRefactorableFileEditorInput().setFile(fileInput.getFile());
				input = getRefactorableFileEditorInput();
			} else {
				input = new RefactorableFileEditorInput(fileInput.getFile());
			}

		}

		if (resource != null) {
			if (PHPToolkitUtil.isPHPFile(resource)) {

				PHPSourceParser.projectThreadLocal.set(resource.getProject());

				super.doSetInput(input);

				// At this point new document content was set, maybe even
				// getProject() value was updated (for example when document
				// was moved from one PHP project to another).
				initPHPVersionsListener();

			} else {
				super.doSetInput(input);
			}
		} else {
			isExternal = true;
			super.doSetInput(input);
		}

		ImageDescriptor imageDescriptor = input.getImageDescriptor();
		if (imageDescriptor != null) {
			setTitleImage(JFaceResources.getResources().createImageWithDefault(imageDescriptor));
		}
		if (isShowingOverrideIndicators()) {
			installOverrideIndicator(true);
		}

		if (fProjectionModelUpdater != null) {
			updateProjectionSupport();
		}

		if (fPHPEditorErrorTickUpdater != null) {
			fPHPEditorErrorTickUpdater.updateEditorImage(getModelElement());
		}
	}

	/**
	 * Install everything necessary to get document folding working and enable
	 * document folding
	 */
	private void updateProjectionSupport() {
		// dispose of previous document folding support
		if (fProjectionModelUpdater != null) {
			fProjectionModelUpdater.uninstall();
			fProjectionModelUpdater = null;
		}

		ProjectionViewer projectionViewer = (ProjectionViewer) getSourceViewer();
		if (projectionViewer == null) {
			return;
		}

		fProjectionModelUpdater = new PHPFoldingStructureProviderProxy();
		if (fProjectionModelUpdater != null) {
			fProjectionModelUpdater.install(projectionViewer);
			fProjectionModelUpdater.initialize();
		}

	}

	@Override
	protected boolean canHandleMove(IEditorInput originalElement, IEditorInput movedElement) {
		if (getRefactorableFileEditorInput() != null) {
			getRefactorableFileEditorInput().setRefactor(true);
		}
		return super.canHandleMove(originalElement, movedElement);
	}

	private RefactorableFileEditorInput getRefactorableFileEditorInput() {
		if (getEditorInput() instanceof RefactorableFileEditorInput) {
			return (RefactorableFileEditorInput) getEditorInput();
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object getAdapter(Class required) {

		if (required == IFoldingStructureProviderExtension.class && fProjectionModelUpdater != null) {
			IStructuredTextFoldingProvider foldingProvider = fProjectionModelUpdater.getFoldingProvider();
			if (foldingProvider instanceof IFoldingStructureProviderExtension) {
				return foldingProvider;
			}
		}

		if (required == IFoldingStructureProvider.class && fProjectionModelUpdater != null) {
			IStructuredTextFoldingProvider foldingProvider = fProjectionModelUpdater.getFoldingProvider();
			if (foldingProvider instanceof IFoldingStructureProvider) {
				return foldingProvider;
			}
		}

		Object adapter = super.getAdapter(required);

		// add selection listener to outline page
		// so that if outline selects model element, editor selects correct item
		if (adapter instanceof ConfigurableContentOutlinePage && IContentOutlinePage.class.equals(required)
				&& shouldOutlineViewBeLoaded()) {
			final ConfigurableContentOutlinePage outlinePage = (ConfigurableContentOutlinePage) adapter;
			fPHPOutlinePage = outlinePage;
			outlinePage.setInput(getModelElement());
		}
		return adapter;
	}

	private boolean shouldOutlineViewBeLoaded() {
		if (fPHPOutlinePage != null && fPHPOutlinePage.getControl() != null
				&& !fPHPOutlinePage.getControl().isDisposed()) {
			return false;
		}
		return true;
	}

	protected void clearStatusLine() {
		setStatusLineErrorMessage(null);
		setStatusLineMessage(null);
	}

	public SourceViewerConfiguration getSourceViwerConfiguration() {
		return super.getSourceViewerConfiguration();
	}

	/**
	 * Returns the cached selected range, which allows to query it from a non-UI
	 * thread.
	 * <p>
	 * The result might be outdated if queried from a non-UI thread.</em>
	 * </p>
	 * 
	 * @return the caret offset in the master document
	 * @see ITextViewer#getSelectedRange()
	 * @since 3.3
	 */
	public Point getCachedSelectedRange() {
		return fCachedSelectedRange;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.wst.sse.ui.StructuredTextEditor#handleCursorPositionChanged()
	 */
	@Override
	protected void handleCursorPositionChanged() {
		updateCursorDependentActions();
		if (getTextViewer() != null) {
			fCachedSelectedRange = getTextViewer().getSelectedRange();
		} else {
			fCachedSelectedRange = null;
		}
		super.handleCursorPositionChanged();
	}

	@Override
	protected void handlePreferenceStoreChanged(final PropertyChangeEvent event) {
		final String property = event.getProperty();
		try {
			if (AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH.equals(property)) {
				/*
				 * Ignore tab setting since we rely on the formatter
				 * preferences. We do this outside the try-finally block to
				 * avoid that EDITOR_TAB_WIDTH is handled by the sub-class
				 * (AbstractDecoratedTextEditor).
				 */
				return;
			}

			if (PreferenceConstants.EDITOR_TEXT_HOVER_MODIFIERS.equals(property)
					|| PreferenceConstants.EDITOR_TEXT_HOVER_MODIFIER_MASKS.equals(property)) {
				updateHoverBehavior();
				return;
			}
			boolean newBooleanValue = false;
			Object newValue = event.getNewValue();
			if (newValue != null) {
				newBooleanValue = Boolean.valueOf(newValue.toString()).booleanValue();
			}
			if (PreferenceConstants.EDITOR_MARK_OCCURRENCES.equals(property)) {
				if (newBooleanValue != fMarkOccurrenceAnnotations) {
					fMarkOccurrenceAnnotations = newBooleanValue;
					if (!fMarkOccurrenceAnnotations) {
						uninstallOccurrencesFinder();
					} else {
						installOccurrencesFinder(true);
					}
				}
				return;
			}
			if (PreferenceConstants.EDITOR_CLOSE_BRACKETS.equals(property)) {
				fBracketInserter.setCloseBracketsEnabled(getPreferenceStore().getBoolean(property));
				return;
			}

			if (PreferenceConstants.EDITOR_CLOSE_STRINGS.equals(property)) {
				fBracketInserter.setCloseStringsEnabled(getPreferenceStore().getBoolean(property));
				return;
			}

			if (PreferenceConstants.EDITOR_MARK_TYPE_OCCURRENCES.equals(property)) {
				fMarkTypeOccurrences = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_METHOD_OCCURRENCES.equals(property)) {
				fMarkMethodOccurrences = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_FUNCTION_OCCURRENCES.equals(property)) {
				fMarkFunctionOccurrences = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_CONSTANT_OCCURRENCES.equals(property)) {
				fMarkConstantOccurrences = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_GLOBAL_VARIABLE_OCCURRENCES.equals(property)) {
				fMarkGlobalVariableOccurrences = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_LOCAL_VARIABLE_OCCURRENCES.equals(property)) {
				fMarkLocalVariableOccurrences = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_METHOD_EXIT_POINTS.equals(property)) {
				fMarkMethodExitPoints = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_BREAK_CONTINUE_TARGETS.equals(property)) {
				fMarkBreakContinueTargets = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_MARK_IMPLEMENTORS.equals(property)) {
				fMarkImplementors = newBooleanValue;
				return;
			}
			if (PreferenceConstants.EDITOR_STICKY_OCCURRENCES.equals(property)) {
				fStickyOccurrenceAnnotations = newBooleanValue;
				return;
			}

			if (affectsOverrideIndicatorAnnotations(event)) {
				if (isShowingOverrideIndicators()) {
					if (fOverrideIndicatorManager == null) {
						installOverrideIndicator(true);
					}
				} else {
					if (fOverrideIndicatorManager != null) {
						uninstallOverrideIndicator();
					}
				}
				return;
			}

		} finally {
			super.handlePreferenceStoreChanged(event);
		}

		if (PreferenceConstants.EDITOR_FOLDING_ENABLED.equals(property)) {
			if (getSourceViewer() instanceof ProjectionViewer) {
				// install projection support if it has not even been
				// installed yet
				if (isFoldingEnabled() && !projectionSupportInstalled) {
					installProjectionSupport();
				}
			}
		}
	}

	private static char getEscapeCharacter(char character) {
		switch (character) {
		case '"':
		case '\'':
			return '\\';
		default:
			return 0;
		}
	}

	private static char getPeerCharacter(char character) {
		switch (character) {
		case '(':
			return ')';
		case ')':
			return '(';
		case '[':
			return ']';
		case ']':
			return '[';
		case '"':
			return character;
		case '\'':
			return character;
		case '`':
			return character;
		default:
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Determines whether the preference change encoded by the given event
	 * changes the override indication.
	 * 
	 * @param event
	 *            the event to be investigated
	 * @return <code>true</code> if event causes a change
	 */
	protected boolean affectsOverrideIndicatorAnnotations(PropertyChangeEvent event) {
		String key = event.getProperty();
		AnnotationPreference preference = getAnnotationPreferenceLookup()
				.getAnnotationPreference(OverrideIndicatorManager.ANNOTATION_TYPE);
		if (key == null || preference == null) {
			return false;
		}

		return key.equals(preference.getHighlightPreferenceKey())
				|| key.equals(preference.getVerticalRulerPreferenceKey())
				|| key.equals(preference.getOverviewRulerPreferenceKey())
				|| key.equals(preference.getTextPreferenceKey());
	}

	/**
	 * Tells whether override indicators are shown.
	 * 
	 * @return <code>true</code> if the override indicators are shown
	 * @since 3.0
	 */
	protected boolean isShowingOverrideIndicators() {
		AnnotationPreference preference = getAnnotationPreferenceLookup()
				.getAnnotationPreference(OverrideIndicatorManager.ANNOTATION_TYPE);
		IPreferenceStore store = getPreferenceStore();
		return getBoolean(store, preference.getHighlightPreferenceKey())
				|| getBoolean(store, preference.getVerticalRulerPreferenceKey())
				|| getBoolean(store, preference.getOverviewRulerPreferenceKey())
				|| getBoolean(store, preference.getTextPreferenceKey());
	}

	/**
	 * Returns the boolean preference for the given key.
	 * 
	 * @param store
	 *            the preference store
	 * @param key
	 *            the preference key
	 * @return <code>true</code> if the key exists in the store and its value is
	 *         <code>true</code>
	 * @since 3.0
	 */
	private boolean getBoolean(IPreferenceStore store, String key) {
		return key != null && store.getBoolean(key);
	}

	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { PHPUiConstants.EDITOR_SCOPE });
	}

	/**
	 * Marks or unmarks the given action to be updated on text cursor position
	 * changes.
	 * 
	 * @param actionId
	 *            the action id
	 * @param mark
	 *            <code>true</code> if the action is cursor position dependent
	 */
	public void markAsCursorDependentAction(final String actionId, final boolean mark) {
		assert actionId != null;
		if (mark) {
			if (!fCursorActions.contains(actionId)) {
				fCursorActions.add(actionId);
			}
		} else {
			fCursorActions.remove(actionId);
		}
	}

	public IDocument getDocument() {
		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer != null) {
			return sourceViewer.getDocument();
		}
		return null;
	}

	/**
	 * Updates the specified action by calling <code>IUpdate.update</code> if
	 * applicable.
	 * 
	 * @param actionId
	 *            the action id
	 */
	private void updateAction(final String actionId) {
		assert actionId != null;
		final IAction action = getAction(actionId);
		if (action instanceof IUpdate) {
			((IUpdate) action).update();
		}
	}

	/**
	 * Updates all cursor position dependent actions.
	 */
	protected void updateCursorDependentActions() {
		if (fCursorActions != null) {

			long currentTime = System.currentTimeMillis();
			if (fLastActionsUpdate > currentTime - 1000) { // only allow updates
				// at most once per
				// second
				return;
			}
			fLastActionsUpdate = currentTime;

			final Iterator<String> e = fCursorActions.iterator();
			while (e.hasNext()) {
				updateAction(e.next());
			}
		}
	}

	/*
	 * Update the hovering behavior depending on the preferences.
	 */
	private void updateHoverBehavior() {
		final SourceViewerConfiguration configuration = getSourceViewerConfiguration();
		final String[] types = configuration.getConfiguredContentTypes(getSourceViewer());

		for (final String t : types) {

			final ISourceViewer sourceViewer = getSourceViewer();
			if (sourceViewer instanceof ITextViewerExtension2) {
				// Remove existing hovers
				((ITextViewerExtension2) sourceViewer).removeTextHovers(t);

				final int[] stateMasks = configuration.getConfiguredTextHoverStateMasks(getSourceViewer(), t);

				if (stateMasks != null) {
					for (final int stateMask : stateMasks) {
						final ITextHover textHover = configuration.getTextHover(sourceViewer, t, stateMask);
						((ITextViewerExtension2) sourceViewer).setTextHover(textHover, t, stateMask);
					}
				} else {
					final ITextHover textHover = configuration.getTextHover(sourceViewer, t);
					((ITextViewerExtension2) sourceViewer).setTextHover(textHover, t,
							ITextViewerExtension2.DEFAULT_HOVER_STATE_MASK);
				}
			} else {
				sourceViewer.setTextHover(configuration.getTextHover(sourceViewer, t), t);
			}
		}
	}

	@Override
	protected StructuredTextViewer createStructedTextViewer(Composite parent, IVerticalRuler verticalRuler,
			int styles) {
		if (getProject() != null) {
			fFormatterProfileListener = new IPreferencesPropagatorListener() {
				@Override
				public void preferencesEventOccured(PreferencesPropagatorEvent event) {
					SourceViewerConfiguration config = getSourceViewerConfiguration();
					ISourceViewer sourceViewer = getSourceViewer();
					if (config != null && sourceViewer != null) {
						StyledText textWidget = sourceViewer.getTextWidget();
						int tabWidth = config.getTabWidth(sourceViewer);
						if (textWidget != null && textWidget.getTabs() != tabWidth) {
							textWidget.setTabs(tabWidth);
						}
					}
				}

				@Override
				public IProject getProject() {
					IScriptProject scriptProject = PHPStructuredEditor.this.getProject();
					if (scriptProject != null) {
						return scriptProject.getProject();
					}
					return null;
				}
			};

			// workaround for bug 409116
			PreferencesPropagator propagator = PreferencePropagatorFactory.getPreferencePropagator(FORMATTER_PLUGIN_ID);
			propagator.addPropagatorListener(fFormatterProfileListener,
					"org.eclipse.php.formatter.core.formatter.tabulation.size"); //$NON-NLS-1$
			propagator.addPropagatorListener(fFormatterProfileListener, "formatterProfile"); //$NON-NLS-1$
		}
		return new PHPStructuredTextViewer(this, parent, verticalRuler, getOverviewRuler(), isOverviewRulerVisible(),
				styles);
	}

	/**
	 * Resets the foldings structure according to the folding preferences.
	 */
	public void resetProjection() {
		if (fProjectionModelUpdater != null) {
			fProjectionModelUpdater.initialize();
		}
	}

	/*
	 * @see org.eclipse.ui.texteditor.AbstractTextEditor#performSave(boolean,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected void performSave(boolean overwrite, IProgressMonitor progressMonitor) {
		IDocumentProvider p = getDocumentProvider();
		if (p instanceof ISourceModuleDocumentProvider) {
			ISourceModuleDocumentProvider cp = (ISourceModuleDocumentProvider) p;
			cp.setSavePolicy(fSavePolicy);
		}
		try {
			super.performSave(overwrite, progressMonitor);
		} finally {
			if (p instanceof ISourceModuleDocumentProvider) {
				ISourceModuleDocumentProvider cp = (ISourceModuleDocumentProvider) p;
				cp.setSavePolicy(null);
			}
		}
	}

	@Override
	public IDocumentProvider getDocumentProvider() {
		if (getEditorInput() instanceof ExternalStorageEditorInput) {
			IDocumentProvider provider = LocalStorageModelProvider.getInstance();
			if (provider != null) {
				return provider;
			}
		}
		if (getEditorInput() instanceof RefactorableFileEditorInput) {
			return super.getDocumentProvider();
		}
		if (getEditorInput() instanceof IStorageEditorInput) {
			IDocumentProvider provider = StorageModelProvider.getInstance();
			if (provider != null) {
				return provider;
			}
		}

		return super.getDocumentProvider();
	}

	/**
	 * IScriptReconcilingListener methods - reconcile listeners
	 */
	private ListenerList<IPHPScriptReconcilingListener> fReconcilingListeners = new ListenerList<>(
			ListenerList.IDENTITY);

	public void addReconcileListener(IPHPScriptReconcilingListener reconcileListener) {
		synchronized (fReconcilingListeners) {
			fReconcilingListeners.add(reconcileListener);
		}
	}

	public void removeReconcileListener(IPHPScriptReconcilingListener reconcileListener) {
		synchronized (fReconcilingListeners) {
			fReconcilingListeners.remove(reconcileListener);
		}
	}

	public void aboutToBeReconciled() {
		// Notify AST provider
		PHPUiPlugin.getDefault().getASTProvider().aboutToBeReconciled((ISourceModule) getModelElement());
		// Notify listeners
		Object[] listeners = fReconcilingListeners.getListeners();
		for (int i = 0, length = listeners.length; i < length; ++i) {
			((IPHPScriptReconcilingListener) listeners[i]).aboutToBeReconciled();
		}
	}

	/*
	 * @see
	 * org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener#reconciled
	 * (CompilationUnit, boolean, IProgressMonitor)
	 * 
	 * @since 3.0
	 */
	public void reconciled(Program ast, boolean forced, IProgressMonitor progressMonitor) {

		// see: https://bugs.eclipse.org/bugs/show_bug.cgi?id=58245
		PHPUiPlugin phpPlugin = PHPUiPlugin.getDefault();
		if (phpPlugin == null) {
			return;
		}

		// Always notify AST provider
		ISourceModule inputModelElement = (ISourceModule) getModelElement();

		phpPlugin.getASTProvider().reconciled(ast, inputModelElement, progressMonitor);

		// Notify listeners
		Object[] listeners = fReconcilingListeners.getListeners();
		for (int i = 0, length = listeners.length; i < length; ++i) {
			((IPHPScriptReconcilingListener) listeners[i]).reconciled(ast, forced, progressMonitor);
		}
	}

	/**
	 * Returns the model element wrapped by this editors input. Most likely to
	 * be the relevant source module
	 * 
	 * @return the model element wrapped by this editors input.
	 * 
	 */
	public IModelElement getModelElement() {
		return EditorUtility.getEditorInputModelElement(this, false);
	}

	/**
	 * Returns project that holds the edited file (if any)
	 * 
	 * @return project or <code>null</code> if there's no one
	 */
	public IScriptProject getProject() {
		IModelElement modelElement = getModelElement();
		if (modelElement != null) {
			return modelElement.getScriptProject();
		}
		return null;
	}

	/**
	 * Support mark occurrences in PHP Editor
	 */

	/**
	 * Returns the lock object for the given annotation model.
	 * 
	 * @param annotationModel
	 *            the annotation model
	 * @return the annotation model's lock object
	 * @since 3.0
	 */
	private Object getLockObject(IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			Object lock = ((ISynchronizable) annotationModel).getLockObject();
			if (lock != null) {
				return lock;
			}
		}
		return annotationModel;
	}

	protected void updateOccurrenceAnnotations(ITextSelection selection, Program astRoot) {
		updateOccurrencesAnnotationsRunJob(selection, astRoot);
	}

	/**
	 * Updates the occurrences annotations based on the current selection.
	 * 
	 * @param selection
	 *            the text selection
	 * @param astRoot
	 *            the compilation unit AST
	 * @since 3.0
	 */
	protected void updateOccurrenceAnnotations(final ITextSelection selection, final IModelElement sourceModule) {

		if (fOccurrencesFinderJob != null) {
			fOccurrencesFinderJob.cancel();
		}

		if (!fMarkOccurrenceAnnotations) {
			return;
		}

		String updatingOccurrencesJobName = "Updating occurrence annotations"; //$NON-NLS-1$

		IJobManager jobManager = Job.getJobManager();
		if (jobManager.find(updatingOccurrencesJobName).length > 0) {
			jobManager.cancel(updatingOccurrencesJobName);
		}
		Job job = new Job(updatingOccurrencesJobName) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				Program astRoot = null;
				try {
					astRoot = SharedASTProvider.getAST((ISourceModule) sourceModule, SharedASTProvider.WAIT_ACTIVE_ONLY,
							new NullProgressMonitor());
				} catch (ModelException e) {
					Logger.logException(e);
				} catch (IOException e) {
					Logger.logException(e);
				}
				if (!monitor.isCanceled() && astRoot != null) {
					updateOccurrencesAnnotationsRunJob(selection, astRoot);
				}
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object family) {
				return getName().equals(family);
			}
		};
		job.setSystem(true);
		job.setPriority(Job.DECORATE);
		job.schedule();

	}

	private void updateOccurrencesAnnotationsRunJob(ITextSelection selection, Program astRoot) {
		if (astRoot == null || selection == null) {
			return;
		}

		ISourceViewer viewer = getSourceViewer();
		if (viewer == null) {
			return;
		}

		IDocument document = viewer.getDocument();
		if (document == null) {
			return;
		}

		boolean hasChanged = false;
		if (document instanceof IDocumentExtension4) {
			int offset = selection.getOffset();
			long currentModificationStamp = ((IDocumentExtension4) document).getModificationStamp();
			IRegion markOccurrenceTargetRegion = fMarkOccurrenceTargetRegion;
			hasChanged = currentModificationStamp != fMarkOccurrenceModificationStamp;
			if (markOccurrenceTargetRegion != null && !hasChanged) {
				if (markOccurrenceTargetRegion.getOffset() <= offset
						&& offset <= markOccurrenceTargetRegion.getOffset() + markOccurrenceTargetRegion.getLength()) {
					return;
				}
			}
			fMarkOccurrenceTargetRegion = ScriptWordFinder.findWord(document, offset);
			fMarkOccurrenceModificationStamp = currentModificationStamp;
		}

		OccurrenceLocation[] locations = null;

		ASTNode selectedNode = NodeFinder.perform(astRoot, selection.getOffset(), selection.getLength());

		if (locations == null && fMarkExceptions) {
			// TODO : Implement Me!
			// ExceptionOccurrencesFinder finder= new
			// ExceptionOccurrencesFinder();
			// if (finder.initialize(astRoot, selectedNode) == null) {
			// locations= finder.getOccurrences();
			// }
		}

		if (locations == null && fMarkMethodExitPoints) {
			IOccurrencesFinder finder = OccurrencesFinderFactory.createMethodExitsFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations = finder.getOccurrences();
			}
		}

		if (locations == null && fMarkImplementors) {
			IOccurrencesFinder finder = OccurrencesFinderFactory.createIncludeFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations = finder.getOccurrences();
			}
		}

		if (locations == null && fMarkBreakContinueTargets) {
			IOccurrencesFinder finder = OccurrencesFinderFactory.createBreakContinueTargetFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations = finder.getOccurrences();
			}
		}

		if (locations == null && fMarkImplementors) {
			IOccurrencesFinder finder = OccurrencesFinderFactory.createImplementorsOccurrencesFinder();
			if (finder.initialize(astRoot, selectedNode) == null) {
				locations = finder.getOccurrences();
			}
		}

		if (selectedNode != null && selectedNode.getType() == ASTNode.VARIABLE) {
			final Expression name = ((Variable) selectedNode).getName();
			if (name instanceof Identifier) {
				selectedNode = name;
			}
		}

		if (locations == null && selectedNode != null
				&& (selectedNode instanceof Identifier || (isScalarButNotInString(selectedNode)))) {
			int type = PHPElementConciliator.concile(selectedNode);
			if (markOccurrencesOfType(type)) {
				IOccurrencesFinder finder = OccurrencesFinderFactory.getOccurrencesFinder(type);
				if (finder != null) {
					if (finder.initialize(astRoot, selectedNode) == null) {
						locations = finder.getOccurrences();
					}
				}
			}
		}

		if (locations == null) {
			if (!fStickyOccurrenceAnnotations) {
				removeOccurrenceAnnotations();
			} else if (hasChanged) {
				removeOccurrenceAnnotations();
			}
			return;
		}

		fOccurrencesFinderJob = new OccurrencesFinderJob(document, locations, selection);
		// fOccurrencesFinderJob.setPriority(Job.DECORATE);
		// fOccurrencesFinderJob.setSystem(true);
		// fOccurrencesFinderJob.schedule();
		fOccurrencesFinderJob.run(new NullProgressMonitor());
	}

	/**
	 * Checks whether or not the node is a scalar and return true only if the
	 * scalar is not part of a string
	 * 
	 * @param node
	 * @return
	 */
	private boolean isScalarButNotInString(ASTNode node) {
		return (node.getType() == ASTNode.SCALAR) && (node.getParent().getType() != ASTNode.QUOTE);
	}

	protected void uninstallOverrideIndicator() {
		if (fOverrideIndicatorManager != null) {
			removeReconcileListener(fOverrideIndicatorManager);
			fOverrideIndicatorManager.removeAnnotations();
			fOverrideIndicatorManager = null;
		}
	}

	protected void installOverrideIndicator(boolean provideAST) {
		uninstallOverrideIndicator();
		if (getDocumentProvider() == null) {
			return;
		}
		IAnnotationModel model = getDocumentProvider().getAnnotationModel(getEditorInput());
		final IModelElement inputElement = getModelElement();
		if (model == null || inputElement == null || inputElement.getElementType() != IModelElement.SOURCE_MODULE) {
			return;
		}

		fOverrideIndicatorManager = new OverrideIndicatorManager(model);
		addReconcileListener(fOverrideIndicatorManager);
		if (provideAST) {
			Job job = new Job("Installing override indicator") { //$NON-NLS-1$

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						Program ast = SharedASTProvider.getAST((ISourceModule) inputElement,
								SharedASTProvider.WAIT_ACTIVE_ONLY, new NullProgressMonitor());
						if (fOverrideIndicatorManager != null) {
							fOverrideIndicatorManager.reconciled(ast, true, getProgressMonitor());
						}
					} catch (ModelException e) {
						Logger.logException(e);
					} catch (IOException e) {
						Logger.logException(e);
					}
					return Status.OK_STATUS;
				}
			};
			job.setSystem(true);
			job.setPriority(Job.DECORATE);
			job.schedule();

		}
	}

	protected void installOccurrencesFinder(boolean forceUpdate) {
		fMarkOccurrenceAnnotations = true;

		fPostSelectionListenerWithAST = new ISelectionListenerWithAST() {
			@Override
			public void selectionChanged(IEditorPart part, ITextSelection selection, Program astRoot) {
				updateOccurrenceAnnotations(selection, astRoot);
			}
		};
		SelectionListenerWithASTManager.getDefault().addListener(this, fPostSelectionListenerWithAST);
		if (forceUpdate && getSelectionProvider() != null) {
			fForcedMarkOccurrencesSelection = getSelectionProvider().getSelection();
			final IModelElement source = getModelElement();
			if ((source != null) && source.getElementType() == IModelElement.SOURCE_MODULE) {
				updateOccurrenceAnnotations((ITextSelection) fForcedMarkOccurrencesSelection, (ISourceModule) source);
			}
		}

		if (fOccurrencesFinderJobCanceler == null) {
			fOccurrencesFinderJobCanceler = new OccurrencesFinderJobCanceler();
			fOccurrencesFinderJobCanceler.install();
		}
	}

	protected void uninstallOccurrencesFinder() {
		fMarkOccurrenceAnnotations = false;

		if (fOccurrencesFinderJob != null) {
			fOccurrencesFinderJob.cancel();
			fOccurrencesFinderJob = null;
		}

		if (fOccurrencesFinderJobCanceler != null) {
			fOccurrencesFinderJobCanceler.uninstall();
			fOccurrencesFinderJobCanceler = null;
		}

		if (fPostSelectionListenerWithAST != null) {
			SelectionListenerWithASTManager.getDefault().removeListener(this, fPostSelectionListenerWithAST);
			fPostSelectionListenerWithAST = null;
		}

		removeOccurrenceAnnotations();
	}

	public boolean isMarkingOccurrences() {
		IPreferenceStore store = getPreferenceStore();
		return store != null && store.getBoolean(PreferenceConstants.EDITOR_MARK_OCCURRENCES);
	}

	/**
	 * Returns is the occurrences of the type should be marked.
	 * 
	 * @param type
	 *            One of the {@link PHPElementConciliator} constants integer
	 *            type.
	 * @return True, if the type occurrences should be marked; False, otherwise.
	 */
	boolean markOccurrencesOfType(int type) {
		switch (type) {
		case PHPElementConciliator.CONCILIATOR_GLOBAL_VARIABLE:
			return fMarkGlobalVariableOccurrences;
		case PHPElementConciliator.CONCILIATOR_LOCAL_VARIABLE:
			return fMarkLocalVariableOccurrences;
		case PHPElementConciliator.CONCILIATOR_FUNCTION:
			return fMarkFunctionOccurrences;
		case PHPElementConciliator.CONCILIATOR_CLASSNAME:
		case PHPElementConciliator.CONCILIATOR_TRAITNAME:
			return fMarkTypeOccurrences;
		case PHPElementConciliator.CONCILIATOR_CONSTANT:
			return fMarkConstantOccurrences;
		case PHPElementConciliator.CONCILIATOR_CLASS_MEMBER:
			return fMarkMethodOccurrences;
		case PHPElementConciliator.CONCILIATOR_UNKNOWN:
		case PHPElementConciliator.CONCILIATOR_PROGRAM:
		default:
			return false;
		}
	}

	void removeOccurrenceAnnotations() {
		fMarkOccurrenceModificationStamp = IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
		fMarkOccurrenceTargetRegion = null;

		IDocumentProvider documentProvider = getDocumentProvider();
		if (documentProvider == null) {
			return;
		}

		IAnnotationModel annotationModel = documentProvider.getAnnotationModel(getEditorInput());
		if (annotationModel == null || fOccurrenceAnnotations == null) {
			return;
		}

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(fOccurrenceAnnotations, null);
			} else {
				for (int i = 0, length = fOccurrenceAnnotations.length; i < length; i++) {
					annotationModel.removeAnnotation(fOccurrenceAnnotations[i]);
				}
			}
			fOccurrenceAnnotations = null;
		}
	}

	protected boolean isActivePart() {
		IWorkbenchPart part = getActivePart();
		return part != null && part.equals(this);
	}

	private IWorkbenchPart getActivePart() {
		IWorkbenchWindow window = getSite().getWorkbenchWindow();
		IPartService service = window.getPartService();
		IWorkbenchPart part = service.getActivePart();
		return part;
	}

	protected void setSelection(ISourceReference reference, boolean moveCursor) {
		if (getSelectionProvider() == null) {
			return;
		}
		final ISelection[] selection = new ISelection[1];
		Runnable getSelection = () -> {
			selection[0] = getSelectionProvider().getSelection();
		};
		if (Display.getCurrent() != null) {
			getSelection.run();
		} else {
			Display.getDefault().syncExec(getSelection);
		}
		if (selection[0] == null) {
			return;
		}
		if (selection[0] instanceof TextSelection) {
			TextSelection textSelection = (TextSelection) selection[0];
			if (textSelection instanceof IStructuredSelection) {
				Object firstElement = ((IStructuredSelection) textSelection).getFirstElement();
				if (firstElement instanceof IImplForPHP) {
					((IImplForPHP) firstElement).setModelElement(getModelElement());
				}
			}
			// PR 39995: [navigation] Forward history cleared after going back
			// in navigation history:
			// mark only in navigation history if the cursor is being moved
			// (which it isn't if
			// this is called from a PostSelectionEvent that should only update
			// the magnet)
			if (moveCursor && (textSelection.getOffset() != 0 || textSelection.getLength() != 0)) {
				markInNavigationHistory();
			}
		}
		if (reference != null) {
			StyledText textWidget = null;
			ISourceViewer sourceViewer = getSourceViewer();
			if (sourceViewer != null) {
				textWidget = sourceViewer.getTextWidget();
			}
			if (textWidget == null) {
				return;
			}
			try {
				ISourceRange range = null;
				range = reference.getSourceRange();
				if (range == null) {
					return;
				}
				IDocument document = getDocument();
				int offset = range.getOffset();
				int length = range.getLength();
				// avoid throwing BadLocationException in
				// GenericPositionManager#addPosition(String, Position)
				if (offset < 0 || length < 0 || (document != null && offset + length > document.getLength())) {
					return;
				}
				setHighlightRange(offset, length, moveCursor);
				if (!moveCursor) {
					return;
				}
				offset = -1;
				length = -1;
				if (reference instanceof IMember) {
					range = ((IMember) reference).getNameRange();
					if (range != null) {
						offset = range.getOffset();
						length = range.getLength();
					}
				}
				if (offset > -1 && length > 0 && sourceViewer != null) {
					try {
						textWidget.setRedraw(false);
						sourceViewer.revealRange(offset, length);
						sourceViewer.setSelectedRange(offset, length);
					} finally {
						textWidget.setRedraw(true);
					}
					markInNavigationHistory();
				}
			} catch (ModelException x) {
			} catch (IllegalArgumentException x) {
			}
		} else if (moveCursor) {
			resetHighlightRange();
			markInNavigationHistory();
		}
	}

	@Override
	public void doSave(IProgressMonitor progressMonitor) {
		if (getDocument() instanceof IStructuredDocument) {
			CommandStack commandStack = ((IStructuredDocument) getDocument()).getUndoManager().getCommandStack();
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=322529
			((IStructuredDocument) getDocument()).getUndoManager().forceEndOfPendingCommand(null,
					getViewer().getSelectedRange().x, getViewer().getSelectedRange().y);
			if (commandStack instanceof BasicCommandStack) {
				((BasicCommandStack) commandStack).saveIsDone();
			}
		}

		super.doSave(progressMonitor);
	}

	public ISourceViewer getViewer() {
		return super.getSourceViewer();
	}

	@Override
	public void update() {
		super.update();
		if (fPHPOutlinePage != null && fPHPOutlinePage instanceof ConfigurableContentOutlinePage) {
			((ConfigurableContentOutlinePage) fPHPOutlinePage).setInput(getModelElement());
		}
	}

	@Override
	public boolean isDirty() {
		// if super.isDirty() return false,it means this
		boolean result = super.isDirty();
		if (!result) {
			return result;
		}
		if (getDocument() instanceof IStructuredDocument) {
			CommandStack commandStack = ((IStructuredDocument) getDocument()).getUndoManager().getCommandStack();
			if (commandStack instanceof BasicCommandStack) {
				BasicCommandStack bcs = (BasicCommandStack) commandStack;
				return bcs.isSaveNeeded();
			}
		}
		return result;
	}

	@Override
	public void firePropertyChange(int property) {
		super.firePropertyChange(property);
	}

	@Override
	protected boolean isTabsToSpacesConversionEnabled() {
		return true;
	}

	@Override
	protected void installTabsToSpacesConverter() {
		SourceViewerConfiguration config = getSourceViewerConfiguration();
		ISourceViewer sourceViewer = getSourceViewer();
		if (config != null && sourceViewer instanceof ITextViewerExtension7) {
			// NB: no need to reset the indentation object used by the
			// TabAutoEditStrategy object because installTabsToSpacesConverter()
			// is called each time the editor is bound to a new document.
			TabAutoEditStrategy strategy = new TabAutoEditStrategy(false);
			((ITextViewerExtension7) sourceViewer).setTabsToSpacesConverter(strategy);
			updateIndentPrefixes();
		}
	}

	/**
	 * Installs a tabs to spaces converter.
	 * 
	 * <p>
	 * Subclasses may extend or override this method.
	 * </p>
	 * 
	 * @since 3.3
	 */
	@Override
	protected void uninstallTabsToSpacesConverter() {
	}

	public void updatedTitleImage(Image image) {
		setTitleImage(image);
	}

	/**
	 * Workaround for BracketInserterTest
	 * 
	 * <p>
	 * Don't call this inside PDT.
	 * 
	 * TODO remove this once we find a better way to test it
	 * </p>
	 * 
	 * @return
	 */
	final public VerifyKeyListener getfBracketInserter() {
		return fBracketInserter;
	}
}
