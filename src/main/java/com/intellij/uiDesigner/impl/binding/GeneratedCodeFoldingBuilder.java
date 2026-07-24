/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.folding.FoldingBuilderEx;
import consulo.language.editor.folding.FoldingDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class GeneratedCodeFoldingBuilder extends FoldingBuilderEx
{
	@Nonnull
	public FoldingDescriptor[] buildFoldRegions(@Nonnull PsiElement root, @Nonnull Document document, boolean quick)
	{
		MyFoldingVisitor visitor = new MyFoldingVisitor();
		root.accept(visitor);
		return visitor.myFoldingData.toArray(new FoldingDescriptor[visitor.myFoldingData.size()]);
	}

	public String getPlaceholderText(@Nonnull ASTNode node)
	{
		return UIDesignerBundle.message("uidesigner.generated.code.folding.placeholder.text");
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return JavaLanguage.INSTANCE;
	}

	public boolean isCollapsedByDefault(@Nonnull ASTNode node)
	{
		return true;
	}

	private static boolean isGeneratedUIInitializer(PsiClassInitializer initializer)
	{
		PsiCodeBlock body = initializer.getBody();
		if(body.getStatements().length != 1)
		{
			return false;
		}
		PsiStatement statement = body.getStatements()[0];
		if(!(statement instanceof PsiExpressionStatement) ||
				!(((PsiExpressionStatement) statement).getExpression() instanceof PsiMethodCallExpression))
		{
			return false;
		}

		PsiMethodCallExpression call = (PsiMethodCallExpression) ((PsiExpressionStatement) statement).getExpression();
		return AsmCodeGenerator.SETUP_METHOD_NAME.equals(call.getMethodExpression().getReferenceName());
	}

	private static class MyFoldingVisitor extends JavaRecursiveElementWalkingVisitor
	{
		private PsiElement myLastElement;
		private final List<FoldingDescriptor> myFoldingData = new ArrayList<>();

		@Override
		public void visitMethod(PsiMethod method)
		{
			if(AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName()) ||
					AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(method.getName()) ||
					AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD.equals(method.getName()) ||
					AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD.equals(method.getName()))
			{
				addFoldingData(method);
			}
		}

		@Override
		public void visitClassInitializer(PsiClassInitializer initializer)
		{
			if(isGeneratedUIInitializer(initializer))
			{
				addFoldingData(initializer);
			}
		}

		private void addFoldingData(PsiElement element)
		{
			PsiElement prevSibling = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
			synchronized(myFoldingData)
			{
				if(myLastElement == null || prevSibling != myLastElement)
				{
					myFoldingData.add(new FoldingDescriptor(element, element.getTextRange()));
				}
				else
				{
					FoldingDescriptor lastDescriptor = myFoldingData.get(myFoldingData.size() - 1);
					TextRange range = new TextRange(lastDescriptor.getRange().getStartOffset(), element.getTextRange().getEndOffset());
					myFoldingData.set(myFoldingData.size() - 1, new FoldingDescriptor(lastDescriptor.getElement(), range));
				}
			}
			myLastElement = element;
		}
	}
}
