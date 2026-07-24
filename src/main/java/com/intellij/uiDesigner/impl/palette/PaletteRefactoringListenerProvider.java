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
package com.intellij.uiDesigner.impl.palette;

import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringElementListenerProvider;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PaletteRefactoringListenerProvider implements RefactoringElementListenerProvider
{
	private final UIDesignerPaletteProvider myUiDesignerPaletteProvider;
	private final Palette myPalette;

	@Inject
	public PaletteRefactoringListenerProvider(Project project, Palette palette)
	{
		myUiDesignerPaletteProvider = PaletteItemProvider.EP_NAME.findExtensionOrFail(project, UIDesignerPaletteProvider.class);
		myPalette = palette;
	}

	public RefactoringElementListener getListener(PsiElement element)
	{
		if(element instanceof PsiClass)
		{
			PsiClass psiClass = (PsiClass) element;
			String oldName = ClassUtil.getJVMClassName(psiClass);
			if(oldName != null)
			{
				ComponentItem item = myPalette.getItem(oldName);
				if(item != null)
				{
					return new MyRefactoringElementListener(item);
				}
			}
		}
		return null;
	}

	private class MyRefactoringElementListener extends RefactoringElementAdapter
	{
		private final ComponentItem myItem;

		public MyRefactoringElementListener(ComponentItem item)
		{
			myItem = item;
		}

		public void elementRenamedOrMoved(@Nonnull PsiElement newElement)
		{
			PsiClass psiClass = (PsiClass) newElement;
			String qName = ClassUtil.getJVMClassName(psiClass);
			if(qName != null)
			{
				myItem.setClassName(qName);
				myUiDesignerPaletteProvider.fireGroupsChanged();
			}
		}

		@Override
		public void undoElementMovedOrRenamed(@Nonnull PsiElement newElement, @Nonnull String oldQualifiedName)
		{
			myItem.setClassName(oldQualifiedName);
			myUiDesignerPaletteProvider.fireGroupsChanged();
		}
	}
}
