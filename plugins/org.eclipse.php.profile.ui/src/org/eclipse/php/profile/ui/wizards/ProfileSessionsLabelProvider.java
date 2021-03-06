/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rogue Wave Software Inc. - initial implementation
 *******************************************************************************/
package org.eclipse.php.profile.ui.wizards;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.osgi.util.NLS;
import org.eclipse.php.profile.core.engine.ProfilerDB;
import org.eclipse.php.profile.ui.PHPProfileUIMessages;
import org.eclipse.php.profile.ui.ProfilerUIImages;
import org.eclipse.swt.graphics.Image;

/**
 * Profile sessions label provider.
 */
class ProfileSessionsLabelProvider extends LabelProvider {
	private Image fProcessImage;

	public ProfileSessionsLabelProvider() {
		fProcessImage = ProfilerUIImages.get(ProfilerUIImages.IMG_OBJ_PROCESS);
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof ProfilerDB) {
			return fProcessImage;
		}
		return null;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof ProfilerDB) {
			ProfilerDB db = (ProfilerDB) element;
			return NLS.bind(PHPProfileUIMessages.getString("ProfilingMonitorLabelProvider.0"), //$NON-NLS-1$
					db.getGlobalData().getURI(), db.getProfileDate());
		}
		return super.getText(element);
	}
}