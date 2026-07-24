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

import consulo.application.ui.wm.IdeFocusManager;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IndentProperty;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.ui.ex.action.AnActionEvent;

import jakarta.annotation.Nonnull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IncreaseIndentAction extends AbstractGuiEditorAction {
  public IncreaseIndentAction() {
    super(true);
  }

  protected void actionPerformed(GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    IndentProperty indentProperty = IndentProperty.getInstance(editor.getProject());
    for(RadComponent c: selection) {
      int indent = indentProperty.getValue(c).intValue();
      indentProperty.setValueEx(c, adjustIndent(indent));
    }
  }

  protected void update(@Nonnull GuiEditor editor, ArrayList<RadComponent> selection, AnActionEvent e) {
    boolean applicable = canAdjustIndent(selection);
    e.getPresentation().setVisible(applicable);
    Component focusOwner = IdeFocusManager.findInstanceByComponent(editor).getFocusOwner();
    e.getPresentation().setEnabled(applicable && (focusOwner == editor || editor.isAncestorOf(focusOwner)));
  }

  private boolean canAdjustIndent(ArrayList<RadComponent> selection) {
    for(RadComponent c: selection) {
      if (canAdjustIndent(c)) {
        return true;
      }
    }
    return false;
  }

  protected int adjustIndent(int indent) {
    return indent + 1;
  }

  protected boolean canAdjustIndent(RadComponent component) {
    return component.getParent().getLayout() instanceof GridLayoutManager;
  }
}
