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

package com.intellij.uiDesigner.impl.actions;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.uiDesigner.impl.GuiDesignerConfiguration;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.radComponents.LayoutManagerRegistry;
import consulo.application.AllIcons;
import consulo.application.WriteAction;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.TemplateKindCombo;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.util.function.Consumer;

/**
 * @author yole
 */
public class CreateFormAction extends AbstractCreateFormAction
{
	private static final Logger LOG = Logger.getInstance(CreateFormAction.class);

	private String myLastClassName = null;
	private String myLastLayoutManager = null;

	public CreateFormAction()
	{
		super(UIDesignerBundle.message("action.gui.form.text"), UIDesignerBundle.message("action.gui.form.description"), AllIcons.FileTypes.UiForm);
	}

	@Override
	@RequiredUIAccess
	protected void invokeDialog(Project project, PsiDirectory directory, @Nonnull Consumer<PsiElement[]> consumer)
	{
		final MyInputValidator validator = new MyInputValidator(project, directory);

		final DialogWrapper dialog = new MyDialog(project, validator);

		dialog.showAsync().doWhenDone(() -> consumer.accept(validator.getCreatedElements()));
	}

	@RequiredUIAccess
	@Nonnull
	protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception
	{
		PsiElement createdFile;
		PsiClass newClass = null;
		try
		{
			final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
			assert aPackage != null;
			final String packageName = aPackage.getQualifiedName();
			String fqClassName = null;
			if(myLastClassName != null)
			{
				fqClassName = packageName.length() == 0 ? myLastClassName : packageName + "." + myLastClassName;
			}

			final String formBody = createFormBody(fqClassName, "/com/intellij/uiDesigner/impl/NewForm.xml",
					myLastLayoutManager);
			final String fileName = newName + ".form";
			final PsiFile formFile = PsiFileFactory.getInstance(directory.getProject())
					.createFileFromText(fileName, GuiFormFileType.INSTANCE, formBody);
			createdFile = WriteAction.compute(() -> directory.add(formFile));

			if(myLastClassName != null)
			{
				newClass = JavaDirectoryService.getInstance().createClass(directory, myLastClassName);
			}
		}
		catch(IncorrectOperationException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			LOG.error(e);
			return PsiElement.EMPTY_ARRAY;
		}

		if(newClass != null)
		{
			return new PsiElement[]{
					newClass.getContainingFile(),
					createdFile
			};
		}
		return new PsiElement[]{createdFile};
	}

	protected String getErrorTitle()
	{
		return UIDesignerBundle.message("error.cannot.create.form");
	}

	protected String getCommandName()
	{
		return UIDesignerBundle.message("command.create.form");
	}

	private class MyDialog extends DialogWrapper
	{
		private JPanel myTopPanel;
		private JTextField myFormNameTextField;
		private JCheckBox myCreateBoundClassCheckbox;
		private JTextField myClassNameTextField;
		private TemplateKindCombo myBaseLayoutManagerCombo;
		private JLabel myUpDownHintForm;
		private boolean myAdjusting = false;
		private boolean myNeedAdjust = true;

		private final Project myProject;
		private final MyInputValidator myValidator;

		public MyDialog(final Project project,
						final MyInputValidator validator)
		{
			super(project, true);
			myProject = project;
			myValidator = validator;
			myBaseLayoutManagerCombo.registerUpDownHint(myFormNameTextField);
			myUpDownHintForm.setIcon(TargetAWT.to(PlatformIconGroup.ideUpdown()));
			init();
			setTitle(UIDesignerBundle.message("title.new.gui.form"));
			setOKActionEnabled(false);

			myCreateBoundClassCheckbox.addChangeListener(new ChangeListener()
			{
				public void stateChanged(ChangeEvent e)
				{
					myClassNameTextField.setEnabled(myCreateBoundClassCheckbox.isSelected());
				}
			});

			myFormNameTextField.getDocument().addDocumentListener(new DocumentAdapter()
			{
				protected void textChanged(DocumentEvent e)
				{
					setOKActionEnabled(myFormNameTextField.getText().length() > 0);
					if(myNeedAdjust)
					{
						myAdjusting = true;
						myClassNameTextField.setText(myFormNameTextField.getText());
						myAdjusting = false;
					}
				}
			});

			myClassNameTextField.getDocument().addDocumentListener(new DocumentAdapter()
			{
				protected void textChanged(DocumentEvent e)
				{
					if(!myAdjusting)
					{
						myNeedAdjust = false;
					}
				}
			});

			for(String layoutName : LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames())
			{
				String displayName = LayoutManagerRegistry.getLayoutManagerDisplayName(layoutName);
				myBaseLayoutManagerCombo.addItem(displayName, null, layoutName);
			}
			myBaseLayoutManagerCombo.setSelectedName(GuiDesignerConfiguration.getInstance(project).DEFAULT_LAYOUT_MANAGER);
		}

		protected JComponent createCenterPanel()
		{
			return myTopPanel;
		}

		protected void doOKAction()
		{
			if(myCreateBoundClassCheckbox.isSelected())
			{
				myLastClassName = myClassNameTextField.getText();
			}
			else
			{
				myLastClassName = null;
			}
			myLastLayoutManager = myBaseLayoutManagerCombo.getSelectedName();
			GuiDesignerConfiguration.getInstance(myProject).DEFAULT_LAYOUT_MANAGER = myLastLayoutManager;
			final String inputString = myFormNameTextField.getText().trim();
			if(myValidator.checkInput(inputString) && myValidator.canClose(inputString))
			{
				close(OK_EXIT_CODE);
			}
			close(OK_EXIT_CODE);
		}

		public JComponent getPreferredFocusedComponent()
		{
			return myFormNameTextField;
		}
	}
}
