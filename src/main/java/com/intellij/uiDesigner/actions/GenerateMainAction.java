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

package com.intellij.uiDesigner.actions;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.codeInsight.generation.PsiGenerationInfo;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.binding.FormClassIndex;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class GenerateMainAction extends AnAction
{
	private static final Logger LOG = Logger.getInstance(GenerateMainAction.class);

	public void actionPerformed(AnActionEvent e)
	{
		final Project project = e.getData(CommonDataKeys.PROJECT);
		assert project != null;
		final Editor editor = e.getData(PlatformDataKeys.EDITOR);
		assert editor != null;
		final int offset = editor.getCaretModel().getOffset();
		final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
		PsiClass psiClass = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiClass.class);
		assert psiClass != null;

		if(!PsiUtil.hasDefaultConstructor(psiClass))
		{
			Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.no.default.constructor"), UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
			return;
		}

		final List<PsiFile> boundForms = FormClassIndex.findFormsBoundToClass(project, psiClass.getQualifiedName());
		final LwRootContainer rootContainer;
		try
		{
			rootContainer = Utils.getRootContainer(boundForms.get(0).getText(), null);
		}
		catch(AlienFormFileException ex)
		{
			Messages.showMessageDialog(project, "The form bound to the class is not a valid IntelliJ IDEA form",
					UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
			return;
		}
		catch(Exception ex)
		{
			LOG.error(ex);
			return;
		}

		if(rootContainer.getComponentCount() == 0)
		{
			Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.empty.form"),
					UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
			return;
		}
		String rootBinding = rootContainer.getComponent(0).getBinding();
		if(rootBinding == null || psiClass.findFieldByName(rootBinding, true) == null)
		{
			Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.no.root.binding"),
					UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
			return;
		}

		@NonNls final StringBuilder mainBuilder = new StringBuilder("public static void main(String[] args) { ");
		final JavaCodeStyleManager csm = JavaCodeStyleManager.getInstance(project);
		SuggestedNameInfo nameInfo = csm.suggestVariableName(VariableKind.LOCAL_VARIABLE, "frame", null, null);
		String varName = nameInfo.names[0];
		mainBuilder.append(JFrame.class.getName()).append(" ").append(varName).append("= new ").append(JFrame.class.getName());
		mainBuilder.append("(\"").append(psiClass.getName()).append("\");");
		mainBuilder.append(varName).append(".setContentPane(new ").append(psiClass.getQualifiedName()).append("().").append(rootBinding).append(");");
		mainBuilder.append(varName).append(".setDefaultCloseOperation(").append(JFrame.class.getName()).append(".EXIT_ON_CLOSE);");
		mainBuilder.append(varName).append(".pack();");
		mainBuilder.append(varName).append(".setVisible(true);");

		mainBuilder.append("}\n");

		CommandProcessor.getInstance().executeCommand(project, new Runnable()
		{
			public void run()
			{
				ApplicationManager.getApplication().runWriteAction(new Runnable()
				{
					public void run()
					{
						try
						{
							PsiMethod method =
									JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createMethodFromText(mainBuilder.toString(), file);
							List<PsiGenerationInfo<PsiMethod>> infos = Collections.singletonList(new PsiGenerationInfo<PsiMethod>(method));
							List<PsiGenerationInfo<PsiMethod>> resultMembers = GenerateMembersUtil.insertMembersAtOffset(file, offset, infos);
							resultMembers.get(0).positionCaret(editor, false);
						}
						catch(IncorrectOperationException e1)
						{
							LOG.error(e1);
						}
					}
				});
			}
		}, null, null);
	}

	@Override
	public void update(AnActionEvent e)
	{
		e.getPresentation().setVisible(isActionEnabled(e));
	}

	private static boolean isActionEnabled(final AnActionEvent e)
	{
		Project project = e.getData(CommonDataKeys.PROJECT);
		if(project == null)
		{
			return false;
		}
		Editor editor = e.getData(PlatformDataKeys.EDITOR);
		if(editor == null)
		{
			return false;
		}
		int offset = editor.getCaretModel().getOffset();
		PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
		if(file == null)
		{
			return false;
		}
		PsiElement element = file.findElementAt(offset);
		if(element == null)
		{
			return false;
		}
		PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
		if(psiClass == null)
		{
			return false;
		}
		if(PsiMethodUtil.findMainMethod(psiClass) != null)
		{
			return false;
		}
		if(FormClassIndex.findFormsBoundToClass(psiClass).isEmpty())
		{
			return false;
		}
		return true;
	}
}
