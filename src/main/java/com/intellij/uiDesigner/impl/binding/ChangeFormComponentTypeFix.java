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
package com.intellij.uiDesigner.impl.binding;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.ReadonlyStatusHandler;

import javax.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 * Date: Jun 15, 2005
 */
public class ChangeFormComponentTypeFix implements SyntheticIntentionAction
{
	private final PsiPlainTextFile myFormFile;
	private final String myFieldName;
	private final String myComponentTypeToSet;

	public ChangeFormComponentTypeFix(PsiPlainTextFile formFile, String fieldName, PsiType componentTypeToSet)
	{
		myFormFile = formFile;
		myFieldName = fieldName;
		if(componentTypeToSet instanceof PsiClassType)
		{
			PsiClass psiClass = ((PsiClassType) componentTypeToSet).resolve();
			if(psiClass != null)
			{
				myComponentTypeToSet = ClassUtil.getJVMClassName(psiClass);
			}
			else
			{
				myComponentTypeToSet = ((PsiClassType) componentTypeToSet).rawType().getCanonicalText();
			}
		}
		else
		{
			myComponentTypeToSet = componentTypeToSet.getCanonicalText();
		}
	}

	@Nonnull
	public String getText()
	{
		return JavaQuickFixBundle.message("uidesigner.change.gui.component.type");
	}

	@Nonnull
	public String getFamilyName()
	{
		return getText();
	}

	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return true;
	}

	public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
	{
		CommandProcessor.getInstance().executeCommand(file.getProject(), new Runnable()
		{
			public void run()
			{
				final ReadonlyStatusHandler readOnlyHandler = ReadonlyStatusHandler.getInstance(myFormFile.getProject());
				final ReadonlyStatusHandler.OperationStatus status = readOnlyHandler.ensureFilesWritable(myFormFile.getVirtualFile());
				if(!status.hasReadonlyFiles())
				{
					FormReferenceProvider.setGUIComponentType(myFormFile, myFieldName, myComponentTypeToSet);
				}
			}
		}, getText(), null);
	}

	public boolean startInWriteAction()
	{
		return true;
	}
}
