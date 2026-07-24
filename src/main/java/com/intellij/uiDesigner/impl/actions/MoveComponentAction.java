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

import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class MoveComponentAction extends AbstractGuiEditorAction {
  private final int myRowDelta;
  private final int myColumnDelta;
  private final int myRowSpanDelta;
  private final int myColSpanDelta;

  public MoveComponentAction(int rowDelta, int columnDelta, int rowSpanDelta, int colSpanDelta) {
    super(true);
    myRowDelta = rowDelta;
    myColumnDelta = columnDelta;
    myRowSpanDelta = rowSpanDelta;
    myColSpanDelta = colSpanDelta;
  }

  @Override
  protected void actionPerformed(GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    if (myColumnDelta != 0) {
      // sort the selection so that move in indexed layout will handle components in correct order
      Collections.sort(selection, (o1, o2) -> {
        int index1 = o1.getParent().indexOfComponent(o1);
        int index2 = o2.getParent().indexOfComponent(o2);
        return (index2 - index1) * myColumnDelta;
      });
    }
    for(RadComponent c: selection) {
      c.getParent().getLayoutManager().moveComponent(c, myRowDelta, myColumnDelta, myRowSpanDelta, myColSpanDelta);
    }
  }

  @Override
  @RequiredUIAccess
  protected void update(@Nonnull GuiEditor editor, List<RadComponent> selection, AnActionEvent e) {
    e.getPresentation().setEnabled(true);
    for(RadComponent c: selection) {
      if (!c.getParent().getLayoutManager().canMoveComponent(c, myRowDelta, myColumnDelta, myRowSpanDelta, myColSpanDelta)) {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
  }
}
