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
package com.intellij.uiDesigner.impl.inspections;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.impl.StringDescriptorManager;
import com.intellij.uiDesigner.impl.SwingProperties;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.impl.propertyInspector.editors.string.StringEditorDialog;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class FormInspectionUtil
{
	private FormInspectionUtil()
	{
	}

	public static boolean isComponentClass(consulo.module.Module module, IComponent component,
                                           Class componentClass)
	{
		GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
		PsiManager psiManager = PsiManager.getInstance(module.getProject());
		PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
		if(aClass != null)
		{
			PsiClass labelClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(componentClass.getName(), scope);
			if(labelClass != null && InheritanceUtil.isInheritorOrSelf(aClass, labelClass, true))
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	public static String getText(@Nonnull Module module, IComponent component)
	{
		IProperty textProperty = findProperty(component, SwingProperties.TEXT);
		if(textProperty != null)
		{
			Object propValue = textProperty.getPropertyValue(component);
			String value = null;
			if(propValue instanceof StringDescriptor)
			{
				StringDescriptor descriptor = (StringDescriptor) propValue;
				if(component instanceof RadComponent)
				{
					value = StringDescriptorManager.getInstance(module).resolve((RadComponent) component, descriptor);
				}
				else
				{
					value = StringDescriptorManager.getInstance(module).resolve(descriptor, null);
				}
			}
			else if(propValue instanceof String)
			{
				value = (String) propValue;
			}
			if(value != null)
			{
				return value;
			}
		}
		return null;
	}

	@Nullable
	public static IProperty findProperty(IComponent component, String name)
	{
		IProperty[] props = component.getModifiedProperties();
		for(IProperty prop : props)
		{
			if(prop.getName().equals(name))
			{
				return prop;
			}
		}
		return null;
	}

	public static void updateStringPropertyValue(GuiEditor editor,
												 RadComponent component,
												 IntroStringProperty prop,
												 StringDescriptor descriptor,
												 String result)
	{
		if(descriptor.getBundleName() == null)
		{
			prop.setValueEx(component, StringDescriptor.create(result));
		}
		else
		{
			String newKeyName = StringEditorDialog.saveModifiedPropertyValue(editor.getModule(), descriptor,
					editor.getStringDescriptorLocale(), result,
					editor.getPsiFile());
			if(newKeyName != null)
			{
				prop.setValueEx(component, new StringDescriptor(descriptor.getBundleName(), newKeyName));
			}
		}
		editor.refreshAndSave(false);
	}
}
