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

import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyInspector;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyInspectorTable;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;

import java.util.List;

/**
 * @author yole
 */
public class ResetValueAction extends AbstractGuiEditorAction
{
	private static final Logger LOG = Logger.getInstance(ResetValueAction.class);

	@Override
	protected void actionPerformed(GuiEditor editor, List<RadComponent> selection, AnActionEvent e)
	{
		PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
		assert inspector != null;
		Property property = inspector.getSelectedProperty();
		assert property != null;
		doResetValue(selection, property, editor);
	}

	public static void doResetValue(List<RadComponent> selection, Property property, GuiEditor editor)
	{
		try
		{
			if(!editor.ensureEditable())
			{
				return;
			}
			PropertyInspector propertyInspector = DesignerToolWindowManager.getInstance(editor).getPropertyInspector();
			if(propertyInspector.isEditing())
			{
				propertyInspector.stopEditing();
			}
			//noinspection unchecked
			for(RadComponent component : selection)
			{
				//noinspection unchecked
				if(property.isModified(component))
				{
					//noinspection unchecked
					property.resetValue(component);
					component.getDelegee().invalidate();
				}
			}
			editor.refreshAndSave(false);
			propertyInspector.repaint();
		}
		catch(Exception e1)
		{
			LOG.error(e1);
		}
	}

	@Override
    @RequiredUIAccess
	protected void update(GuiEditor editor, List<RadComponent> selection, AnActionEvent e)
	{
		PropertyInspectorTable inspector = e.getData(PropertyInspectorTable.DATA_KEY);
		if(inspector != null)
		{
			Property selectedProperty = inspector.getSelectedProperty();
			//noinspection unchecked
			e.getPresentation().setEnabled(selectedProperty != null &&
					selection.size() > 0 &&
					inspector.isModifiedForSelection(selectedProperty));
		}
		else
		{
			e.getPresentation().setEnabled(false);
		}
	}
}
