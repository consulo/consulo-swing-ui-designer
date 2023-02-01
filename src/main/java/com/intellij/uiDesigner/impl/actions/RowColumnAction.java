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

package com.intellij.uiDesigner.impl.actions;

import com.intellij.uiDesigner.impl.CaptionSelection;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
public abstract class RowColumnAction extends AnAction
{
	private final String myColumnText;
	private final Image myColumnIcon;
	private final String myRowText;
	private final Image myRowIcon;

	public RowColumnAction(final String columnText, @Nullable final Image columnIcon, final String rowText, @Nullable final Image rowIcon)
	{
		myColumnText = columnText;
		myColumnIcon = columnIcon;
		myRowText = rowText;
		myRowIcon = rowIcon;
	}

	@RequiredUIAccess
	@Override
	public void actionPerformed(@Nonnull final AnActionEvent e)
	{
		GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
		CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
		if(editor == null || selection == null || !editor.ensureEditable())
		{
			return;
		}
		actionPerformed(selection);
		selection.getContainer().revalidate();
		editor.refreshAndSave(true);
	}

	protected abstract void actionPerformed(CaptionSelection selection);

	@RequiredUIAccess
	@Override
	public void update(@Nonnull final AnActionEvent e)
	{
		final Presentation presentation = e.getPresentation();
		CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
		if(selection == null)
		{
			presentation.setEnabled(false);
		}
		else
		{
			presentation.setEnabled(selection.getContainer() != null && selection.getFocusedIndex() >= 0);
			if(!selection.isRow())
			{
				presentation.setText(myColumnText);
				if(myColumnIcon != null)
				{
					presentation.setIcon(myColumnIcon);
				}
			}
			else
			{
				presentation.setText(myRowText);
				if(myRowIcon != null)
				{
					presentation.setIcon(myRowIcon);
				}
			}
		}
	}
}
