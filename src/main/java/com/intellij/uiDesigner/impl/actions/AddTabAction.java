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

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.impl.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.impl.designSurface.ComponentItemDragObject;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadTabbedPane;
import com.intellij.uiDesigner.impl.palette.Palette;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class AddTabAction extends AbstractGuiEditorAction {
  public AddTabAction() {
    super(true);
  }

  @Override
  protected void actionPerformed(GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    RadTabbedPane tabbedPane = (RadTabbedPane) selection.get(0);
    Palette palette = Palette.getInstance(editor.getProject());

    RadComponent radComponent = InsertComponentProcessor.createPanelComponent(editor);
    ComponentDropLocation dropLocation = tabbedPane.getDropLocation(null);
    dropLocation.processDrop(editor, new RadComponent[] { radComponent }, null, 
                             new ComponentItemDragObject(palette.getPanelItem()));
  }

  @Override
  @RequiredUIAccess
  protected void update(@Nonnull GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    e.getPresentation().setVisible(selection.size() == 1 && selection.get(0) instanceof RadTabbedPane);
  }
}
