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

import consulo.ui.ex.action.AnActionEvent;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IButtonGroup;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import com.intellij.uiDesigner.impl.radComponents.RadButtonGroup;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class UngroupButtonsAction extends AbstractGuiEditorAction {
  public UngroupButtonsAction() {
    super(true);
  }

  protected void actionPerformed(GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    if (selection.size() == 1) {
      RadComponent component = selection.get(0);
      IButtonGroup group = FormEditingUtil.findGroupForComponent(editor.getRootContainer(), component);
      editor.getRootContainer().deleteGroup((RadButtonGroup) group);
    }
    else {
      for(RadComponent component: selection) {
        editor.getRootContainer().setGroupForComponent(component, null);
      }
    }
  }

  protected void update(@Nonnull GuiEditor editor, ArrayList<RadComponent> selection, AnActionEvent e) {
    boolean visible = GroupButtonsAction.allButtons(selection);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && canUngroup(editor, selection));
  }

  private static boolean canUngroup(GuiEditor editor, ArrayList<RadComponent> selectedComponents) {
    if (selectedComponents.size() < 2) {
      return selectedComponents.size() == 1;
    }
    return isSameGroup(editor, selectedComponents);
  }

  public static boolean isSameGroup(GuiEditor editor, ArrayList<RadComponent> selectedComponents) {
    RadRootContainer rootContainer = editor.getRootContainer();
    IButtonGroup group = FormEditingUtil.findGroupForComponent(rootContainer, selectedComponents.get(0));
    if (group == null) {
      return false;
    }
    for(int i=1; i<selectedComponents.size(); i++) {
      if (FormEditingUtil.findGroupForComponent(rootContainer, selectedComponents.get(i)) != group) {
        return false;
      }
    }
    return true;
  }
}
