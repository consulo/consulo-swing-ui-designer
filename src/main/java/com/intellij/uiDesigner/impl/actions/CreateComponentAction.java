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

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.*;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;

import java.awt.*;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author yole
 */
public class CreateComponentAction extends AbstractGuiEditorAction
{
	private ComponentItem myLastCreatedComponent = null;

	@Override
    protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e)
	{
        Predicate<ComponentItem> processor = selectedValue -> {
            if(selectedValue != null)
            {
                myLastCreatedComponent = selectedValue;
                editor.getMainProcessor().startInsertProcessor(selectedValue, getCreateLocation(editor, selection));
            }
            return true;
        };

		PaletteListPopupStep step = new PaletteListPopupStep(
		    editor,
            myLastCreatedComponent,
            processor,
            UIDesignerLocalize.createComponentTitle()
        );
		final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);

		if(selection.size() > 0)
		{
			FormEditingUtil.showPopupUnderComponent(listPopup, selection.get(0));
		}
		else
		{
			listPopup.showInCenterOf(editor.getRootContainer().getDelegee());
		}
	}

	private static ComponentDropLocation getCreateLocation(final GuiEditor editor, final List<RadComponent> selection)
	{
		ComponentDropLocation dropLocation = null;
		if(selection.size() > 0)
		{
			RadComponent component = selection.get(0);
			final RadContainer container = component.getParent();
			if(container.getLayoutManager().isGrid())
			{
				GridConstraints c = component.getConstraints();
				// try to insert in empty cell to the right or below the component; if not found -
				// insert row below selected component
				int nextCol = FormEditingUtil.adjustForGap(component.getParent(), c.getColumn() + c.getColSpan(), false, 1);
				int nextRow = FormEditingUtil.adjustForGap(component.getParent(), c.getRow() + c.getRowSpan(), true, 1);
				if(nextCol < container.getGridColumnCount() &&
						container.getComponentAtGrid(c.getRow(), nextCol) == null)
				{
					dropLocation = new GridDropLocation(container, c.getRow(), nextCol);
				}
				else if(nextRow < container.getGridRowCount() &&
						container.getComponentAtGrid(nextRow, c.getColumn()) == null)
				{
					dropLocation = new GridDropLocation(container, nextRow, c.getColumn());
				}
				else
				{
					dropLocation = new GridInsertLocation(container, c.getRow() + c.getRowSpan() - 1, c.getColumn(),
							GridInsertMode.RowAfter);
				}
			}
		}
		if(dropLocation == null)
		{
			final Point mousePosition = editor.getMainProcessor().getLastMousePosition();
			if(mousePosition != null)
			{
				RadContainer container = GridInsertProcessor.getDropTargetContainer(editor.getRootContainer(), mousePosition);
				if(container == null)
				{
					container = editor.getRootContainer();
				}
				if(container instanceof RadRootContainer && container.getComponentCount() == 1 &&
						container.getComponent(0) instanceof RadContainer)
				{
					RadContainer childContainer = (RadContainer) container.getComponent(0);
					dropLocation = childContainer.getDropLocation(null);
				}
				else
				{
					dropLocation = GridInsertProcessor.getDropLocation(editor.getRootContainer(), mousePosition);
				}
			}
			else
			{
				final RadRootContainer container = editor.getRootContainer();
				if(container.getComponentCount() == 1 && container.getComponent(0) instanceof RadContainer)
				{
					RadContainer childContainer = (RadContainer) container.getComponent(0);
					dropLocation = childContainer.getDropLocation(null);
				}
				else
				{
					dropLocation = container.getDropLocation(null);
				}
			}
		}
		return dropLocation;
	}
}
