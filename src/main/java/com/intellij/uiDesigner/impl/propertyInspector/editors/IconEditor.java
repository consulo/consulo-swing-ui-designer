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
package com.intellij.uiDesigner.impl.propertyInspector.editors;

import com.intellij.java.language.util.TreeClassChooserFactory;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.ImageFileFilter;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroIconProperty;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.ide.impl.idea.openapi.module.ResourceFileUtil;
import consulo.language.editor.ui.TreeFileChooser;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.virtualFileSystem.VirtualFile;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class IconEditor extends PropertyEditor<IconDescriptor>
{
	private final TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
	private IconDescriptor myValue;
	private RadComponent myComponent;

	public IconEditor()
	{
		myTextField.getTextField().setBorder(null);
		myTextField.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(getModule().getProject());
				PsiFile iconFile = null;
				if(myValue != null)
				{
					VirtualFile iconVFile = ResourceFileUtil.findResourceFileInScope(myValue.getIconPath(), getModule().getProject(),
							GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(getModule(), true));
					if(iconVFile != null)
					{
						iconFile = PsiManager.getInstance(getModule().getProject()).findFile(iconVFile);
					}
				}
				TreeFileChooser fileChooser = factory.createFileChooser(UIDesignerBundle.message("title.choose.icon.file"), iconFile,
						null, new ImageFileFilter(getModule()), false, true);
				fileChooser.showDialog();
				PsiFile file = fileChooser.getSelectedFile();
				if(file != null)
				{
					String resourceName = FormEditingUtil.buildResourceName(file);
					if(resourceName != null)
					{
						IconDescriptor descriptor = new IconDescriptor(resourceName);
						IntroIconProperty.loadIconFromFile(file.getVirtualFile(), descriptor);
						myValue = descriptor;
						myTextField.setText(descriptor.getIconPath());
					}
				}
			}
		});
	}

	private Module getModule()
	{
		return myComponent.getModule();
	}

	public IconDescriptor getValue() throws Exception
	{
		if(myTextField.getText().length() == 0)
		{
			return null;
		}
		IconDescriptor descriptor = new IconDescriptor(myTextField.getText());
		IntroIconProperty.ensureIconLoaded(getModule(), descriptor);
		return descriptor;
	}

	public JComponent getComponent(RadComponent component, IconDescriptor value, InplaceContext inplaceContext)
	{
		myValue = value;
		myComponent = component;
		if(myValue != null)
		{
			myTextField.setText(myValue.getIconPath());
		}
		else
		{
			myTextField.setText("");
		}
		return myTextField;
	}

	public void updateUI()
	{
		SwingUtilities.updateComponentTreeUI(myTextField);
	}
}
