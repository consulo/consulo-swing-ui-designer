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

import com.intellij.java.analysis.impl.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.actions.ResetValueAction;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;

/**
 * @author yole
 */
@ExtensionImpl
public class Java15FormInspection extends BaseFormInspection
{
	public Java15FormInspection()
	{
		super("Since15");
	}

	@Override
	protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector)
	{
		GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
		PsiManager psiManager = PsiManager.getInstance(module.getProject());
		PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
		if(aClass == null)
		{
			return;
		}

		for(IProperty prop : component.getModifiedProperties())
		{
			PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, prop.getName(), false, true);
			if(getter == null)
			{
				continue;
			}
			LanguageLevel languageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);
			if(Java15APIUsageInspection.getLastIncompatibleLanguageLevel(getter, languageLevel) != null)
			{
				registerError(component, collector, prop, "@since " + Java15APIUsageInspection.getShortName(languageLevel));
			}
		}
	}

	private void registerError(
			IComponent component, FormErrorCollector collector, final IProperty prop, @NonNls String api)
	{
		collector.addError(getID(), component, prop, InspectionsBundle.message("inspection.1.5.problem.descriptor", api),
				new EditorQuickFixProvider()
				{
					@Override
					public QuickFix createQuickFix(GuiEditor editor, RadComponent component)
					{
						return new RemovePropertyFix(editor, component, (Property) prop);
					}
				});
	}

	private static class RemovePropertyFix extends QuickFix
	{
		private final Property myProperty;

		public RemovePropertyFix(GuiEditor editor, RadComponent component, Property property)
		{
			super(editor, UIDesignerBundle.message("remove.property.quickfix"), component);
			myProperty = property;
		}


		@Override
		public void run()
		{
			ResetValueAction.doResetValue(Collections.singletonList(myComponent), myProperty, myEditor);
		}
	}
}
