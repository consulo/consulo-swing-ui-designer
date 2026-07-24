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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.document.util.TextRange;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class FormPackageReference extends ReferenceInForm
{
	protected FormPackageReference(PsiPlainTextFile file, TextRange range)
	{
		super(file, range);
	}

	public PsiElement resolve()
	{
		Project project = myFile.getProject();
		String text = getRangeText().replace('/', '.');
		return JavaPsiFacade.getInstance(project).findPackage(text);
	}

	@Override
	public boolean isReferenceTo(PsiElement element)
	{
		if(!(element instanceof PsiJavaPackage))
		{
			return false;
		}
		String qName = ((PsiJavaPackage) element).getQualifiedName().replace('.', '/');
		String rangeText = getRangeText();
		return qName.equals(rangeText);
	}

	@Override
	public PsiElement handleElementRename(String newElementName)
	{
		String s = getRangeText();
		int pos = s.lastIndexOf("/");
		if(pos < 0)
		{
			updateRangeText(newElementName);
		}
		else
		{
			updateRangeText(s.substring(0, pos + 1) + newElementName);
		}
		return myFile;
	}

	public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException
	{
		return myFile;
	}
}
