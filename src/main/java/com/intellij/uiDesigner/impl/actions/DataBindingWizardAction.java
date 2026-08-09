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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.wizard.DataBindingWizard;
import com.intellij.uiDesigner.impl.wizard.Generator;
import com.intellij.uiDesigner.impl.wizard.WizardData;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DataBindingWizardAction extends AnAction implements AnActionWithSyncUpdate
{
	private static final Logger LOG = Logger.getInstance(DataBindingWizardAction.class);

	@Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e)
	{
		Project project;
		VirtualFile formFile;
		GuiEditor editor = FormEditingUtil.getActiveEditor(e.getDataContext());
		assert editor != null;
		project = editor.getProject();
		formFile = editor.getFile();

		try
		{
			WizardData wizardData = new WizardData(project, formFile);


			Module module = ModuleUtilCore.findModuleForFile(formFile, wizardData.myProject);
			LOG.assertTrue(module != null);

			LwRootContainer[] rootContainer = new LwRootContainer[1];
			Generator.exposeForm(wizardData.myProject, formFile, rootContainer);
			String classToBind = rootContainer[0].getClassToBind();
			if(classToBind == null)
			{
				Messages.showInfoMessage(
					project,
					UIDesignerLocalize.infoFormNotBound().get(),
					UIDesignerLocalize.titleDataBindingWizard().get()
				);
				return;
			}

			PsiClass boundClass = FormEditingUtil.findClassToBind(module, classToBind);
			if(boundClass == null)
			{
				Messages.showErrorDialog(
					project,
					UIDesignerLocalize.errorBoundToNotFoundClass(classToBind).get(),
					UIDesignerLocalize.titleDataBindingWizard().get()
				);
				return;
			}

			Generator.prepareWizardData(wizardData, boundClass);

			if(!hasBinding(rootContainer[0]))
			{
				Messages.showInfoMessage(
					project,
					UIDesignerLocalize.infoNoBoundComponents().get(),
					UIDesignerLocalize.titleDataBindingWizard().get()
				);
				return;
			}

			if(!wizardData.myBindToNewBean)
			{
                String[] variants = new String[]{
					UIDesignerLocalize.actionAlterDataBinding().get(),
					UIDesignerLocalize.actionBindToAnotherBean().get(),
					CommonLocalize.buttonCancel().get()
				};
                int result = Messages.showYesNoCancelDialog(
					project,
					UIDesignerLocalize.infoDataBindingRegenerate(wizardData.myBeanClass.getQualifiedName()).get(),
					UIDesignerLocalize.titleDataBinding().get(),
					variants[0],
                    variants[1],
                    variants[2],
                    UIUtil.getQuestionIcon()
				);
				if(result == 0)
				{
					// do nothing here
				}
				else if(result == 1)
				{
					wizardData.myBindToNewBean = true;
				}
				else
				{
					return;
				}
			}

			DataBindingWizard wizard = new DataBindingWizard(project, formFile, wizardData);
			wizard.show();
		}
		catch(Generator.MyException exc)
		{
			Messages.showErrorDialog(
				project,
				exc.getMessage(),
				CommonLocalize.titleError().get()
			);
		}
	}

	@Override
	public void update(AnActionEvent e)
	{
		e.getPresentation().setVisible(FormEditingUtil.getActiveEditor(e.getDataContext()) != null);
	}


	private static boolean hasBinding(LwComponent component)
	{
		if(component.getBinding() != null)
		{
			return true;
		}

		if(component instanceof LwContainer)
		{
			LwContainer container = (LwContainer) component;
			for(int i = 0; i < container.getComponentCount(); i++)
			{
				if(hasBinding((LwComponent) container.getComponent(i)))
				{
					return true;
				}
			}
		}

		return false;
	}
}
