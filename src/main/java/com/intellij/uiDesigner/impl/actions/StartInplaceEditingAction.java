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

import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.InplaceContext;
import consulo.ui.ex.action.Presentation;

import javax.annotation.Nullable;

import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StartInplaceEditingAction extends AnAction{

  private GuiEditor myEditor;

  public StartInplaceEditingAction(@Nullable final GuiEditor editor) {
    myEditor = editor;
  }

  public void setEditor(final GuiEditor editor) {
    myEditor = editor;
  }

  public void actionPerformed(final AnActionEvent e) {
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);
    final RadComponent component = selection.get(0);
    final Property defaultInplaceProperty = component.getDefaultInplaceProperty();
    myEditor.getInplaceEditingLayer().startInplaceEditing(component, defaultInplaceProperty,
                                                          component.getDefaultInplaceEditorBounds(), new InplaceContext(true));
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(myEditor);

    // Inplace editing can be started only if single component is selected
    if(selection.size() != 1){
      presentation.setEnabled(false);
      return;
    }

    // Selected component should have "inplace" property
    final RadComponent component = selection.get(0);
    presentation.setEnabled(component.getDefaultInplaceProperty() != null);
  }
}
