/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
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
package org.eclipse.php.internal.core.documentModel.parser;

import java.util.List;

import org.eclipse.wst.sse.core.internal.provisional.events.RegionChangedEvent;
import org.eclipse.wst.sse.core.internal.provisional.events.StructuredDocumentEvent;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredTextReParser;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.xml.core.internal.parser.XMLStructuredDocumentReParser;

/**
 * Handles the php region when reparsing an XML/PHP structured document
 * 
 * @author Roy, 2006
 */
public class PHPStructuredDocumentReParser extends XMLStructuredDocumentReParser {

	public PHPStructuredDocumentReParser() {
		super();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.wst.xml.core.internal.parser.XMLStructuredDocumentReParser
	 * #newInstance()
	 */
	@Override
	public IStructuredTextReParser newInstance() {
		return new PHPStructuredDocumentReParser();
	}

	/**
	 * Adding the support to php comments
	 */
	@Override
	protected StructuredDocumentEvent checkForComments() {
		StructuredDocumentEvent result = checkForCriticalKey("/*"); //$NON-NLS-1$
		if (result == null) {
			result = checkForCriticalKey("*/"); //$NON-NLS-1$
		}
		return result != null ? result : super.checkForComments();
	}

	/**
	 * This function was added in order to support asp tags in PHP (bug fix #150363)
	 */
	@Override
	protected StructuredDocumentEvent checkForCrossStructuredDocumentRegionSyntax() {
		StructuredDocumentEvent result = super.checkForCrossStructuredDocumentRegionSyntax();
		if (result == null) {
			result = checkForCriticalKey("<%"); //$NON-NLS-1$
			if (result == null) {
				result = checkForCriticalKey("%>"); //$NON-NLS-1$
			}

		}
		return result;
	}

	/**
	 * Change PHP Script Regions nodes...
	 */
	@Override
	protected StructuredDocumentEvent regionCheck(IStructuredDocumentRegion oldNode,
			IStructuredDocumentRegion newNode) {
		final StructuredDocumentEvent event = super.regionCheck(oldNode, newNode);

		if (event instanceof RegionChangedEvent) {
			RegionChangedEvent changedEvent = (RegionChangedEvent) event;

			if (changedEvent.getRegion().getType() == PHPRegionContext.PHP_CONTENT) {
				oldNode.setRegions(newNode.getRegions());
			}
		}
		return event;
	}

	/**
	 * This implementation updates the php tokens model after updating WST editor
	 * model
	 */
	@Override
	public StructuredDocumentEvent reparse() {
		// NB: ValBuilderJob calls reparse() on all PHP files but
		// uses model returned by
		// StructuredModelManager.getModelManager().getModelForRead((IFile)
		// resource) that didn't keep file project informations, so
		// in this case we'll end with ((PhpSourceParser)
		// fStructuredDocument.getParser()).getProject() having null value.
		final StructuredDocumentEvent documentEvent = super.reparse();
		return documentEvent;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public StructuredDocumentEvent _checkBlockNodeList(List blockTagList) {
		// There are no blocktags that should be checked within PHP script
		// content
		if (dirtyStart.equals(dirtyEnd)) {
			ITextRegion region = dirtyStart.getRegionAtCharacterOffset(fStart);
			if (region != null && region.getType().equals(PHPRegionContext.PHP_CONTENT)
					&& (dirtyStart.getStart() + region.getEnd() >= (fStart + fLengthToReplace))) {
				return null;
			}
		}
		return super._checkBlockNodeList(blockTagList);
	}
}
