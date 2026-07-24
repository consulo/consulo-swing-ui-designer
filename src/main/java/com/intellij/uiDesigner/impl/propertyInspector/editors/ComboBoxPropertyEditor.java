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
package com.intellij.uiDesigner.impl.propertyInspector.editors;

import consulo.ui.ex.awt.ComboBox;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class ComboBoxPropertyEditor<V> extends PropertyEditor<V> {
  protected final ComboBox<V> myCbx;

  public ComboBoxPropertyEditor() {
    myCbx = new ComboBox<>(-1);
    myCbx.setBorder(null);
    myCbx.addPopupMenuListener(new MyPopupMenuListener());
  }

  @Override
  public final void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCbx);
    ListCellRenderer renderer = myCbx.getRenderer();
    if (renderer instanceof JComponent) {
      SwingUtilities.updateComponentTreeUI((JComponent)renderer);
    }
  }

  @Override
  public V getValue() throws Exception {
    if (myCbx.isEditable()) {
      Component editorComponent = myCbx.getEditor().getEditorComponent();
      //noinspection unchecked
      return (V)((JTextField)editorComponent).getText();
    }
    else {
      //noinspection unchecked
      return (V)myCbx.getSelectedItem();
    }
  }

  private final class MyPopupMenuListener implements PopupMenuListener{
    private boolean myCancelled;

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e){
      myCancelled=false;
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e){
      if(!myCancelled){
        fireValueCommitted(true, true);
      }
    }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e){
      myCancelled=true;
    }
  }
}