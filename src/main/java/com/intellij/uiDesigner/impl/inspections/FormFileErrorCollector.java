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
package com.intellij.uiDesigner.impl.inspections;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemDescriptorBase;
import consulo.language.psi.PsiFile;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.impl.make.FormElementNavigatable;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.util.jdom.JDOMUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FormFileErrorCollector extends FormErrorCollector {
  private final InspectionManager myManager;
  private final PsiFile myFile;
  private final boolean myOnTheFly;
  private final List<ProblemDescriptor> myProblems = new ArrayList<>();

  public FormFileErrorCollector(PsiFile file, InspectionManager manager, boolean onTheFly) {
    myManager = manager;
    myFile = file;
    myOnTheFly = onTheFly;
  }

  public void addError(String inspectionId, IComponent component, @Nullable IProperty prop,
                       @Nonnull String errorMessage,
                       EditorQuickFixProvider... editorQuickFixProviders) {
    ProblemDescriptor problemDescriptor = myManager.createProblemDescriptor(myFile, JDOMUtil.escapeText(errorMessage),
                                                                                  (LocalQuickFix)null,
                                                                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly);
    if (problemDescriptor instanceof ProblemDescriptorBase && component != null) {
      FormElementNavigatable navigatable = new FormElementNavigatable(myFile.getProject(), myFile.getVirtualFile(),
                                                                      component.getId());
      ((ProblemDescriptorBase) problemDescriptor).setNavigatable(navigatable);
    }
    myProblems.add(problemDescriptor);
  }

  public ProblemDescriptor[] result() {
    return myProblems.toArray(new ProblemDescriptor[myProblems.size()]);
  }
}
