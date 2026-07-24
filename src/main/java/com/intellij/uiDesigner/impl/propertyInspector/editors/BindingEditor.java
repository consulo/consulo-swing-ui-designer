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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiType;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.impl.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.impl.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadErrorComponent;
import com.intellij.uiDesigner.impl.radComponents.RadHSpacer;
import com.intellij.uiDesigner.impl.radComponents.RadVSpacer;
import com.intellij.uiDesigner.lw.IRootContainer;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CommonShortcuts;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingEditor extends ComboBoxPropertyEditor<String>
{
	public BindingEditor(final Project project)
	{
		myCbx.setEditable(true);
		final JComponent editorComponent = (JComponent) myCbx.getEditor().getEditorComponent();
		editorComponent.setBorder(null);

		myCbx.addActionListener(e -> fireValueCommitted(true, false));

		new AnAction()
		{
			@Override
            @RequiredUIAccess
			public void actionPerformed(final AnActionEvent e)
			{
				if(!myCbx.isPopupVisible())
				{
					fireEditingCancelled();
					SwingUtilities.invokeLater(
					    () -> DesignerToolWindowManager.getInstance(DesignerToolWindowManager.getInstance(project).getActiveFormEditor())
                            .getPropertyInspector().requestFocus()
                    );
				}
			}
		}.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myCbx);
	}

	private static String[] getFieldNames(final RadComponent component, final String currentName)
	{
		List<String> result = new ArrayList<>();
		if(currentName != null)
		{
			result.add(currentName);
		}

		final IRootContainer root = FormEditingUtil.getRoot(component);
		final String className = root.getClassToBind();
		if(className == null)
		{
			return ArrayUtil.toStringArray(result);
		}

		final PsiClass aClass = FormEditingUtil.findClassToBind(component.getModule(), className);
		if(aClass == null)
		{
			return ArrayUtil.toStringArray(result);
		}

		final PsiField[] fields = aClass.getFields();

		for(final PsiField field : fields)
		{
			if(field.isStatic())
			{
				continue;
			}

			final String fieldName = field.getName();

			if(Comparing.equal(currentName, fieldName))
			{
				continue;
			}

			if(!FormEditingUtil.isBindingUnique(component, fieldName, root))
			{
				continue;
			}

			final String componentClassName;
			if(component instanceof RadErrorComponent)
			{
				componentClassName = component.getComponentClassName();
			}
			else if(component instanceof RadHSpacer || component instanceof RadVSpacer)
			{
				componentClassName = Spacer.class.getName();
			}
			else
			{
				componentClassName = component.getComponentClass().getName();
			}

			final PsiType componentType;
			try
			{
				componentType = JavaPsiFacade.getInstance(component.getProject()).getElementFactory().createTypeFromText(componentClassName, null);
			}
			catch(IncorrectOperationException e)
			{
				continue;
			}

			final PsiType fieldType = field.getType();
			if(!fieldType.isAssignableFrom(componentType))
			{
				continue;
			}

			result.add(fieldName);
		}

		String text = FormInspectionUtil.getText(component.getModule(), component);
		if(text != null)
		{
			String binding = BindingProperty.suggestBindingFromText(component, text);
			if(binding != null && !result.contains(binding))
			{
				result.add(binding);
			}
		}

		final String[] names = ArrayUtil.toStringArray(result);
		Arrays.sort(names);
		return names;
	}

	@Override
	public String getValue() throws Exception
	{
		final String value = super.getValue();
		return value != null ? value.replace('$', '.') : null; // PSI works only with dots
	}

	@Override
	public JComponent getComponent(final RadComponent component, final String value, final InplaceContext inplaceContext)
	{
		final String[] fieldNames = getFieldNames(component, value);
		myCbx.setModel(new DefaultComboBoxModel<>(fieldNames));
		myCbx.setSelectedItem(value);
		return myCbx;
	}
}
