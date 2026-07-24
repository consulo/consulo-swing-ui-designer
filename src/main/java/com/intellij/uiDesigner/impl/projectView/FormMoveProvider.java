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

package com.intellij.uiDesigner.impl.projectView;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.move.MoveHandlerDelegate;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nullable;
import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class FormMoveProvider extends MoveHandlerDelegate
{
	private static final Logger LOG = Logger.getInstance(FormMoveProvider.class);

	@Override
	public boolean canMove(DataContext dataContext)
	{
		Form[] forms = dataContext.getData(Form.DATA_KEY);
		return forms != null && forms.length > 0;
	}

	@Override
	public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources)
	{
		return MoveFilesOrDirectoriesHandler.isValidTarget(psiElement);
	}

	@Override
	public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer)
	{
		return false;
	}

	@Override
	public void collectFilesOrDirsFromContext(DataContext dataContext, Set<PsiElement> filesOrDirs)
	{
		Form[] forms = dataContext.getData(Form.DATA_KEY);
		LOG.assertTrue(forms != null);
		PsiClass[] classesToMove = new PsiClass[forms.length];
		PsiFile[] filesToMove = new PsiFile[forms.length];
		for(int i = 0; i < forms.length; i++)
		{
			classesToMove[i] = forms[i].getClassToBind();
			if(classesToMove[i] != null)
			{
				filesOrDirs.add(classesToMove[i].getContainingFile());
			}
			filesToMove[i] = forms[i].getFormFiles()[0];
			if(filesToMove[i] != null)
			{
				filesOrDirs.add(filesToMove[i]);
			}
		}
	}


	@Override
	public boolean isMoveRedundant(PsiElement source, PsiElement target)
	{
		if(source instanceof PsiFile && source.getParent() == target)
		{
			VirtualFile virtualFile = ((PsiFile) source).getVirtualFile();
			if(virtualFile != null && virtualFile.getFileType() instanceof GuiFormFileType)
			{
				return true;
			}
		}
		return super.isMoveRedundant(source, target);
	}
}
