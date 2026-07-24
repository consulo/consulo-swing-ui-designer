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

import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.radComponents.RadRootContainer;
import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GroupSelectionProcessor extends EventProcessor {
  private final GuiEditor myEditor;
  private final RadComponent myComponent;
  private Point myStartPoint;
  private final MyRectanglePainter myRectangePainter;

  /**
   * @param component group where drag is started. This group should not be selected
   * after drag is complete.
   */
  public GroupSelectionProcessor(GuiEditor editor, RadComponent component) {
    myEditor = editor;
    myComponent = component;
    myRectangePainter=new MyRectanglePainter();
  }

  protected void processKeyEvent(KeyEvent e){
  }

  protected void processMouseEvent(MouseEvent e){
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      myStartPoint = e.getPoint();
      myEditor.getDragLayer().add(myRectangePainter);
    }
    else if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
      Rectangle rectangle = getRectangle(e);
      myRectangePainter.setBounds(rectangle);
      myEditor.getDragLayer().repaint();
    }
    else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      Rectangle rectangle = getRectangle(e);
      if (e.isShiftDown() && rectangle.width <= 3 && rectangle.height <= 3) {
        RadComponent component = FormEditingUtil.getRadComponentAt(myEditor.getRootContainer(), e.getX(), e.getY());
        if (component != null) {
          RadComponent anchor = myEditor.getSelectionAnchor();
          if (anchor == null || anchor.getParent() != component.getParent() || anchor.getParent() == null || !anchor.getParent().getLayoutManager().isGrid()) {
            component.setSelected(!component.isSelected());
          }
          else {
            selectComponentsInRange(component, anchor);
            myEditor.setSelectionLead(component);
          }
        }
      }
      markRectangle(myEditor.getRootContainer(), rectangle, e.getComponent());
      JComponent dragLayer = myEditor.getDragLayer();
      dragLayer.remove(myRectangePainter);
      dragLayer.repaint();
      myStartPoint = null;
    }
  }

  private static void selectComponentsInRange(RadComponent component, RadComponent anchor) {
    GridConstraints c1 = component.getConstraints();
    GridConstraints c2 = anchor.getConstraints();
    int startRow = Math.min(c1.getRow(), c2.getRow());
    int startCol = Math.min(c1.getColumn(), c2.getColumn());
    int endRow = Math.max(c1.getRow() + c1.getRowSpan(), c2.getRow() + c2.getRowSpan());
    int endCol = Math.max(c1.getColumn() + c1.getColSpan(), c2.getColumn() + c2.getColSpan());
    for(int row=startRow; row<endRow; row++) {
      for(int col=startCol; col<endCol; col++) {
        RadComponent c = anchor.getParent().getComponentAtGrid(row, col);
        if (c != null) {
          c.setSelected(true);
        }
      }
    }
  }

  protected boolean cancelOperation() {
    JComponent dragLayer = myEditor.getDragLayer();
    dragLayer.remove(myRectangePainter);
    dragLayer.repaint();
    return true;
  }

  private Rectangle getRectangle(MouseEvent e){
    int x = Math.min(myStartPoint.x, e.getX());
    int y = Math.min(myStartPoint.y, e.getY());

    int width = Math.abs(myStartPoint.x - e.getX());
    int height = Math.abs(myStartPoint.y - e.getY());

    return new Rectangle(x, y, width, height);
  }

  private void markRectangle(
    RadComponent component,
    Rectangle rectangle,
    Component coordinateOriginComponent
  ){
    if (!(component instanceof RadRootContainer) && !component.equals(myComponent)) {
      Rectangle bounds = component.getBounds();
      Point point = SwingUtilities.convertPoint(component.getDelegee().getParent(), bounds.x, bounds.y, coordinateOriginComponent);
      bounds.setLocation(point);

      if(rectangle.intersects(bounds)){
        component.setSelected(true);
        return;
      }
    }

    if (component instanceof RadContainer){
      RadContainer container = (RadContainer)component;
      // [anton] it is very important to iterate through a STORED array because setSelected can
      // change order of components so iteration via getComponent(i) is incorrect 
      RadComponent[] components = container.getComponents();
      for (RadComponent component1 : components) {
        markRectangle(component1, rectangle, coordinateOriginComponent);
      }
    }
  }

  private static final class MyRectanglePainter extends JComponent{
    private final AlphaComposite myComposite1;
    private final AlphaComposite myComposite2;
    private final Color myColor;


    public MyRectanglePainter() {
      myComposite1 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.3f);
      myComposite2 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.6f);
      myColor = new Color(47, 67, 96);
    }

    protected void paintComponent(Graphics g){
      Graphics2D g2d = (Graphics2D)g;
      super.paintComponent(g);
      Composite oldComposite = g2d.getComposite();
      Color oldColor = g2d.getColor();
      g2d.setColor(myColor);

      g2d.setComposite(myComposite1);
      g2d.fillRect(0, 0, getWidth(), getHeight());

      g2d.setComposite(myComposite2);
      g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

      g2d.setColor(oldColor);
      g2d.setComposite(oldComposite);
    }
  }
}
