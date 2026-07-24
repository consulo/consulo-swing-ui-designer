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

import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.UIDesignerBundle;
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.impl.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.IntEnumEditor;
import com.intellij.uiDesigner.impl.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.impl.propertyInspector.properties.VGapProperty;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.IntEnumRenderer;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class RadFlowLayoutManager extends RadAbstractIndexedLayoutManager {
  private static final MyAlignProperty ALIGN_PROPERTY = new MyAlignProperty();

  public String getName() {
    return UIFormXmlConstants.LAYOUT_FLOW;
  }

  public LayoutManager createLayout() {
    return new FlowLayout();
  }

  public void writeLayout(XmlWriter writer, RadContainer radContainer) {
    FlowLayout layout = (FlowLayout) radContainer.getLayout();
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_HGAP, layout.getHgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VGAP, layout.getVgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_FLOW_ALIGN, layout.getAlignment());
  }

  @Nonnull
  @Override
  public ComponentDropLocation getDropLocation(RadContainer container, Point location) {
    FlowLayout flowLayout = (FlowLayout) container.getLayout();
    return new FlowDropLocation(container, location, flowLayout.getAlignment(),
                                (flowLayout.getHgap()+1)/2, (flowLayout.getVgap()+1)/2);
  }

  @Override public Property[] getContainerProperties(Project project) {
    return new Property[] {
      ALIGN_PROPERTY,
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project) };
  }

  @Override
  public void createSnapshotLayout(SnapshotContext context,
                                   JComponent parent,
                                   RadContainer container,
                                   LayoutManager layout) {
    FlowLayout flowLayout = (FlowLayout) layout;
    container.setLayout(new FlowLayout(flowLayout.getAlignment(), flowLayout.getHgap(), flowLayout.getVgap()));
  }

  private static class MyAlignProperty extends Property<RadContainer, Integer> {
    private IntEnumRenderer myRenderer;
    private IntEnumEditor myEditor;
    private IntEnumEditor.Pair[] myPairs;

    public MyAlignProperty() {
      super(null, "Alignment");
    }

    private void initPairs() {
      if (myPairs == null) {
        myPairs = new IntEnumEditor.Pair[] {
          new IntEnumEditor.Pair(FlowLayout.CENTER, UIDesignerBundle.message("property.center")),
          new IntEnumEditor.Pair(FlowLayout.LEFT, UIDesignerBundle.message("property.left")),
          new IntEnumEditor.Pair(FlowLayout.RIGHT, UIDesignerBundle.message("property.right")),
          new IntEnumEditor.Pair(FlowLayout.LEADING, UIDesignerBundle.message("property.leading")),
          new IntEnumEditor.Pair(FlowLayout.TRAILING, UIDesignerBundle.message("property.trailing"))
        };
      }
    }

    public Integer getValue(RadContainer component) {
      LayoutManager layout = component.getLayout();
      if (!(layout instanceof FlowLayout)) return null;
      FlowLayout flowLayout = (FlowLayout)layout;
      return flowLayout.getAlignment();
    }

    protected void setValueImpl(RadContainer component, Integer value) throws Exception {
      FlowLayout flowLayout = (FlowLayout) component.getLayout();
      flowLayout.setAlignment(value.intValue());
    }

    @Nonnull
	public PropertyRenderer<Integer> getRenderer() {
      if (myRenderer == null) {
        initPairs();
        myRenderer = new IntEnumRenderer(myPairs);
      }
      return myRenderer;
    }

    @Nonnull
	public PropertyEditor<Integer> getEditor() {
      if (myEditor == null) {
        initPairs();
        myEditor = new IntEnumEditor(myPairs);
      }
      return myEditor;
    }

    @Override public boolean isModified(RadContainer component) {
      LayoutManager layout = component.getLayout();
      if (!(layout instanceof FlowLayout)) return false;
      FlowLayout flowLayout = (FlowLayout)layout;
      return flowLayout.getAlignment() != FlowLayout.CENTER;
    }
  }
}
