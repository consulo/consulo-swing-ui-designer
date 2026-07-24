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
package com.intellij.uiDesigner.impl;

import consulo.application.ApplicationManager;
import com.intellij.uiDesigner.impl.componentTree.ComponentPtr;
import com.intellij.uiDesigner.impl.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Stack;

/**
 * This class implements Ctrl+W / Ctrl+Shift+W functionality in the GuiEditor
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SelectionState{
  private final Stack<ComponentPtr[]> mySelectionHistory;
  /** We do not need to handle our own events */
  private boolean myInsideChange;

  public SelectionState(@Nonnull GuiEditor editor) {
    mySelectionHistory = new Stack<>();
    editor.addComponentSelectionListener(new MyComponentSelectionListener());
  }

  public void setInsideChange(boolean insideChange){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myInsideChange = insideChange;
  }

  public Stack<ComponentPtr[]> getSelectionHistory() {
    return mySelectionHistory;
  }

  public static ComponentPtr[] getSelection(GuiEditor editor){
    ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(editor);
    ComponentPtr[] ptrs = new ComponentPtr[selection.size()];
    for(int i = selection.size() - 1; i >= 0; i--){
      ptrs[i] = new ComponentPtr(editor, selection.get(i));
    }
    return ptrs;
  }

  public static void restoreSelection(GuiEditor editor, ComponentPtr[] ptrs) {
    FormEditingUtil.clearSelection(editor.getRootContainer());
    for(int i = ptrs.length - 1; i >= 0; i--){
      ComponentPtr ptr = ptrs[i];
      ptr.validate();
      if(ptr.isValid()){
        ptr.getComponent().setSelected(true);
      }
    }
  }

  private final class MyComponentSelectionListener implements ComponentSelectionListener{

    public void selectedComponentChanged(GuiEditor source) {
      if(myInsideChange){ // do not react on own events
        return;
      }
      mySelectionHistory.clear();
      mySelectionHistory.push(getSelection(source));
    }
  }

}
