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

package com.intellij.uiDesigner.impl.radComponents;

import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;

import javax.swing.*;
import java.awt.LayoutManager;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * @author yole
 */
public class RadSwingGridLayoutManager extends RadGridLayoutManager {
  private int myLastRow = 0;
  private int myLastColumn = 0;

  @Override
  public void createSnapshotLayout(SnapshotContext context,
                                   JComponent parent,
                                   RadContainer container,
                                   LayoutManager layout) {
    GridLayout gridLayout = (GridLayout) layout;

    int ncomponents = parent.getComponentCount();
    int nrows = gridLayout.getRows();
    int ncols = gridLayout.getColumns();

    if (nrows > 0) {
        ncols = (ncomponents + nrows - 1) / nrows;
    } else {
        nrows = (ncomponents + ncols - 1) / ncols;
    }

    container.setLayout(new GridLayoutManager(nrows, ncols,
                                              new Insets(0, 0, 0, 0),
                                              gridLayout.getHgap(), gridLayout.getVgap(),
                                              true, true));
  }


  @Override
  public void addSnapshotComponent(JComponent parent,
                                   JComponent child,
                                   RadContainer container,
                                   RadComponent component) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    component.getConstraints().setRow(myLastRow);
    component.getConstraints().setColumn(myLastColumn);
    component.getConstraints().setFill(GridConstraints.FILL_BOTH);
    if (myLastColumn == grid.getColumnCount()-1) {
      myLastRow++;
      myLastColumn = 0;
    }
    else {
      myLastColumn++;
    }
    container.addComponent(component);
  }
}
