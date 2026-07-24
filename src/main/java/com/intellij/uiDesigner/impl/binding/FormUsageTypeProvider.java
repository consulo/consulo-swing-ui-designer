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

import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.usage.UsageType;
import consulo.usage.UsageTypeProvider;

import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class FormUsageTypeProvider implements UsageTypeProvider
{
	private static final UsageType FORM_USAGE_TYPE = new UsageType(UIDesignerBundle.message("form.usage.type"));

	@Override
	@Nullable
	public UsageType getUsageType(PsiElement element)
	{
		PsiFile psiFile = element.getContainingFile();
		if(psiFile.getFileType() == GuiFormFileType.INSTANCE)
		{
			return FORM_USAGE_TYPE;
		}
		return null;
	}
}