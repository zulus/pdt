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
package org.eclipse.php.core.compiler.ast.nodes;

import org.eclipse.dltk.ast.ASTVisitor;
import org.eclipse.dltk.ast.expressions.Expression;
import org.eclipse.dltk.ast.references.Reference;
import org.eclipse.dltk.utils.CorePrinter;
import org.eclipse.php.internal.core.compiler.ast.visitor.ASTPrintVisitor;

/**
 * Represents include, include_once, require and require_once expressions
 * 
 * <pre>
 * e.g.
 * 
 * include('myFile.php'), include_once($myFile),
 * require($myClass->getFileName()), require_once(A::FILE_NAME)
 * </pre>
 */
public class Include extends Reference {

	public static final int IT_REQUIRE = 0;
	public static final int IT_REQUIRE_ONCE = 1;
	public static final int IT_INCLUDE = 2;
	public static final int IT_INCLUDE_ONCE = 3;

	private final Expression expr;
	private final int includeType;

	public Include(int start, int end, Expression expr, int type) {
		super(start, end);

		assert expr != null;
		this.expr = expr;
		this.includeType = type;
	}

	public String getType() {
		switch (getIncludeType()) {
		case IT_REQUIRE:
			return "require"; //$NON-NLS-1$
		case IT_REQUIRE_ONCE:
			return "require_once"; //$NON-NLS-1$
		case IT_INCLUDE:
			return "include"; //$NON-NLS-1$
		case IT_INCLUDE_ONCE:
			return "include_once"; //$NON-NLS-1$
		default:
			throw new IllegalArgumentException();
		}
	}

	@Override
	public void traverse(ASTVisitor visitor) throws Exception {
		final boolean visit = visitor.visit(this);
		if (visit) {
			expr.traverse(visitor);
		}
		visitor.endvisit(this);
	}

	@Override
	public int getKind() {
		return ASTNodeKinds.INCLUDE;
	}

	public Expression getExpr() {
		return expr;
	}

	public int getIncludeType() {
		return includeType;
	}

	@Override
	public String getStringRepresentation() {
		return getType();
	}

	/**
	 * We don't print anything - we use {@link ASTPrintVisitor} instead
	 */
	@Override
	public final void printNode(CorePrinter output) {
	}

	@Override
	public String toString() {
		return ASTPrintVisitor.toXMLString(this);
	}
}
