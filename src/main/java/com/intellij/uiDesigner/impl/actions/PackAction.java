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
package com.intellij.uiDesigner.impl.actions;

import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import consulo.ui.ex.action.AnActionEvent;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PackAction extends AbstractGuiEditorAction {
  protected void actionPerformed(GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    RadContainer container = getContainerToPack(selection);
    if (container != null) {
      container.getDelegee().setSize(container.getMinimumSize());
      container.getDelegee().revalidate();
    }
  }

  protected void update(@Nonnull GuiEditor editor, ArrayList<RadComponent> selection, AnActionEvent e) {
    e.getPresentation().setEnabled(getContainerToPack(selection) != null);
  }

  private static RadContainer getContainerToPack(List<RadComponent> selection) {
    if (selection.size() != 1 || !(selection.get(0) instanceof RadContainer)) {
      return null;
    }

    RadContainer container = (RadContainer)selection.get(0);
    if (!container.getParent().isXY()) {
      return null;
    }
    return container;
  }
}
