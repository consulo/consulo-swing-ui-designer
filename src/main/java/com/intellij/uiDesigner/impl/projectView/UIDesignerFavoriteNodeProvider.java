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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.bookmark.ui.view.BookmarkNodeProvider;
import consulo.dataContext.DataContext;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author yole
 */
@ExtensionImpl
public class UIDesignerFavoriteNodeProvider implements BookmarkNodeProvider
{
	@Override
	@Nullable
	public Collection<AbstractTreeNode> getFavoriteNodes(DataContext context, ViewSettings viewSettings)
	{
		Project project = context.getData(Project.KEY);
		if(project == null)
		{
			return null;
		}
		Form[] forms = context.getData(Form.DATA_KEY);
		if(forms != null)
		{
			Collection<AbstractTreeNode> result = new ArrayList<>();
			Set<PsiClass> bindClasses = new HashSet<>();
			for(Form form : forms)
			{
				PsiClass classToBind = form.getClassToBind();
				if(classToBind != null)
				{
					if(bindClasses.contains(classToBind))
					{
						continue;
					}
					bindClasses.add(classToBind);
					result.add(FormNode.constructFormNode(classToBind, project, viewSettings));
				}
			}
			if(!result.isEmpty())
			{
				return result;
			}
		}

		VirtualFile vFile = context.getData(PlatformDataKeys.VIRTUAL_FILE);
		if(vFile != null)
		{
			FileType fileType = vFile.getFileType();
			if(fileType.equals(GuiFormFileType.INSTANCE))
			{
				PsiFile formFile = PsiManager.getInstance(project).findFile(vFile);
				if(formFile == null)
				{
					return null;
				}
				String text = formFile.getText();
				String className;
				try
				{
					className = Utils.getBoundClassName(text);
				}
				catch(Exception e)
				{
					return null;
				}
				if(className == null)
				{
					return null;
				}
				PsiClass classToBind = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
				if(classToBind != null)
				{
					Form form = new Form(classToBind);
					AbstractTreeNode node = new FormNode(project, form, viewSettings);
					return Collections.singletonList(node);
				}
			}
		}

		return null;
	}

	public boolean elementContainsFile(Object element, VirtualFile vFile)
	{
		if(element instanceof Form)
		{
			Form form = (Form) element;
			return form.containsFile(vFile);
		}
		return false;
	}

	public int getElementWeight(Object element, boolean isSortByType)
	{
		if(element instanceof Form)
		{
			return 9;
		}
		return -1;
	}

	@Nullable
	public String getElementLocation(Object element)
	{
		if(element instanceof Form)
		{
			PsiFile[] psiFiles = ((Form) element).getFormFiles();
			VirtualFile vFile = null;
			if(psiFiles.length > 0)
			{
				vFile = psiFiles[0].getVirtualFile();
			}
			if(vFile != null)
			{
				return vFile.getPresentableUrl();
			}
		}
		return null;
	}

	public boolean isInvalidElement(Object element)
	{
		if(element instanceof Form)
		{
			return !((Form) element).isValid();
		}
		return false;
	}

	@Nonnull
	@NonNls
	public String getFavoriteTypeId()
	{
		return "form";
	}

	@Nullable
	@NonNls
	public String getElementUrl(Object element)
	{
		if(element instanceof Form)
		{
			Form form = (Form) element;
			return form.getClassToBind().getQualifiedName();
		}
		return null;
	}

	public String getElementModuleName(Object element)
	{
		if(element instanceof Form)
		{
			Form form = (Form) element;
			Module module = ModuleUtilCore.findModuleForPsiElement(form.getClassToBind());
			return module != null ? module.getName() : null;
		}
		return null;
	}

	public Object[] createPathFromUrl(Project project, String url, String moduleName)
	{
		PsiManager psiManager = PsiManager.getInstance(project);
		PsiClass classToBind = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(url, GlobalSearchScope.allScope(project));
		if(classToBind == null)
		{
			return null;
		}
		return new Object[]{new Form(classToBind)};
	}
}
