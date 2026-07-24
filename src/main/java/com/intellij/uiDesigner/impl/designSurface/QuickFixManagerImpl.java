/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.impl.designSurface;

import java.awt.Rectangle;
import java.util.ArrayList;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import jakarta.annotation.Nonnull;
import com.intellij.uiDesigner.impl.ErrorAnalyzer;
import com.intellij.uiDesigner.impl.ErrorInfo;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.impl.quickFixes.QuickFixManager;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;

/**
 * @author yole
 */
public class QuickFixManagerImpl extends QuickFixManager<GlassLayer>
{
	public QuickFixManagerImpl(GuiEditor editor, GlassLayer component, JViewport viewPort)
	{
		super(editor, component, viewPort);
		editor.addComponentSelectionListener(new ComponentSelectionListener()
		{
			@Override
			public void selectedComponentChanged(GuiEditor source)
			{
				hideIntentionHint();
				updateIntentionHintVisibility();
			}
		});
	}

	@Override
	@Nonnull
	protected ErrorInfo[] getErrorInfos()
	{
		ArrayList<RadComponent> list = FormEditingUtil.getSelectedComponents(getEditor());
		if(list.size() != 1)
		{
			return ErrorInfo.EMPTY_ARRAY;
		}
		return ErrorAnalyzer.getAllErrorsForComponent(list.get(0));
	}

	@Override
	protected Rectangle getErrorBounds()
	{
		ArrayList<RadComponent> list = FormEditingUtil.getSelectedComponents(getEditor());
		if(list.size() != 1)
		{
			return null;
		}
		RadComponent c = list.get(0);
		return SwingUtilities.convertRectangle(c.getDelegee().getParent(), c.getBounds(), getEditor().getGlassLayer());
	}

	@Override
	protected Rectangle getHintClipRect(JViewport viewPort)
	{
		// allow some overlap with editor bounds
		Rectangle rc = viewPort.getViewRect();
		rc.grow(4, 4);
		return rc;
	}
}
