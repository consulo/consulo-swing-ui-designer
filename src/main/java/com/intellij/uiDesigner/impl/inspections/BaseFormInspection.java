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

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.impl.ErrorInfo;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.GuiFormFileType;
import com.intellij.uiDesigner.impl.PsiPropertiesProvider;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.lw.LwRootContainer;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class BaseFormInspection extends BaseJavaLocalInspectionTool implements FormInspectionTool {
  private final String myInspectionKey;

  public BaseFormInspection(@NonNls @Nonnull String inspectionKey) {
    myInspectionKey = inspectionKey;
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return LocalizeValue.of();
  }

  @Nonnull
  public LocalizeValue getGroupDisplayName() {
    return UIDesignerLocalize.formInspectionsGroup();
  }

  @Nonnull
  @NonNls public String getShortName() {
    return myInspectionKey;
  }

  @Override public boolean isEnabledByDefault() {
    return true;
  }

  public boolean isActive(PsiElement psiRoot) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiRoot.getProject()).getInspectionProfile();
    HighlightDisplayKey key = HighlightDisplayKey.find(myInspectionKey);
    return key != null && profile.isToolEnabled(key, psiRoot);
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    if (file.getFileType().equals(GuiFormFileType.INSTANCE)) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      final Module module = ModuleUtilCore.findModuleForFile(virtualFile, file.getProject());
      if (module == null) {
        return null;
      }

      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(file.getText(), new PsiPropertiesProvider(module));
      }
      catch (Exception e) {
        return null;
      }

      if (rootContainer.isInspectionSuppressed(getShortName(), null)) {
        return null;
      }
      final FormFileErrorCollector collector = new FormFileErrorCollector(file, manager, isOnTheFly);
      startCheckForm(rootContainer);
      FormEditingUtil.iterate(rootContainer, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          if (!rootContainer.isInspectionSuppressed(getShortName(), component.getId())) {
            checkComponentProperties(module, component, collector);
          }
          return true;
        }
      });
      doneCheckForm(rootContainer);
      return collector.result();
    }
    return null;
  }

  public void startCheckForm(IRootContainer rootContainer) {
  }

  public void doneCheckForm(IRootContainer rootContainer) {
  }

  @Nullable
  public ErrorInfo[] checkComponent(@Nonnull GuiEditor editor, @Nonnull RadComponent component) {
    FormEditorErrorCollector collector = new FormEditorErrorCollector(editor, component);
    checkComponentProperties(component.getModule(), component, collector);
    return collector.result();
  }

  protected abstract void checkComponentProperties(consulo.module.Module module, IComponent component, FormErrorCollector collector);
}
