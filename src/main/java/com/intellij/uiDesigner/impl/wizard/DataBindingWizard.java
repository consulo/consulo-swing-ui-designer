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

import consulo.ide.impl.idea.ide.wizard.AbstractWizard;
import consulo.logging.Logger;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DataBindingWizard extends AbstractWizard{
  private static final Logger LOG = Logger.getInstance(DataBindingWizard.class);
  private final WizardData myData;
  private final Project myProject;
  private final BeanStep myBeanStep;

  public DataBindingWizard(@Nonnull Project project, @Nonnull VirtualFile formFile, @Nonnull WizardData data) {
    super(UIDesignerLocalize.titleDataBindingWizard().get(), project);
    myProject = project;
    myData = data;

    myBeanStep = new BeanStep(myData);
    addStep(myBeanStep);
    addStep(new BindCompositeStep(myData));

    init();

    if (!data.myBindToNewBean) {
      doNextAction();
    }
  }

  @Override
  @RequiredUIAccess
  public JComponent getPreferredFocusedComponent() {
    return myBeanStep.myTfShortClassName; 
  }

  @Override
  @RequiredUIAccess
  protected void doOKAction() {
    CommandProcessor.getInstance().newCommand()
      .project(myProject)
      .inWriteAction()
      .run(() -> {
        try {
          Generator.generateDataBindingMethods(myData);
          DataBindingWizard.super.doOKAction();
        }
        catch (Generator.MyException exc) {
          Messages.showErrorDialog(
            getContentPane(),
            exc.getMessage(),
            CommonLocalize.titleError().get()
          );
        }
      });
  }

  @Override
  protected String getHelpID() {
    return "guiDesigner.formCode.dataBind";
  }
}
