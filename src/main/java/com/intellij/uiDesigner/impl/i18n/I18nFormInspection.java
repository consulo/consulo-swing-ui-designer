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
package com.intellij.uiDesigner.impl.i18n;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.inspections.EditorQuickFixProvider;
import com.intellij.uiDesigner.impl.inspections.FormErrorCollector;
import com.intellij.uiDesigner.impl.inspections.StringDescriptorInspection;
import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.propertyInspector.properties.BorderProperty;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.radComponents.RadTabbedPane;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.properties.impl.i18n.I18nInspection;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class I18nFormInspection extends StringDescriptorInspection
{
	public I18nFormInspection()
	{
		super("HardCodedStringLiteral");
	}

	protected void checkStringDescriptor(Module module,
                                         IComponent component,
                                         final IProperty prop,
                                         StringDescriptor descriptor,
                                         FormErrorCollector collector)
	{
		if(isHardCodedStringDescriptor(descriptor))
		{
			if(isPropertyDescriptor(prop))
			{
				if(isSetterNonNls(module.getProject(),
						GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module),
						component.getComponentClassName(), prop.getName()))
				{
					return;
				}
			}

			EditorQuickFixProvider provider;

			if(prop.getName().equals(BorderProperty.NAME))
			{
				provider = new EditorQuickFixProvider()
				{
					public QuickFix createQuickFix(GuiEditor editor, RadComponent component)
					{
						return new I18nizeFormBorderQuickFix(editor, UIDesignerBundle.message("i18n.quickfix.border.title"),
								(RadContainer) component);
					}
				};
			}
			else if(prop.getName().equals(RadTabbedPane.TAB_TITLE_PROPERTY) || prop.getName().equals(RadTabbedPane.TAB_TOOLTIP_PROPERTY))
			{
				provider = new EditorQuickFixProvider()
				{
					public QuickFix createQuickFix(GuiEditor editor, RadComponent component)
					{
						return new I18nizeTabTitleQuickFix(editor, UIDesignerBundle.message("i18n.quickfix.tab.title", prop.getName()),
								component, prop.getName());
					}
				};
			}
			else
			{
				provider = new EditorQuickFixProvider()
				{
					public QuickFix createQuickFix(GuiEditor editor, RadComponent component)
					{
						return new I18nizeFormPropertyQuickFix(editor, UIDesignerBundle.message("i18n.quickfix.property", prop.getName()),
								component,
								(IntrospectedProperty) prop);
					}
				};
			}

			collector.addError(getID(), component, prop,
					UIDesignerBundle.message("inspection.i18n.message.in.form", descriptor.getValue()),
					provider);
		}
	}

	private static boolean isPropertyDescriptor(IProperty prop)
	{
		return !prop.getName().equals(BorderProperty.NAME) && !prop.getName().equals(RadTabbedPane.TAB_TITLE_PROPERTY) &&
				!prop.getName().equals(RadTabbedPane.TAB_TOOLTIP_PROPERTY);
	}

	private static boolean isHardCodedStringDescriptor(StringDescriptor descriptor)
	{
		if(descriptor.isNoI18n())
		{
			return false;
		}
		return descriptor.getBundleName() == null &&
				descriptor.getKey() == null &&
				StringUtil.containsAlphaCharacters(descriptor.getValue());
	}

	private static boolean isSetterNonNls(Project project, GlobalSearchScope searchScope,
                                          String componentClassName, String propertyName)
	{
		PsiClass componentClass = JavaPsiFacade.getInstance(project).findClass(componentClassName, searchScope);
		if(componentClass == null)
		{
			return false;
		}
		PsiMethod setter = PropertyUtil.findPropertySetter(componentClass, propertyName, false, true);
		if(setter != null)
		{
			PsiParameter[] parameters = setter.getParameterList().getParameters();
			if(parameters.length == 1 &&
					"java.lang.String".equals(parameters[0].getType().getCanonicalText()) &&
					AnnotationUtil.isAnnotated(parameters[0], AnnotationUtil.NON_NLS, false, true))
			{
				return true;
			}
		}

		return false;
	}

	@Nullable
	public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state)
	{
		if(file.getFileType().equals(GuiFormFileType.INSTANCE))
		{
			PsiDirectory directory = file.getContainingDirectory();
			if(directory != null && I18nInspection.isPackageNonNls(JavaDirectoryService.getInstance().getPackage(directory)))
			{
				return null;
			}
		}

		return super.checkFile(file, manager, isOnTheFly, state);
	}
}
