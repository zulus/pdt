/*******************************************************************************
 * Copyright (c) 2006 Zend Corporation and IBM Corporation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   William Candillon {wcandillon@gmail.com} - Initial implementation
 *******************************************************************************/
package org.eclipse.php.internal.ui.editor.highlighters;

import org.eclipse.dltk.ast.Modifiers;
import org.eclipse.php.core.ast.nodes.*;
import org.eclipse.php.internal.ui.editor.highlighter.AbstractSemanticApply;
import org.eclipse.php.internal.ui.editor.highlighter.AbstractSemanticHighlighting;

public class StaticMethodHighlighting extends AbstractSemanticHighlighting {

	protected class StaticMethodApply extends AbstractSemanticApply {

		@Override
		public boolean visit(FunctionDeclaration functionDeclaration) {
			ASTNode parent = functionDeclaration.getParent();
			while (parent.getType() == ASTNode.BLOCK || parent.getType() == ASTNode.FUNCTION_DECLARATION) {
				parent = parent.getParent();
			}
			if (parent.getType() == ASTNode.METHOD_DECLARATION) {
				MethodDeclaration methodDeclaration = (MethodDeclaration) parent;
				if ((methodDeclaration.getModifier() & Modifiers.AccStatic) != 0) {
					highlight(functionDeclaration.getFunctionName());
				}

			}
			return true;
		}

		@Override
		public boolean visit(FunctionInvocation functionInvocation) {
			final Expression functionName = functionInvocation.getFunctionName().getName();
			final int invocationParent = functionInvocation.getParent().getType();
			if ((functionName.getType() == ASTNode.IDENTIFIER || (functionName.getType() == ASTNode.VARIABLE
					&& ((Variable) functionName).getName().getType() == ASTNode.IDENTIFIER))
					&& invocationParent == ASTNode.STATIC_METHOD_INVOCATION) {
				highlight(functionName);
			}
			return true;
		}
	}

	@Override
	public AbstractSemanticApply getSemanticApply() {
		return new StaticMethodApply();
	}

	@Override
	public int getPriority() {
		return 110;
	}

	@Override
	protected void initDefaultPreferences() {
		getStyle().setItalicByDefault(true);
	}

	@Override
	public String getDisplayName() {
		return Messages.StaticMethodHighlighting_0;
	}
}
