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

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.psi.PsiManager;
import com.intellij.uiDesigner.impl.ErrorInfo;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FormEditorErrorCollector extends FormErrorCollector {
  private final GuiEditor myEditor;
  private final RadComponent myComponent;
  private List<ErrorInfo> myResults = null;
  private final InspectionProfile myProfile;
  private final PsiFile myFormPsiFile;

  @RequiredReadAction
  public FormEditorErrorCollector(GuiEditor editor, RadComponent component) {
    myEditor = editor;
    myComponent = component;

    myFormPsiFile = PsiManager.getInstance(editor.getProject()).findFile(editor.getFile());
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(editor.getProject());
    myProfile = profileManager.getInspectionProfile();
  }

  public ErrorInfo[] result() {
    return myResults == null ? null : myResults.toArray(new ErrorInfo[myResults.size()]);
  }

  @Override
  public void addError(@Nonnull String inspectionId, IComponent component, @Nullable IProperty prop,
                       @Nonnull String errorMessage,
                       EditorQuickFixProvider... editorQuickFixProviders) {
    if (myResults == null) {
      myResults = new ArrayList<>();
    }
    List<QuickFix> quickFixes = new ArrayList<>();
    for (EditorQuickFixProvider provider : editorQuickFixProviders) {
      if (provider != null) {
        quickFixes.add(provider.createQuickFix(myEditor, myComponent));
      }
    }

    ErrorInfo errorInfo = new ErrorInfo(myComponent, prop == null ? null : prop.getName(), errorMessage,
                                              myProfile.getErrorLevel(HighlightDisplayKey.find(inspectionId), myFormPsiFile),
                                              quickFixes.toArray(new QuickFix[quickFixes.size()]));
    errorInfo.setInspectionId(inspectionId);
    myResults.add(errorInfo);
  }
}
