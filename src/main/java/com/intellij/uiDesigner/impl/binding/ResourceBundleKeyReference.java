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

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
public final class ResourceBundleKeyReference extends ReferenceInForm {
  private final String myBundleName;

  public ResourceBundleKeyReference(PsiPlainTextFile file, String bundleName, TextRange keyNameRange) {
    super(file, keyNameRange);
    myBundleName = bundleName;
  }

  public PsiElement resolve() {
    Project project = myFile.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile formVirtualFile = myFile.getVirtualFile();
    if (formVirtualFile == null) {
      return null;
    }
    Module module = fileIndex.getModuleForFile(formVirtualFile);
    if (module == null) {
      return null;
    }
    PropertiesFile propertiesFile = PropertiesUtil.getPropertiesFile(myBundleName, module, null);
    if (propertiesFile == null) {
      return null;
    }
    IProperty property = propertiesFile.findPropertyByKey(getRangeText());
    return property == null ? null : property.getPsiElement();
  }

  public PsiElement bindToElement(@Nonnull PsiElement element) throws consulo.language.util.IncorrectOperationException
  {
    if (!(element instanceof IProperty)) {
      throw new IncorrectOperationException();
    }
    updateRangeText(((IProperty)element).getUnescapedKey());
    return myFile;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof IProperty)) {
      return false;
    }
    IProperty property = (IProperty) element;
    String baseName = PropertiesUtil.getFullName(property.getPropertiesFile());
    return baseName != null && myBundleName.equals(baseName.replace('.', '/')) && getRangeText().equals(property.getUnescapedKey());
  }
}
