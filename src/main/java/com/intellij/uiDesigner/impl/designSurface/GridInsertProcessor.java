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
package com.intellij.uiDesigner.impl.designSurface;

import consulo.logging.Logger;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class GridInsertProcessor {
  private static final Logger LOG = Logger.getInstance(GridInsertProcessor.class);

  private final GuiEditor myEditor;

  public GridInsertProcessor(GuiEditor editor) {
    myEditor = editor;
  }

  @Nonnull
  public static ComponentDropLocation getDropLocation(RadRootContainer rootContainer, Point aPoint) {
    RadContainer container = getDropTargetContainer(rootContainer, aPoint);

    if (container == null) {
      return NoDropLocation.INSTANCE;
    }

    Point targetPoint = SwingUtilities.convertPoint(rootContainer.getDelegee(), aPoint, container.getDelegee());
    return container.getDropLocation(targetPoint);
  }

  public static RadContainer getDropTargetContainer(RadRootContainer rootContainer, Point aPoint) {
    int EPSILON = 4;
    RadContainer container = FormEditingUtil.getRadContainerAt(rootContainer, aPoint.x, aPoint.y, EPSILON);
    // to facilitate initial component adding, increase stickiness if there is one container at top level
    if (container instanceof RadRootContainer && rootContainer.getComponentCount() == 1) {
      RadComponent singleComponent = rootContainer.getComponents()[0];
      if (singleComponent instanceof RadContainer) {
        Rectangle rc = singleComponent.getDelegee().getBounds();
        rc.grow(EPSILON*2, EPSILON*2);
        if (rc.contains(aPoint)) {
          container = (RadContainer) singleComponent;
          EPSILON *= 2;
        }
      }
    }
    return container;
  }

  public ComponentDropLocation processDragEvent(Point pnt, ComponentDragObject dragObject) {
    ComponentDropLocation dropLocation = getDropLocation(myEditor.getRootContainer(), pnt);
    LOG.debug("GridInsertProcessor.processDragEvent(): dropLocation " + dropLocation.toString());
    if (dropLocation.canDrop(dragObject)) {
      dropLocation.placeFeedback(myEditor.getActiveDecorationLayer(), dragObject);
    }
    else {
      myEditor.getActiveDecorationLayer().removeFeedback();
    }

    return dropLocation;
  }

  public Cursor processMouseMoveEvent(Point pnt, boolean copyOnDrop, ComponentDragObject dragObject) {
    ComponentDropLocation location = processDragEvent(pnt, dragObject);
    if (!location.canDrop(dragObject)) {
      return FormEditingUtil.getMoveNoDropCursor();
    }
    return copyOnDrop ? FormEditingUtil.getCopyDropCursor() : FormEditingUtil.getMoveDropCursor();
  }
}
