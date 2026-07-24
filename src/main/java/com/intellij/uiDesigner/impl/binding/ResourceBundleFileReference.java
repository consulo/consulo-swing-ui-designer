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

import jakarta.annotation.Nonnull;

import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.document.util.TextRange;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 5, 2005
 */
public final class ResourceBundleFileReference extends ReferenceInForm {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.ResourceBundleFileReference");

  public ResourceBundleFileReference(PsiPlainTextFile file, TextRange bundleNameRange) {
    super(file, bundleNameRange);
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
    PropertiesFile propertiesFile = PropertiesUtil.getPropertiesFile(getRangeText(), module, null);
    return propertiesFile == null ? null : propertiesFile.getContainingFile();
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PropertiesFile)) return false;
    String baseName = PropertiesUtil.getFullName((PropertiesFile) element);
    if (baseName == null) return false;
    baseName = baseName.replace('.', '/');
    String rangeText = getRangeText();
    return rangeText.equals(baseName);
  }

  public PsiElement handleElementRename(String newElementName) {
    return handleFileRename(newElementName, PropertiesFileType.DOT_DEFAULT_EXTENSION, false);
  }

  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PropertiesFile)) {
      throw new IncorrectOperationException();
    }

    PropertiesFile propertyFile = (PropertiesFile)element;
    String bundleName = FormReferenceProvider.getBundleName(propertyFile);
    LOG.assertTrue(bundleName != null);
    updateRangeText(bundleName);
    return myFile;
  }
}
