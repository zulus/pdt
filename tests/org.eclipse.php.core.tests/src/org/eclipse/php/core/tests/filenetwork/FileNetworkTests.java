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
package org.eclipse.php.core.tests.filenetwork;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.core.IMethod;
import org.eclipse.dltk.core.IScriptProject;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.IType;
import org.eclipse.php.core.tests.PHPCoreTests;
import org.eclipse.php.core.tests.TestUtils;
import org.eclipse.php.core.tests.TestSuiteWatcher;
import org.eclipse.php.internal.core.filenetwork.FileNetworkUtility;
import org.eclipse.php.internal.core.filenetwork.ReferenceTree;
import org.eclipse.php.internal.core.filenetwork.ReferenceTree.Node;
import org.eclipse.php.internal.core.typeinference.PHPModelUtils;
import org.junit.ClassRule;
import org.junit.rules.TestWatcher;

import junit.framework.Test;

public class FileNetworkTests extends AbstractModelTests {

	@ClassRule
	public static TestWatcher watcher = new TestSuiteWatcher();

	protected static final String PROJECT = "filenetwork";
	protected IScriptProject SCRIPT_PROJECT;

	public FileNetworkTests(String name) {
		super(PHPCoreTests.PLUGIN_ID, name);
	}

	@Override
	public void setUpSuite() throws Exception {
		deleteProject(PROJECT);
		if (SCRIPT_PROJECT != null) {
			SCRIPT_PROJECT.close();
			SCRIPT_PROJECT = null;
		}
		super.setUpSuite();
	}

	@Override
	public void tearDownSuite() throws Exception {
		deleteProject(PROJECT);
		super.tearDownSuite();
	}

	@Override
	protected void setUp() throws Exception {
		if (SCRIPT_PROJECT == null) {
			SCRIPT_PROJECT = setUpScriptProject(PROJECT);
			TestUtils.waitForIndexer();
		}
		super.setUp();
	}

	protected String getSavedHierarchy(String subfolder) throws CoreException, IOException {
		IFile file = getFile(getFilePath(subfolder + "/hierarchy"));
		StringBuilder buf = new StringBuilder();
		BufferedReader r = new BufferedReader(new InputStreamReader(file.getContents()));
		String l;
		while ((l = r.readLine()) != null) {
			buf.append(l).append("\n");
		}
		r.close();
		return buf.toString();
	}

	public static Test suite() {
		Suite suite = new Suite(FileNetworkTests.class);
		return suite;
	}

	protected String sortLines(String str) {
		String[] split = str.split("[\r\n]+");
		Arrays.sort(split);
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < split.length; ++i) {
			if (i > 0) {
				buf.append("\n");
			}
			buf.append(split[i]);
		}
		return buf.toString();
	}

	protected void assertContents(String expected, String actual) {
		expected = sortLines(expected);
		actual = sortLines(actual);

		String diff = TestUtils.compareContents(expected, actual);
		if (diff != null) {
			fail(diff);
		}
	}

	protected String getFilePath(String projectRelativePath) {
		return "/" + PROJECT + "/" + projectRelativePath;
	}

	public void testReferencingFiles() throws Exception {
		ISourceModule sourceModule = getSourceModule(getFilePath("test1/a.php"));
		ReferenceTree tree = FileNetworkUtility.buildReferencingFilesTree(sourceModule, null);
		assertContents(getSavedHierarchy("test1"), tree.toString());
	}

	public void testReferencedFiles() throws Exception {
		ISourceModule sourceModule = getSourceModule(getFilePath("test2/a.php"));
		ReferenceTree tree = FileNetworkUtility.buildReferencedFilesTree(sourceModule, null);
		assertContents(getSavedHierarchy("test2"), tree.toString());
	}

	public void testCachedReferencedFiles() throws Exception {
		ISourceModule sourceModule = getSourceModule(getFilePath("test2/a.php"));
		HashMap<ISourceModule, Node> cache = new HashMap<>();

		ReferenceTree tree = FileNetworkUtility.buildReferencedFilesTree(sourceModule, cache, null);
		tree = FileNetworkUtility.buildReferencedFilesTree(sourceModule, cache, null);
		assertContents(getSavedHierarchy("test2"), tree.toString());
	}

	public void testSearchMethod() throws Exception {
		ISourceModule sourceModule = getSourceModule(getFilePath("test3/c.php"));
		IType type = sourceModule.getType("Test3");
		IMethod[] method = PHPModelUtils.getTypeHierarchyMethod(type, "foo", true, null);

		assertTrue("Can't find method", method.length > 0);
		assertEquals("Wrong method was found", "a.php", method[0].getSourceModule().getElementName());
	}

	public void testSearchMethodBadNetwork() throws Exception {
		ISourceModule sourceModule = getSourceModule(getFilePath("test4/c.php"));
		IType type = sourceModule.getType("Test4");
		IMethod[] method = PHPModelUtils.getTypeHierarchyMethod(type, "foo", true, null);

		assertTrue("There should be two methods found", method.length == 2);
	}
}
