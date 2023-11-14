package com.intellij.uiDesigner.impl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.impl.make.FormSourceCodeGenerator;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.Arrays;

/**
 * @author VISTALL
 * @since 2023-11-14
 */
public class RemovingSetupMethodProcessor extends BaseRefactoringProcessor
{
	public RemovingSetupMethodProcessor(@Nonnull Project project)
	{
		super(project);
	}

	@Nonnull
	@Override
	protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usageInfos)
	{
		return new UsageViewDescriptorAdapter()
		{
			@Nonnull
			@Override
			public PsiElement[] getElements()
			{
				return new PsiElement[0];
			}

			@Override
			public String getProcessedElementsHeader()
			{
				return null;
			}
		};
	}

	@Nonnull
	@Override
	protected UsageInfo[] findUsages()
	{
		final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myProject);
		final PsiMethod[] methods = cache.getMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME,
				GlobalSearchScope.projectScope(myProject));

		return Arrays.stream(methods).map(UsageInfo::new).toArray(UsageInfo[]::new);
	}

	@Override
	protected boolean isPreviewUsages(@Nonnull UsageInfo[] usages)
	{
		return false;
	}

	@Override
	protected void performRefactoring(@Nonnull UsageInfo[] usageInfos)
	{
		for(UsageInfo usageInfo : usageInfos)
		{
			final PsiMethod method = (PsiMethod) usageInfo.getElement();
			final PsiClass aClass = method.getContainingClass();
			if(aClass != null)
			{
				final PsiFile psiFile = aClass.getContainingFile();
				final VirtualFile vFile = psiFile.getVirtualFile();
				if(vFile.isWritable())
				{
					FormSourceCodeGenerator.cleanup(aClass);
				}
			}
		}
	}

	@Nonnull
	@Override
	protected String getCommandName()
	{
		return "Converting Methods";
	}
}
