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

import consulo.document.util.TextRange;
import consulo.ide.impl.idea.openapi.module.ResourceFileUtil;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.language.util.IncorrectOperationException;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import consulo.module.Module;
import consulo.project.Project;

import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class ResourceFileReference extends ReferenceInForm {
  public ResourceFileReference(PsiPlainTextFile file, TextRange range) {
    super(file, range);
  }

  @Nullable
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
    VirtualFile formFile = ResourceFileUtil.findResourceFileInDependents(module, getRangeText());
    if (formFile == null) {
      return null;
    }
    return PsiManager.getInstance(project).findFile(formFile);
  }

  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException
  {
    if (!(element instanceof PsiFile)) { //should be icon file or nested form
      throw new consulo.language.util.IncorrectOperationException();
    }

    updateRangeText(FormEditingUtil.buildResourceName((PsiFile)element));
    return myFile;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) {
    return handleFileRename(newElementName, "", true);
  }
}
