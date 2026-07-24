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
package com.intellij.uiDesigner.impl.wizard;

import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.ide.impl.idea.ide.wizard.CommitStepException;
import consulo.ide.impl.idea.ide.wizard.StepAdapter;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanStep extends StepAdapter
{
	private JPanel myComponent;
	private TextFieldWithBrowseButton myTfWitgBtnChooseClass;
	private JRadioButton myRbBindToNewBean;
	private JRadioButton myRbBindToExistingBean;
	JTextField myTfShortClassName;
	private TextFieldWithBrowseButton myTfWithBtnChoosePackage;
	private JLabel myPackageLabel;
	private JLabel myExistClassLabel;
	private final WizardData myData;

    public BeanStep(@Nonnull final WizardData data)
	{
		myData = data;

		myPackageLabel.setLabelFor(myTfWithBtnChoosePackage.getTextField());
		myExistClassLabel.setLabelFor(myTfWitgBtnChooseClass.getTextField());

		ItemListener itemListener = e -> {
            boolean state = myRbBindToNewBean.isSelected();

            myTfShortClassName.setEnabled(state);
            myTfWithBtnChoosePackage.setEnabled(state);

            myTfWitgBtnChooseClass.setEnabled(!state);
        };
		myRbBindToNewBean.addItemListener(itemListener);
		myRbBindToExistingBean.addItemListener(itemListener);

		{
			final ButtonGroup buttonGroup = new ButtonGroup();
			buttonGroup.add(myRbBindToNewBean);
			buttonGroup.add(myRbBindToExistingBean);
		}

		myTfWitgBtnChooseClass.addActionListener(
            e -> {
                TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myData.myProject).createWithInnerClassesScopeChooser(
                    UIDesignerLocalize.titleChooseBeanClass().get(),
                    GlobalSearchScope.projectScope(myData.myProject),
                    aClass -> aClass.getParent() instanceof PsiJavaFile,
                    null
                );
                chooser.showDialog();
                final PsiClass aClass = chooser.getSelected();
                if(aClass == null)
                {
                    return;
                }
                final String fqName = aClass.getQualifiedName();
                myTfWitgBtnChooseClass.setText(fqName);
            }
        );

		myTfWithBtnChoosePackage.addActionListener(e -> {
            final PackageChooser dialog = myData.myProject.getInstance(PackageChooserFactory.class).create();
            dialog.selectPackage(myTfWithBtnChoosePackage.getText());

            List<PsiJavaPackage> psiJavaPackages = dialog.showAndSelect();
            final PsiJavaPackage aPackage = psiJavaPackages == null || psiJavaPackages.isEmpty() ? null : psiJavaPackages.getFirst();
            if(aPackage != null)
            {
                myTfWithBtnChoosePackage.setText(aPackage.getQualifiedName());
            }
        });
	}

	@Override
    public void _init()
	{
		// Select way of binding
		if(myData.myBindToNewBean)
		{
			myRbBindToNewBean.setSelected(true);
		}
		else
		{
			myRbBindToExistingBean.setSelected(true);
		}

		// New bean
		myTfShortClassName.setText(myData.myShortClassName);
		myTfWithBtnChoosePackage.setText(myData.myPackageName);

		// Existing bean
		myTfWitgBtnChooseClass.setText(
				myData.myBeanClass != null ? myData.myBeanClass.getQualifiedName() : null
		);
	}

	private void resetBindings()
	{
		for(int i = myData.myBindings.length - 1; i >= 0; i--)
		{
			myData.myBindings[i].myBeanProperty = null;
		}
	}

	@Override
    public void _commit(boolean finishChosen) throws CommitStepException
	{
		final boolean newBindToNewBean = myRbBindToNewBean.isSelected();
		if(myData.myBindToNewBean != newBindToNewBean)
		{
			resetBindings();
		}

		myData.myBindToNewBean = newBindToNewBean;

		if(myData.myBindToNewBean)
		{ // new bean
			final String oldShortClassName = myData.myShortClassName;
			final String oldPackageName = myData.myPackageName;

			final String shortClassName = myTfShortClassName.getText().trim();
			if(shortClassName.length() == 0)
			{
				throw new CommitStepException(UIDesignerLocalize.errorPleaseSpecifyClassNameOfTheBeanToBeCreated().get());
			}
			final PsiManager psiManager = PsiManager.getInstance(myData.myProject);
			if(!JavaPsiFacade.getInstance(psiManager.getProject()).getNameHelper().isIdentifier(shortClassName))
			{
				throw new CommitStepException(UIDesignerLocalize.errorXIsNotAValidClassName(shortClassName).get());
			}

			final String packageName = myTfWithBtnChoosePackage.getText().trim();
			if(packageName.length() != 0 && JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(packageName) == null)
			{
				throw new CommitStepException(UIDesignerLocalize.errorPackageWithNameXDoesNotExist(packageName).get());
			}

			myData.myShortClassName = shortClassName;
			myData.myPackageName = packageName;

			// check whether new class already exists
			{
				final String fullClassName = packageName.length() != 0 ? packageName + "." + shortClassName : shortClassName;
				final Module module = ModuleUtilCore.findModuleForFile(myData.myFormFile, myData.myProject);
				if(JavaPsiFacade.getInstance(psiManager.getProject())
						.findClass(fullClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) != null)
				{
					throw new CommitStepException(UIDesignerLocalize.errorCannotCreateClassXBecauseItAlreadyExists(fullClassName).get());
				}
			}

			if(!Comparing.equal(oldShortClassName, shortClassName) || !Comparing.equal(oldPackageName, packageName))
			{
				// After bean class changed we need to reset all previously set bindings
				resetBindings();
			}
		}
		else
		{ // existing bean
			final String oldFqClassName = myData.myBeanClass != null ? myData.myBeanClass.getQualifiedName() : null;
			final String newFqClassName = myTfWitgBtnChooseClass.getText().trim();
			if(newFqClassName.length() == 0)
			{
				throw new CommitStepException(UIDesignerLocalize.errorPleaseSpecifyFullyQualifiedNameOfBeanClass().get());
			}
			final PsiClass aClass =
					JavaPsiFacade.getInstance(myData.myProject).findClass(newFqClassName, GlobalSearchScope.allScope(myData.myProject));
			if(aClass == null)
			{
				throw new CommitStepException(UIDesignerLocalize.errorClassWithNameXDoesNotExist(newFqClassName).get());
			}
			myData.myBeanClass = aClass;

			if(!Comparing.equal(oldFqClassName, newFqClassName))
			{
				// After bean class changed we need to reset all previously set bindings
				resetBindings();
			}
		}
	}

	@Override
    public JComponent getComponent()
	{
		return myComponent;
	}
}
