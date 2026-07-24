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
package com.intellij.uiDesigner.impl.propertyInspector;

import consulo.logging.Logger;
import com.intellij.uiDesigner.impl.ErrorInfo;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.quickFixes.QuickFixManager;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class QuickFixManagerImpl extends QuickFixManager <PropertyInspectorTable>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.propertyInspector.QuickFixManagerImpl");

  public QuickFixManagerImpl(GuiEditor editor, PropertyInspectorTable propertyInspectorTable, JViewport viewPort) {
    super(editor, propertyInspectorTable, viewPort);
    propertyInspectorTable.getSelectionModel().addListSelectionListener(new MyListSelectionListener());
  }

  @Nullable
  public Rectangle getErrorBounds() {
    int selectedRow = myComponent.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myComponent.getRowCount()){
      return null;
    }

    Rectangle rowRect = myComponent.getCellRect(selectedRow, 0, true);
    LOG.assertTrue(rowRect != null);
    Rectangle visibleRect = myComponent.getVisibleRect();
    if(visibleRect.intersects(rowRect)){
      return visibleRect.intersection(rowRect);
    }
    else{
      return null;
    }
  }

  @Nonnull
  public ErrorInfo[] getErrorInfos() {
    int selectedRow = myComponent.getSelectedRow();
    if(selectedRow < 0 || selectedRow >= myComponent.getRowCount()){
      return ErrorInfo.EMPTY_ARRAY;
    }
    ErrorInfo info = myComponent.getErrorInfoForRow(selectedRow);
    if (info != null) {
      return new ErrorInfo[] { info };
    }
    return ErrorInfo.EMPTY_ARRAY;
  }

  private final class MyListSelectionListener implements ListSelectionListener{
    public void valueChanged(ListSelectionEvent e) {
      hideIntentionHint();
      updateIntentionHintVisibility();
    }
  }
}
