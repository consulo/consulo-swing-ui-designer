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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.10.2006
 * Time: 17:53:16
 */
package com.intellij.uiDesigner.impl.binding;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import consulo.document.util.TextRange;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class FormEnumConstantReference extends ReferenceInForm
{
	private final PsiClassType myEnumClass;

	protected FormEnumConstantReference(PsiPlainTextFile file, TextRange range, PsiClassType enumClass)
	{
		super(file, range);
		myEnumClass = enumClass;
	}

	@Nullable
	public PsiElement resolve()
	{
		PsiClass enumClass = myEnumClass.resolve();
		if(enumClass == null)
		{
			return null;
		}
		return enumClass.findFieldByName(getRangeText(), false);
	}

	public PsiElement bindToElement(@Nonnull PsiElement element) throws consulo.language.util.IncorrectOperationException
	{
		throw new IncorrectOperationException();
	}
}
