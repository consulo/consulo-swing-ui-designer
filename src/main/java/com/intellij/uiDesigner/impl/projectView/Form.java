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
package com.intellij.uiDesigner.impl.projectView;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.uiDesigner.impl.binding.FormClassIndex;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.navigation.Navigatable;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Collection;
import java.util.HashSet;

public class Form implements Navigatable {
  public static final Key<Form[]> DATA_KEY = Key.create("form.array");
  
  private final Collection<PsiFile> myFormFiles;
  private final PsiClass myClassToBind;

  public Form(PsiClass classToBind) {
    myClassToBind = classToBind;
    myFormFiles = FormClassIndex.findFormsBoundToClass(classToBind);
  }

  public Form(PsiClass classToBind, Collection<PsiFile> formFiles) {
    myClassToBind = classToBind;
    myFormFiles = new HashSet<>(formFiles);
  }

  public boolean equals(Object object) {
    if (object instanceof Form){
      Form form = (Form)object;
      return myFormFiles.equals(form.myFormFiles) && myClassToBind.equals(form.myClassToBind);
    } else {
      return false;
    }
  }

  public int hashCode() {
    return myFormFiles.hashCode() ^ myClassToBind.hashCode();
  }

  public String getName() {
    return myClassToBind.getName();
  }

  public PsiClass getClassToBind() {
    return myClassToBind;
  }

  public PsiFile[] getFormFiles() {
    return PsiUtilCore.toPsiFileArray(myFormFiles);
  }

  public void navigate(boolean requestFocus) {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigate()) {
        psiFile.navigate(requestFocus);
      }
    }
  }

  public boolean canNavigateToSource() {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigateToSource()) return true;
    }
    return false;
  }

  public boolean canNavigate() {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigate()) return true;
    }
    return false;
  }

  public boolean isValid() {
    if (myFormFiles.size() == 0) return false;
    for (PsiFile psiFile : myFormFiles) {
      if (!psiFile.isValid()) {
        return false;
      }
    }
    if (!myClassToBind.isValid()) {
      return false;
    }
    return true;
  }

  public boolean containsFile(VirtualFile vFile) {
    PsiFile classFile = myClassToBind.getContainingFile();
    VirtualFile classVFile = classFile == null ? null : classFile.getVirtualFile();
    if (classVFile != null && classVFile.equals(vFile)) {
      return true;
    }
    for (PsiFile psiFile : myFormFiles) {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && virtualFile.equals(vFile)) {
        return true;
      }
    }
    return false;
  }
}
