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
package org.eclipse.php.internal.core.codeassist.strategies;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.php.core.codeassist.ICompletionContext;
import org.eclipse.php.core.codeassist.ICompletionReporter;
import org.eclipse.php.internal.core.codeassist.ProposalExtraInfo;
import org.eclipse.php.internal.core.codeassist.contexts.AbstractCompletionContext;

/**
 * This strategy completes global classes after 'new' statement
 * 
 * @author michael
 */
public class InstanceOfStrategy extends TypesStrategy {

	public InstanceOfStrategy(ICompletionContext context) {
		super(context);
	}

	@Override
	public void apply(ICompletionReporter reporter) throws BadLocationException {
		// let NamespaceInstanceOfStrategy to deal with namespace prefix
		// AbstractCompletionContext completionContext = (AbstractCompletionContext)
		// getContext();
		// String suffix = getSuffix(completionContext);
		// addAlias(reporter, suffix);

		super.apply(reporter);
		ICompletionContext context = getContext();
		AbstractCompletionContext concreteContext = (AbstractCompletionContext) context;
		addSelf(concreteContext, reporter);
	}

	@Override
	public String getSuffix(AbstractCompletionContext abstractContext) {
		return ""; //$NON-NLS-1$
	}

	@Override
	protected int getExtraInfo() {
		return ProposalExtraInfo.TYPE_ONLY;
	}
}
