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

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.application.ApplicationManager;
import consulo.document.FileDocumentManager;
import consulo.java.properties.impl.i18n.JavaI18nizeQuickFixDialog;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class I18nizeFormQuickFix extends QuickFix
{
	private static final Logger LOG = Logger.getInstance(I18nizeFormQuickFix.class);

	public I18nizeFormQuickFix(GuiEditor editor, String name, RadComponent component)
	{
		super(editor, name, component);
	}

	public void run()
	{
		StringDescriptor descriptor = getStringDescriptorValue();
		final Project project = myEditor.getProject();

		PsiFile psiFile = myEditor.getPsiFile();

		if(!JavaI18nizeQuickFixDialog.isAvailable(myEditor.getPsiFile()))
		{
			return;
		}
		String initialValue = StringUtil.escapeStringCharacters(descriptor.getValue());
		final JavaI18nizeQuickFixDialog dialog = new JavaI18nizeQuickFixDialog(project, psiFile, null, initialValue, null, false, false)
		{
			protected String getDimensionServiceKey()
			{
				return "#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog_Form";
			}
		};
		dialog.show();
		if(!dialog.isOK())
		{
			return;
		}

		if(!myEditor.ensureEditable())
		{
			return;
		}
		final Collection<PropertiesFile> propertiesFiles = dialog.getAllPropertiesFiles();
		PropertiesFile aPropertiesFile = null;
		for(PropertiesFile file : propertiesFiles)
		{
			if(!FileModificationService.getInstance().prepareFileForWrite(file.getContainingFile()))
			{
				return;
			}
			if(aPropertiesFile == null)
			{
				aPropertiesFile = file;
			}
		}

		CommandProcessor.getInstance().executeCommand(project, new Runnable()
		{
			public void run()
			{
				ApplicationManager.getApplication().runWriteAction(new Runnable()
				{
					public void run()
					{
						try
						{
							I18nUtil.createProperty(project, propertiesFiles, dialog.getKey(), dialog.getValue());
						}
						catch(IncorrectOperationException e)
						{
							LOG.error(e);
						}
					}
				});
			}
		}, CodeInsightBundle.message("quickfix.i18n.command.name"), project);

		// saving files is necessary to ensure correct reload of properties files by UI Designer
		for(PropertiesFile file : propertiesFiles)
		{
			FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(project).getDocument(file.getContainingFile()));
		}

		if(aPropertiesFile != null)
		{
			ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
			String packageName = fileIndex.getPackageNameByDirectory(aPropertiesFile.getVirtualFile().getParent());
			if(packageName != null)
			{
				String bundleName;
				if(packageName.length() > 0)
				{
					bundleName = packageName + "." + aPropertiesFile.getResourceBundle().getBaseName();
				}
				else
				{
					bundleName = aPropertiesFile.getResourceBundle().getBaseName();
				}
				bundleName = bundleName.replace('.', '/');
				try
				{
					setStringDescriptorValue(new StringDescriptor(bundleName, dialog.getKey()));
				}
				catch(Exception e)
				{
					LOG.error(e);
				}
				myEditor.refreshAndSave(true);
			}
		}
	}

	protected abstract StringDescriptor getStringDescriptorValue();

	protected abstract void setStringDescriptorValue(StringDescriptor descriptor) throws Exception;
}
