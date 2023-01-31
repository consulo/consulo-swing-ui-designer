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

package com.intellij.uiDesigner.actions;

import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import consulo.ui.annotation.RequiredUIAccess;
import icons.UIDesignerIcons;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
public final class DeleteAction extends AnAction
{
	public DeleteAction()
	{
		getTemplatePresentation().setIcon(UIDesignerIcons.DeleteCell);
	}

	@RequiredUIAccess
	@Override
	public void actionPerformed(@Nonnull final AnActionEvent e)
	{
		final GuiEditor editor = FormEditingUtil.getEditorFromContext(e.getDataContext());
		CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
		if(editor == null || selection == null || selection.getFocusedIndex() < 0)
		{
			return;
		}
		FormEditingUtil.deleteRowOrColumn(editor, selection.getContainer(), selection.getSelection(), selection.isRow());
		selection.getContainer().revalidate();
	}

	@RequiredUIAccess
	@Override
	public void update(@Nonnull final AnActionEvent e)
	{
		final Presentation presentation = e.getPresentation();
		CaptionSelection selection = e.getData(CaptionSelection.DATA_KEY);
		if(selection == null || selection.getContainer() == null)
		{
			presentation.setVisible(false);
			return;
		}
		presentation.setVisible(true);
		if(selection.getSelection().length > 1)
		{
			presentation.setText(!selection.isRow() ? UIDesignerBundle.message("action.delete.columns") : UIDesignerBundle.message("action.delete.rows"));
		}
		else
		{
			presentation.setText(!selection.isRow() ? UIDesignerBundle.message("action.delete.column") : UIDesignerBundle.message("action.delete.row"));
		}

		int minCellCount = selection.getContainer().getGridLayoutManager().getMinCellCount();
		if(selection.getContainer().getGridCellCount(selection.isRow()) - selection.getSelection().length < minCellCount)
		{
			presentation.setEnabled(false);
		}
		else if(selection.getFocusedIndex() < 0)
		{
			presentation.setEnabled(false);
		}
		else
		{
			presentation.setEnabled(true);
		}
	}
}
