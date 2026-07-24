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
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.impl.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.impl.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.impl.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.impl.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.editors.AbstractTextFieldEditor;
import com.intellij.uiDesigner.impl.propertyInspector.editors.ComponentEditor;
import com.intellij.uiDesigner.impl.propertyInspector.properties.HGapProperty;
import com.intellij.uiDesigner.impl.propertyInspector.properties.VGapProperty;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.ComponentRenderer;
import com.intellij.uiDesigner.impl.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.lw.LwContainer;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Vector;

/**
 * @author yole
 */
public class RadCardLayoutManager extends RadLayoutManager {
  @Nullable
  @Override
  public String getName() {
    return UIFormXmlConstants.LAYOUT_CARD;
  }

  @Nullable
  @Override
  public LayoutManager createLayout() {
    return new CardLayout();
  }

  @Override
  public void readLayout(LwContainer lwContainer, RadContainer radContainer) throws Exception {
    String defaultCard = (String)lwContainer.getClientProperty(UIFormXmlConstants.LAYOUT_CARD);
    DefaultCardProperty.INSTANCE.setValue(radContainer, defaultCard);
  }

  @Override
  public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    writer.startElement(UIFormXmlConstants.ELEMENT_CARD);
    try {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, (String) child.getCustomLayoutConstraints());
    }
    finally {
      writer.endElement();
    }
  }

  @Override
  public void writeLayout(final XmlWriter writer, final RadContainer radContainer) {
    CardLayout layout = (CardLayout) radContainer.getLayout();
    
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_HGAP, layout.getHgap());
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VGAP, layout.getVgap());

    String defaultCard = DefaultCardProperty.INSTANCE.getValue(radContainer);
    if (!StringUtil.isEmpty(defaultCard)) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_SHOW, defaultCard);
    }
  }

  @Override
  public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
    container.getDelegee().add(component.getDelegee(), component.getCustomLayoutConstraints());
  }

  @Override
  public void removeComponentFromContainer(RadContainer container, RadComponent component) {
    if (component.getId().equals(DefaultCardProperty.INSTANCE.getValue(container))) {
      DefaultCardProperty.INSTANCE.setValueEx(container, null);
    }
    super.removeComponentFromContainer(container, component);
  }

  @Override public void changeContainerLayout(RadContainer container) throws IncorrectOperationException {
    if (container.getComponentCount() != 0) {
      throw new IncorrectOperationException("Only empty containers can be changed to CardLayout");
    }
    super.changeContainerLayout(container);
  }

  @Override @Nonnull
  public ComponentDropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
    return new CardDropLocation(container);
  }

  @Override
  public Property[] getContainerProperties(final Project project) {
    return new Property[]{
      HGapProperty.getInstance(project),
      VGapProperty.getInstance(project),
      DefaultCardProperty.INSTANCE };
  }

  @Override
  public Property[] getComponentProperties(final Project project, final RadComponent component) {
    return new Property[] { CardNameProperty.INSTANCE };
  }

  @Override
  public boolean isSwitchedToChild(RadContainer container, RadComponent child) {
    return child.getDelegee().isVisible();
  }

  @Override
  public boolean switchContainerToChild(RadContainer container, RadComponent child) {
    CardLayout cardLayout = (CardLayout) container.getLayout();
    String card = (String) child.getCustomLayoutConstraints();
    cardLayout.show(container.getDelegee(), card);
    return true;
  }

  @Override
  public boolean areChildrenExclusive() {
    return true;
  }

  @Override public void createSnapshotLayout(final SnapshotContext context,
                                             final JComponent parent,
                                             final RadContainer container,
                                             final LayoutManager layout) {
    CardLayout cardLayout = (CardLayout) layout;
    container.setLayout(new CardLayout(cardLayout.getHgap(), cardLayout.getVgap()));
  }


  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    // unfortunately card can be extracted only through reflection
    String cardName = null;
    try {
      final LayoutManager layout = parent.getLayout();
      Field vectorField = layout.getClass().getDeclaredField("vector");
      vectorField.setAccessible(true);
      Vector vector = (Vector) vectorField.get(parent.getLayout());
      for (Object card : vector) {
        Field nameField = card.getClass().getDeclaredField("name");
        nameField.setAccessible(true);
        Field compField = card.getClass().getDeclaredField("comp");
        compField.setAccessible(true);
        if (compField.get(card) == child) {
          cardName = (String) nameField.get(card);
          break;
        }
      }
    }
    catch (Exception e) {
      // ignore
    }

    if (cardName != null) {
      component.setCustomLayoutConstraints(cardName);
      container.addComponent(component);
    }
  }

  private static class CardDropLocation implements ComponentDropLocation {
    private final RadContainer myContainer;
    private static final String CARD_NAME_PREFIX = "Card";

    public CardDropLocation(final RadContainer container) {
      myContainer = container;
    }

    @Override
    public RadContainer getContainer() {
      return myContainer;
    }

    @Override
    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1;
    }

    @Override
    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      Rectangle rc = myContainer.getBounds();
      feedbackLayer.putFeedback(myContainer.getParent().getDelegee(), rc, null);
    }

    @Override
    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      int cardIndex = 1;
      while(myContainer.findComponentWithConstraints(CARD_NAME_PREFIX + cardIndex) != null) {
        cardIndex++;
      }
      components [0].setCustomLayoutConstraints(CARD_NAME_PREFIX + cardIndex);
      myContainer.addComponent(components [0]);
    }

    @Nullable
    @Override
    public ComponentDropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }

  private static class CardNameProperty extends Property<RadComponent, String> {
    private final LabelPropertyRenderer<String> myRenderer = new LabelPropertyRenderer<>();

    private final AbstractTextFieldEditor<String> myEditor = new AbstractTextFieldEditor<>() {
      @Override
      protected void setValueFromComponent(RadComponent component, String value) {
        myTf.setText((String) component.getCustomLayoutConstraints());
      }

      @Override
      public String getValue() throws Exception {
        return myTf.getText();
      }
    };

    static CardNameProperty INSTANCE = new CardNameProperty();

    private CardNameProperty() {
      super(null, "Card Name");
    }

    @Override
    public String getValue(final RadComponent component) {
      return (String) component.getCustomLayoutConstraints();
    }

    @Override
    protected void setValueImpl(final RadComponent component, final String value) throws Exception {
      if (!value.equals(component.getCustomLayoutConstraints())) {
        if (component.getParent().findComponentWithConstraints(value) != null) {
          throw new Exception(UIDesignerLocalize.errorCardAlreadyExists(value).get());
        }
        component.changeCustomLayoutConstraints(value);
        final JComponent parent = component.getParent().getDelegee();
        CardLayout layout = (CardLayout) parent.getLayout();
        layout.show(parent, value);
      }
    }

    @Nonnull
    @Override
    public PropertyRenderer<String> getRenderer() {
      return myRenderer;
    }

    @Override
    public PropertyEditor<String> getEditor() {
      return myEditor;
    }

    @Override
    public boolean appliesToSelection(final List<RadComponent> selection) {
      return selection.size() == 1;
    }
  }

  private static class DefaultCardProperty extends Property<RadContainer, String> {
    private static final String NAME = "Default Card";

    private final ComponentRenderer myRenderer = new ComponentRenderer();
    private ComponentEditor myEditor;

    static DefaultCardProperty INSTANCE = new DefaultCardProperty();

    public DefaultCardProperty() {
      super(null, NAME);
    }

    @Nonnull
    @Override
    public PropertyRenderer<String> getRenderer() {
      return myRenderer;
    }

    @Override
    public PropertyEditor<String> getEditor() {
      if (myEditor == null) {
        myEditor = new ComponentEditor(null, null) {
          @Override
          protected RadComponent[] collectFilteredComponents(RadComponent component) {
            RadContainer container = (RadContainer)component;
            RadComponent[] result = new RadComponent[container.getComponentCount() + 1];
            for (int i = 1; i < result.length; i++) {
              result[i] = container.getComponent(i - 1);
            }
            return result;
          }
        };
      }
      return myEditor;
    }

    @Override
    public String getValue(RadContainer component) {
      return (String)component.getDelegee().getClientProperty(NAME);
    }

    @Override
    protected void setValueImpl(RadContainer component, String value) throws Exception {
      component.getDelegee().putClientProperty(NAME, StringUtil.isEmpty(value) ? null : value);
    }

    @Override
    public boolean appliesToSelection(List<RadComponent> selection) {
      return selection.size() == 1;
    }
  }
}