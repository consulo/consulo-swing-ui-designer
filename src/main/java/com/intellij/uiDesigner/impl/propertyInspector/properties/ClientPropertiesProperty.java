/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl.propertyInspector.properties;

import com.intellij.uiDesigner.impl.clientProperties.ClientPropertiesManager;
import com.intellij.uiDesigner.impl.clientProperties.ConfigureClientPropertiesDialog;
import com.intellij.uiDesigner.impl.propertyInspector.*;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class ClientPropertiesProperty extends ReadOnlyProperty
{
	private final Project myProject;

	public static ClientPropertiesProperty getInstance(Project project)
	{
		return ServiceManager.getService(project, ClientPropertiesProperty.class);
	}

	private final PropertyRenderer myRenderer = new LabelPropertyRenderer(UIDesignerLocalize.clientPropertiesConfigure().get());

	private final PropertyEditor myEditor = new MyPropertyEditor();

	@Inject
	public ClientPropertiesProperty(Project project)
	{
		super(null, "Client Properties");
		myProject = project;
	}

    @Nonnull
    @Override
	public PropertyRenderer getRenderer()
	{
		return myRenderer;
	}

	@Override
    public PropertyEditor getEditor()
	{
		return myEditor;
	}

	@Nonnull
	@Override
	public Property[] getChildren(RadComponent component)
	{
		ClientPropertiesManager manager = ClientPropertiesManager.getInstance(component.getProject());
		List<ClientPropertiesManager.ClientProperty> props = manager.getClientProperties(component.getComponentClass());
		Property[] result = new Property[props.size()];
		for(int i = 0; i < props.size(); i++)
		{
			result[i] = new ClientPropertyProperty(this, props.get(i).getName(), props.get(i).getValueClass());
		}
		return result;
	}

	private class MyPropertyEditor extends PropertyEditor
	{
		private final TextFieldWithBrowseButton myTf = new TextFieldWithBrowseButton();

		public MyPropertyEditor()
		{
			myTf.setText(UIDesignerLocalize.clientPropertiesConfigure().get());
			myTf.getTextField().setEditable(false);
			myTf.getTextField().setBorder(null);
			myTf.getTextField().setForeground(JBColor.foreground());
			myTf.addActionListener(e -> showClientPropertiesDialog());
		}

		@RequiredUIAccess
        private void showClientPropertiesDialog()
		{
			ConfigureClientPropertiesDialog dlg = new ConfigureClientPropertiesDialog(myProject);
			dlg.show();
			if(dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE)
			{
				dlg.save();
				fireValueCommitted(true, false);
			}
		}

		@Override
        public Object getValue() throws Exception
		{
			return null;
		}

		@Override
        public JComponent getComponent(RadComponent component, Object value, InplaceContext inplaceContext)
		{
			return myTf;
		}

		@Override
        public void updateUI()
		{
			SwingUtilities.updateComponentTreeUI(myTf);
		}
	}

	@Override
	public boolean needRefreshPropertyList()
	{
		return true;
	}
}
