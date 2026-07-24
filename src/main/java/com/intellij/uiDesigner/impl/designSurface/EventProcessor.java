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

import jakarta.annotation.Nullable;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class EventProcessor {
  private Cursor myCursor;

  public final Cursor getCursor(){
    return myCursor;
  }

  public final void setCursor(Cursor cursor){
    myCursor = cursor;
  }

  protected abstract void processKeyEvent(KeyEvent e);

  protected abstract void processMouseEvent(MouseEvent e);
  
  /**
   * @return true if processor cancelled its operation; false otherwise
   */ 
  protected abstract boolean cancelOperation();

  public boolean isDragActive() {
    return false;
  }

  public boolean needMousePressed() {
    return false;
  }

  protected static ComponentDropLocation.Direction directionFromKey(int keyCode) {
    switch(keyCode) {
      case KeyEvent.VK_RIGHT: return ComponentDropLocation.Direction.RIGHT;
      case KeyEvent.VK_LEFT: return ComponentDropLocation.Direction.LEFT;
      case KeyEvent.VK_UP: return ComponentDropLocation.Direction.UP;
      case KeyEvent.VK_DOWN: return ComponentDropLocation.Direction.DOWN;
      case KeyEvent.VK_END: return ComponentDropLocation.Direction.RIGHT;
      case KeyEvent.VK_HOME: return ComponentDropLocation.Direction.LEFT;
      case KeyEvent.VK_PAGE_UP: return ComponentDropLocation.Direction.UP;
      case KeyEvent.VK_PAGE_DOWN: return ComponentDropLocation.Direction.DOWN;
      default: return null;
    }
  }

  private static boolean isMoveToLast(int keyCode) {
    return keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END ||
           keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN;
  }

  @Nullable
  protected static ComponentDropLocation moveDropLocation(GuiEditor editor, ComponentDropLocation location,
                                                          ComponentDragObject dragObject, KeyEvent e) {
    ComponentDropLocation.Direction dir = directionFromKey(e.getKeyCode());
    boolean moveToLast = isMoveToLast(e.getKeyCode());
    if (dir != null && location != null) {
      e.consume();
      ComponentDropLocation adjacentLocation;
      if (moveToLast) {
        adjacentLocation = location.getAdjacentLocation(dir);
        ComponentDropLocation lastLocation = location;
        while(adjacentLocation != null) {
          if (adjacentLocation.canDrop(dragObject)) {
            lastLocation = adjacentLocation;
          }
          adjacentLocation = adjacentLocation.getAdjacentLocation(dir);
        }
        adjacentLocation = lastLocation;
      }
      else {
        adjacentLocation = location.getAdjacentLocation(dir);
        while(adjacentLocation != null && !adjacentLocation.canDrop(dragObject)) {
          adjacentLocation = adjacentLocation.getAdjacentLocation(dir);
        }
      }

      if (adjacentLocation != null && adjacentLocation.canDrop(dragObject)) {
        adjacentLocation.placeFeedback(editor.getActiveDecorationLayer(), dragObject);
        return adjacentLocation;
      }
    }
    return location;
  }

}
