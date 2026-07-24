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
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * @author yole
 */
@ExtensionImpl
public class FormClassIndex extends ScalarIndexExtension<String>
{
	@NonNls
	public static final ID<String, Void> NAME = ID.create("FormClassIndex");
	private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();
	private final MyInputFilter myInputFilter = new MyInputFilter();
	private final MyDataIndexer myDataIndexer = new MyDataIndexer();

	@Override
	@Nonnull
	public ID<String, Void> getName()
	{
		return NAME;
	}

	@Override
	@Nonnull
	public DataIndexer<String, Void, FileContent> getIndexer()
	{
		return myDataIndexer;
	}

	@Override
	public KeyDescriptor<String> getKeyDescriptor()
	{
		return myKeyDescriptor;
	}

	@Override
	public FileBasedIndex.InputFilter getInputFilter()
	{
		return myInputFilter;
	}

	@Override
	public boolean dependsOnFileContent()
	{
		return true;
	}

	@Override
	public int getVersion()
	{
		return 0;
	}

	private static class MyDataIndexer implements DataIndexer<String, Void, FileContent>
	{
		@Override
		@Nonnull
		public Map<String, Void> map(FileContent inputData)
		{
			String className = null;
			try
			{
				className = Utils.getBoundClassName(inputData.getContentAsText().toString());
			}
			catch(Exception e)
			{
				// ignore
			}
			if(className != null)
			{
				return Collections.singletonMap(className, null);
			}
			return Collections.emptyMap();
		}
	}

	private static class MyInputFilter implements FileBasedIndex.InputFilter
	{
		@Override
		public boolean acceptInput(Project project, VirtualFile file)
		{
			return file.getFileType() == GuiFormFileType.INSTANCE;
		}
	}

	public static List<PsiFile> findFormsBoundToClass(Project project, String className)
	{
		return findFormsBoundToClass(project, className, (GlobalSearchScope) ProjectScopes.getAllScope(project));
	}

	public static List<PsiFile> findFormsBoundToClass(Project project, String className, GlobalSearchScope scope)
	{
		return ReadAction.compute(() ->
		{
			Collection<VirtualFile> files;
			try
			{
				files = FileBasedIndex.getInstance().getContainingFiles(NAME, className, GlobalSearchScope.projectScope(project).intersectWith(scope));
			}
			catch(IndexNotReadyException e)
			{
				return Collections.emptyList();
			}
			if(files.isEmpty())
			{
				return Collections.emptyList();
			}
			List<PsiFile> result = new ArrayList<>();
			for(VirtualFile file : files)
			{
				if(!file.isValid())
				{
					continue;
				}
				PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
				if(psiFile != null)
				{
					result.add(psiFile);
				}
			}
			return result;
		});
	}

	public static List<PsiFile> findFormsBoundToClass(@Nonnull PsiClass psiClass)
	{
		String qName = FormReferencesSearcher.getQualifiedName(psiClass);
		if(qName == null)
		{
			return Collections.emptyList();
		}
		return findFormsBoundToClass(psiClass.getProject(), qName);
	}

	public static List<PsiFile> findFormsBoundToClass(Project project, PsiClass psiClass, GlobalSearchScope scope)
	{
		String qName = FormReferencesSearcher.getQualifiedName(psiClass);
		if(qName == null)
		{
			return Collections.emptyList();
		}
		return findFormsBoundToClass(project, qName, scope);
	}

	public static List<PsiFile> findFormsBoundToClass(Project project, @Nonnull PsiClass psiClass)
	{
		String qName = FormReferencesSearcher.getQualifiedName(psiClass);
		if(qName == null)
		{
			return Collections.emptyList();
		}
		return findFormsBoundToClass(project, qName);
	}

	public static List<PsiFile> findFormsBoundToClass(PsiClass psiClass, GlobalSearchScope scope)
	{
		String qName = FormReferencesSearcher.getQualifiedName(psiClass);
		if(qName == null)
		{
			return Collections.emptyList();
		}
		return findFormsBoundToClass(psiClass.getProject(), qName, scope);
	}
}
