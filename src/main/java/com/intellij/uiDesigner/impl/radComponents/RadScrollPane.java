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

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.ModuleProvider;
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.impl.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.impl.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.impl.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.palette.Palette;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadScrollPane extends RadContainer {
  public static final Class COMPONENT_CLASS = JScrollPane.class;
  private static final Logger LOG = Logger.getInstance(RadScrollPane.class);

  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadScrollPane(module, aClass, id);
    }

    public RadComponent newInstance(Class componentClass, String id, Palette palette) {
      return new RadScrollPane(componentClass, id, palette);
    }
  }

  public RadScrollPane(ModuleProvider module, Class componentClass, String id){
    super(module, componentClass, id);
  }

  public RadScrollPane(Class componentClass, String id, Palette palette) {
    super(componentClass, id, palette);
  }

  @Nullable @Override
  protected RadLayoutManager createInitialLayoutManager() {
    return new RadScrollPaneLayoutManager();
  }

  public void write(XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_SCROLLPANE);
    try {
      writeNoLayout(writer, JScrollPane.class.getName());
    } finally {
      writer.endElement(); // scrollpane
    }
  }

  @Override public RadComponent getActionTargetComponent(RadComponent child) {
    return this;
  }

  @Override
  protected void importSnapshotComponent(SnapshotContext context, JComponent component) {
    JScrollPane scrollPane = (JScrollPane) component;
    Component view = scrollPane.getViewport().getView();
    if (view instanceof JComponent) {
      RadComponent childComponent = createSnapshotComponent(context, (JComponent) view);
      if (childComponent != null) {
        addComponent(childComponent);
      }
    }
  }

  private class RadScrollPaneLayoutManager extends RadLayoutManager {
    private MyDropLocation myDropLocation = null;

    @Nullable public String getName() {
      return null;
    }

    public void writeChildConstraints(XmlWriter writer, RadComponent child) {
    }

    @Override @Nonnull
    public ComponentDropLocation getDropLocation(RadContainer container, @Nullable Point location) {
      if (myDropLocation == null) {
        myDropLocation = new MyDropLocation();
      }
      return myDropLocation;
    }

    public void addComponentToContainer(RadContainer container, RadComponent component, int index) {
      try {
        JScrollPane scrollPane = (JScrollPane)container.getDelegee();
        JComponent delegee = component.getDelegee();
        delegee.setLocation(0,0);
        scrollPane.setViewportView(delegee);
      }
      catch (ClassCastException e) {
        LOG.info(e);
        LOG.info("container classloader=" + container.getDelegee().getClass().getClassLoader());
        LOG.info("component classloader=" + component.getDelegee().getClass().getClassLoader());
        throw e;
      }
    }

    @Override public void removeComponentFromContainer(RadContainer container, RadComponent component) {
      JScrollPane scrollPane = (JScrollPane)container.getDelegee();
      scrollPane.setViewportView(null);
    }
  }

  private class MyDropLocation implements ComponentDropLocation {
    public RadContainer getContainer() {
      return RadScrollPane.this;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1 && getComponentCount() == 0;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      feedbackLayer.putFeedback(getDelegee(), new Rectangle(0, 0, getWidth(), getHeight()), getDisplayName());
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      addComponent(components[0]);
    }

    @Nullable
    public ComponentDropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }
}
