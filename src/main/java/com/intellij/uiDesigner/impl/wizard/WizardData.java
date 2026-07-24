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

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WizardData {
  private static final Logger LOG = Logger.getInstance(WizardData.class);

  @Nonnull
  public final Project myProject;
  /**
   * Form's file.
   */
  @Nonnull
  public final VirtualFile myFormFile;

  /**
   * If <code>true</code> then {@link #myShortClassName} and {@link #myPackageName} should be
   * used, otherwise {@link #myBeanClass} should be used.
   */
  public boolean myBindToNewBean;
  /**
   *
   */
  public String myShortClassName;
  /**
   *
   */
  public String myPackageName;

  /**
   * Bean's class. If <code>null</code> then bean's class is't defined yet.
   */
  public PsiClass myBeanClass;
  @Nonnull
  public final FormProperty2BeanProperty[] myBindings;

  public boolean myGenerateIsModified;

  public WizardData(@Nonnull Project project, @Nonnull VirtualFile formFile) throws Generator.MyException {
    myProject = project;
    myFormFile = formFile;
    myBindToNewBean = true;
    myGenerateIsModified = true;

    LwRootContainer[] rootContainer = new LwRootContainer[1];

    // Create initial bingings between form fields and bean's properties.
    // TODO[vova] ask Anton to not throw exception if form-field doesn't have corresponded field in the Java class
    FormProperty[] formProperties = Generator.exposeForm(myProject, myFormFile, rootContainer);
    myBindings = new FormProperty2BeanProperty[formProperties.length];
    for(int i = formProperties.length - 1; i >= 0; i--){
      myBindings[i] = new FormProperty2BeanProperty(formProperties[i]);
    }

    PsiManager manager = PsiManager.getInstance(myProject);
    VirtualFile directory = formFile.getParent();
    LOG.assertTrue(directory.isDirectory());
    PsiDirectory psiDirectory = manager.findDirectory(directory);
    LOG.assertTrue(psiDirectory != null);
    PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    if(aPackage != null){
      myPackageName = aPackage.getQualifiedName();
    }
  }
}
