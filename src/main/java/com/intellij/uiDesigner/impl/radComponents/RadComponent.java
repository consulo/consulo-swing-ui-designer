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

import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.impl.ModuleProvider;
import com.intellij.uiDesigner.impl.StringDescriptorManager;
import com.intellij.uiDesigner.impl.SwingProperties;
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.impl.designSurface.EventProcessor;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.designSurface.InsertComponentProcessor;
import com.intellij.uiDesigner.impl.palette.ComponentItem;
import com.intellij.uiDesigner.impl.palette.Palette;
import com.intellij.uiDesigner.impl.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.impl.propertyInspector.Property;
import com.intellij.uiDesigner.impl.propertyInspector.properties.ClientPropertiesProperty;
import com.intellij.uiDesigner.impl.propertyInspector.properties.ClientPropertyProperty;
import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import com.intellij.uiDesigner.lw.*;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class RadComponent implements IComponent {
  private static final Logger LOG = Logger.getInstance(RadComponent.class);

  /**
   * Shared instance of empty array of RadComponenets
   */
  public static final RadComponent[] EMPTY_ARRAY = new RadComponent[]{};
  /**
   * Using this constant as client property of the Swing component
   * you can find corresponding <code>RadComponent</code>
   */
  @NonNls
  public static final String CLIENT_PROP_RAD_COMPONENT = "radComponent";

  @NonNls
  public static final String CLIENT_PROP_LOAD_TIME_LOCALE = "LoadTimeLocaleKey";

  /**
   * Whether the component selected or not. Value is java.lang.Boolean
   */
  @NonNls public static final String PROP_SELECTED = "selected";

  /**
   * Change notification for this property is fired when the constraints of a component
   * change.
   */
  @NonNls public static final String PROP_CONSTRAINTS = "constraints";

  /**
   * Component id is unique per RadRootContainer.
   */
  @Nonnull
  private final String myId;
  /**
   * @see #getBinding()
   */
  private String myBinding;
  private boolean myCustomCreate = false;
  private boolean myLoadingProperties = false;

  private final ModuleProvider myModule;

  @Nonnull
  private final Class myClass;
  /**
   * Delegee is the JComponent which really represents the
   * component in UI.
   */
  @Nonnull
  private final JComponent myDelegee;
  /**
   * Parent RadContainer. This field is always not <code>null</code>
   * is the component is in hierarchy. But the root of hierarchy
   * has <code>null</code> parent indeed.
   */
  private RadContainer myParent;
  /**
   * Defines whether the component selected or not.
   */
  private boolean mySelected;

  @Nonnull
  private final GridConstraints myConstraints;

  private Object myCustomLayoutConstraints;

  private final PropertyChangeSupport myChangeSupport;

  private final HashSet<String> myModifiedPropertyNames;

  private Palette myPalette;

  private boolean myHasDragger;
  private boolean myResizing;
  private boolean myDragging;
  private boolean myDragBorder;
  private boolean myDefaultBinding;

  /**
   * Creates new <code>RadComponent</code> with the specified
   * class of delegee and specified ID.
   *
   * @param aClass class of the compoent's delegee
   * @param id     id of the compoent inside the form. <code>id</code>
   *               should be a unique atring inside the form.
   */
  public RadComponent(ModuleProvider module, @Nonnull Class aClass, @Nonnull String id) {
    myModule = module;
    myClass = aClass;
    myId = id;

    myChangeSupport = new PropertyChangeSupport(this);
    myConstraints = new GridConstraints();
    myModifiedPropertyNames = new HashSet<>();

    Constructor constructor;
    try {
      constructor = myClass.getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
    }
    catch (NoSuchMethodException e) {
      try {
        constructor = Utils.suggestReplacementClass(myClass).getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
      }
      catch (NoSuchMethodException e1) {
        throw new RuntimeException(e1);
      }
      setCustomCreate(true);
    }

    constructor.setAccessible(true);
    try {
      myDelegee = (JComponent)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    myDelegee.putClientProperty(CLIENT_PROP_RAD_COMPONENT, this);
  }

  public RadComponent(ModuleProvider module, @Nonnull Class aClass, @Nonnull String id, Palette palette) {
    this(module, aClass, id);
    myPalette = palette;
  }

  /**
   * @return module for the component.
   */
  public final Module getModule() {
    return myModule == null ? null : myModule.getModule();
  }

  public final Project getProject() {
    return myModule == null ? null : myModule.getProject();
  }

  public boolean isLoadingProperties() {
    return myLoadingProperties;
  }

  public Palette getPalette() {
    if (myPalette == null) {
      return Palette.getInstance(getProject());
    }
    return myPalette;
  }

  public void setPalette(Palette palette) {
    myPalette = palette;
  }

  /**
   * Initializes introspected properties into default values and
   * sets default component's constraints.
   */
  public void init(GuiEditor editor, @Nonnull ComponentItem item) {
    initDefaultProperties(item);
  }

  public void initDefaultProperties(@Nonnull ComponentItem item) {
    IntrospectedProperty[] properties = getPalette().getIntrospectedProperties(this);
    for (IntrospectedProperty property : properties) {
      Object initialValue = item.getInitialValue(property);
      if (initialValue != null) {
        try {
          //noinspection unchecked
          property.setValue(this, initialValue);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    myConstraints.restore(item.getDefaultConstraints());
  }

  /**
   * @return the component's id. It is unique within the form.
   */
  @Nonnull
  public final String getId() {
    return myId;
  }

  public final String getBinding() {
    return myBinding;
  }

  public final void setBinding(String binding) {
    //TODO[anton,vova]: check that binding is a valid java identifier!!!
    myBinding = binding;
  }

  public boolean isCustomCreate() {
    return myCustomCreate;
  }

  public void setCustomCreate(boolean customCreate) {
    myCustomCreate = customCreate;
  }

  public boolean isCustomCreateRequired() {
    return !getDelegee().getClass().equals(getComponentClass());
  }

  /**
   * @return Swing delegee component. The <code>RadComponent</code> has the same
   *         delegee during all its life.
   */
  @Nonnull
  public final JComponent getDelegee() {
    return myDelegee;
  }

  /**
   * Sometime bounds of the inplace editor depends on the point where
   * user invoked inplace editor.
   *
   * @param x x in delegee coordinate system
   * @param y y in delegee coordinate system
   * @return inplace property for the <code>RadComponent</code> if any.
   *         The method returns <code>null</code> if the component doesn't have
   *         any inplace property. Please not the method can return different
   *         instances of the property for each invokation.
   */
  @Nullable
  public Property getInplaceProperty(int x, int y) {
    return getDefaultInplaceProperty();
  }

  @Nullable
  public Property getDefaultInplaceProperty() {
    return getPalette().getInplaceProperty(this);
  }

  @Nullable
  public Rectangle getDefaultInplaceEditorBounds() {
    return null;
  }

  /**
   * Sometime bounds of the inplace editor depends on the point where
   * user invoked inplace editor.
   *
   * @param x x in delegee coordinate system
   * @param y y in delegee coordinate system
   * @return area where editor component is located. This is the hint to the
   *         designer.  Designer can use or not this rectangle.
   */
  @Nullable
  public Rectangle getInplaceEditorBounds(@Nonnull Property property, int x, int y) {
    return null;
  }

  @Nonnull
  public final Class getComponentClass() {
    return myClass;
  }

  @Nonnull
  public String getComponentClassName() {
    return myClass.getName();
  }

  public final Object getCustomLayoutConstraints() {
    return myCustomLayoutConstraints;
  }

  public final void setCustomLayoutConstraints(Object customConstraints) {
    myCustomLayoutConstraints = customConstraints;
  }

  public void changeCustomLayoutConstraints(Object constraints) {
    setCustomLayoutConstraints(constraints);
    // update constraints in CardLayout
    JComponent parent = getParent().getDelegee();
    for (int i = 0; i < parent.getComponentCount(); i++) {
      if (parent.getComponent(i) == getDelegee()) {
        parent.remove(i);
        parent.add(getDelegee(), constraints, i);
        break;
      }
    }
  }

  public final boolean hasDragger() {
    return myHasDragger;
  }

  public final void setDragger(boolean hasDragger) {
    myHasDragger = hasDragger;
  }

  public boolean isResizing() {
    return myResizing;
  }

  public void setResizing(boolean resizing) {
    myResizing = resizing;
  }

  public boolean isDragging() {
    return myDragging;
  }

  public void setDragging(boolean dragging) {
    myDragging = dragging;
    RadContainer parent = getParent();
    if (parent != null) {
      parent.getLayoutManager().setChildDragging(this, dragging);
    }
  }

  public void setDragBorder(boolean dragging) {
    myDragging = dragging;
    myDragBorder = dragging;
  }

  public boolean isDragBorder() {
    return myDragBorder;
  }

  public boolean isDefaultBinding() {
    return myDefaultBinding;
  }

  public void setDefaultBinding(boolean defaultBinding) {
    myDefaultBinding = defaultBinding;
  }

  public final void addPropertyChangeListener(PropertyChangeListener l) {
    PropertyChangeListener[] propertyChangeListeners = myChangeSupport.getPropertyChangeListeners();
    for (PropertyChangeListener listener : propertyChangeListeners) {
      assert listener != l;
    }
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  protected final void firePropertyChanged(
    @Nonnull String propertyName,
    Object oldValue,
    Object newValue
  ) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * @return component's constarints.
   */
  @Nonnull
  public final GridConstraints getConstraints() {
    return myConstraints;
  }

  public final RadContainer getParent() {
    return myParent;
  }

  public final void setParent(RadContainer parent) {
    myParent = parent;
  }

  public boolean isSelected() {
    return mySelected;
  }

  public void setSelected(boolean selected) {
    if (mySelected != selected) {
      mySelected = selected;
      firePropertyChanged(PROP_SELECTED, !mySelected, mySelected);
      GuiEditor.repaintLayeredPane(this);
    }
  }

  /**
   * @see JComponent#getClientProperty(Object)
   */
  public final Object getClientProperty(@Nonnull Object key) {
    return myDelegee.getClientProperty(key);
  }

  /**
   * @see JComponent#putClientProperty(Object, Object)
   */
  public final void putClientProperty(@Nonnull Object key, Object value) {
    myDelegee.putClientProperty(key, value);
  }

  public final int getX() {
    return myDelegee.getX();
  }

  public final int getY() {
    return myDelegee.getY();
  }

  public final void setLocation(Point location) {
    myDelegee.setLocation(location);
  }

  public final void shift(int dx, int dy) {
    myDelegee.setLocation(myDelegee.getX() + dx, myDelegee.getY() + dy);
  }

  public final int getWidth() {
    return myDelegee.getWidth();
  }

  public final int getHeight() {
    return myDelegee.getHeight();
  }

  public final Dimension getSize() {
    return myDelegee.getSize();
  }

  public final void setSize(Dimension size) {
    myDelegee.setSize(size);
  }

  /**
   * @return bounds of the delegee in the parent container
   */
  public final Rectangle getBounds() {
    return myDelegee.getBounds();
  }

  public final void setBounds(Rectangle bounds) {
    myDelegee.setBounds(bounds);
  }

  public final Dimension getMinimumSize() {
    return Util.getMinimumSize(myDelegee, myConstraints, false);
  }

  public final Dimension getPreferredSize() {
    return Util.getPreferredSize(myDelegee, myConstraints, false);
  }

  public void refresh() {
  }

  public final void revalidate() {
    RadContainer theContainer = null;

    for (RadContainer container = this instanceof RadContainer ? (RadContainer)this : getParent();
         container != null;
         container = container.getParent()) {
      RadContainer parent = container.getParent();
      if (parent != null && parent.isXY()) {
        Dimension size = container.getSize();
        Dimension minimumSize = container.getMinimumSize();
        if (size.width < minimumSize.width || size.height < minimumSize.height) {
          theContainer = container;
        }
      }
    }

    if (theContainer != null) {
      Dimension minimumSize = theContainer.getMinimumSize();

      minimumSize.width = Math.max(minimumSize.width, theContainer.getWidth());
      minimumSize.height = Math.max(minimumSize.height, theContainer.getHeight());

      theContainer.getDelegee().setSize(minimumSize);
    }

    myDelegee.revalidate();
  }

  public final boolean isMarkedAsModified(Property property) {
    return myModifiedPropertyNames.contains(property.getName());
  }

  public final void markPropertyAsModified(Property property) {
    myModifiedPropertyNames.add(property.getName());
  }

  public final void removeModifiedProperty(Property property) {
    myModifiedPropertyNames.remove(property.getName());
  }

  public RadComponent getComponentToDrag(Point pnt) {
    return this;
  }

  public void processMouseEvent(MouseEvent event) {
  }

  @Nullable
  public EventProcessor getEventProcessor(MouseEvent event) {
    return null;
  }

  /**
   * Serializes component into the passed <code>writer</code>
   */
  public abstract void write(XmlWriter writer);

  /**
   * Serializes component's ID
   */
  protected final void writeId(XmlWriter writer) {
    writer.addAttribute("id", getId());
  }

  /**
   * Serializes component's class
   */
  protected final void writeClass(XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, getComponentClass().getName());
  }

  protected final void writeClassIfDifferent(XmlWriter writer, String defaultClassName) {
    if (!getComponentClassName().equals(defaultClassName)) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, getComponentClass().getName());
    }
  }

  protected final void writeBinding(XmlWriter writer) {
    // Binding
    if (getBinding() != null) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BINDING, getBinding());
    }
    if (isCustomCreate()) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CUSTOM_CREATE, Boolean.TRUE.toString());
    }
    if (isDefaultBinding()) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_DEFAULT_BINDING, Boolean.TRUE.toString());
    }
  }

  protected void writeConstraints(XmlWriter writer) {
    writer.startElement("constraints");
    try {
      if (getParent() != null) {
        getParent().getLayoutManager().writeChildConstraints(writer, this);
      }
    }
    finally {
      writer.endElement(); // constraints
    }
  }

  protected final void writeProperties(XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_PROPERTIES);
    try {
      IntrospectedProperty[] introspectedProperties =
        getPalette().getIntrospectedProperties(this);
      for (IntrospectedProperty property : introspectedProperties) {
        if (isMarkedAsModified(property)) {
          Object value = property.getValue(this);
          if (value != null) {
            writer.startElement(property.getName());
            try {
              //noinspection unchecked
              property.write(value, writer);
            }
            finally {
              writer.endElement();
            }
          }
        }
      }
    }
    finally {
      writer.endElement(); // properties
    }
    writeClientProperties(writer);
  }

  private void writeClientProperties(XmlWriter writer) {
    if (myModule == null) {
      return;
    }
    boolean haveClientProperties = false;
    try {
      ClientPropertiesProperty cpp = ClientPropertiesProperty.getInstance(getProject());
      for (Property prop : cpp.getChildren(this)) {
        ClientPropertyProperty clientProp = (ClientPropertyProperty)prop;
        Object value = getDelegee().getClientProperty(clientProp.getName());
        if (value != null) {
          if (!haveClientProperties) {
            writer.startElement(UIFormXmlConstants.ELEMENT_CLIENT_PROPERTIES);
            haveClientProperties = true;
          }
          writer.startElement(clientProp.getName());
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_CLASS, value.getClass().getName());
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.toString());
          writer.endElement();
        }
      }
    }
    finally {
      if (haveClientProperties) {
        writer.endElement();
      }
    }
  }

  public void fireConstraintsChanged(GridConstraints oldConstraints) {
    firePropertyChanged(PROP_CONSTRAINTS, oldConstraints, myConstraints);
  }

  public IProperty[] getModifiedProperties() {
    IntrospectedProperty[] props = getPalette().getIntrospectedProperties(this);
    ArrayList<IProperty> result = new ArrayList<>();
    for (IntrospectedProperty prop : props) {
      if (isMarkedAsModified(prop)) {
        result.add(prop);
      }
    }
    return result.toArray(new IProperty[result.size()]);
  }

  public IContainer getParentContainer() {
    return myParent;
  }

  public boolean hasIntrospectedProperties() {
    return true;
  }

  public boolean accept(ComponentVisitor visitor) {
    return visitor.visit(this);
  }

  public boolean areChildrenExclusive() {
    return false;
  }

  public void loadLwProperty(LwComponent lwComponent,
                             LwIntrospectedProperty lwProperty,
                             IntrospectedProperty property) {
    myLoadingProperties = true;
    try {
      try {
        Object value = lwComponent.getPropertyValue(lwProperty);
        //noinspection unchecked
        property.setValue(this, value);
      }
      catch (Exception e) {
        LOG.error(e);
        //TODO[anton,vova]: show error and continue to load form
      }
    }
    finally {
      myLoadingProperties = false;
    }
  }

  public void doneLoadingFromLw() {
  }

  @Nullable
  public static RadComponent createSnapshotComponent(SnapshotContext context, JComponent component) {
    String id = context.newId();
    RadComponent result;

    Class componentClass = component.getClass();
    if (componentClass.isAnonymousClass()) {
      componentClass = componentClass.getSuperclass();
    }
    if (component instanceof JPanel && !isCompositeComponent(component)) {
      RadContainer container = new RadContainer(componentClass, id, context.getPalette());
      RadLayoutManager manager = LayoutManagerRegistry.createFromLayout(component.getLayout());
      if (manager == null) {
        return null;
      }
      container.setLayoutManager(manager);
      result = container;
    }
    else if (component instanceof Box.Filler) {
      Box.Filler filler = (Box.Filler)component;
      if (filler.getMaximumSize().height == Short.MAX_VALUE) {
        result = new RadVSpacer(null, id);
        result.getConstraints().setVSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
      }
      else {
        result = new RadHSpacer(null, id);
        result.getConstraints().setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
      }
    }
    else {
      RadComponentFactory factory = InsertComponentProcessor.getRadComponentFactory(componentClass);
      if (factory == null) {
        result = new RadAtomicComponent(componentClass, id, context.getPalette());
      }
      else {
        result = factory.newInstance(componentClass, id, context.getPalette());
      }
    }

    context.registerComponent(component, result);
    result.importSnapshotComponent(context, component);

    IntrospectedProperty[] properties = context.getPalette().getIntrospectedProperties(component.getClass(),
                                                                                             result.getDelegee().getClass());
    for (IntrospectedProperty prop : properties) {
      if (component instanceof AbstractButton) {
        AbstractButton btn = (AbstractButton)component;
        if (prop.getName().equals(SwingProperties.LABEL) && btn.getLabel().equals(btn.getText())) {
          continue;
        }
        if (prop.getName().equals(SwingProperties.ACTION_COMMAND) && btn.getActionCommand().equals(btn.getText())) {
          continue;
        }
      }
      prop.importSnapshotValue(context, component, result);
    }

    if (component instanceof AbstractButton) {
      AbstractButton btn = (AbstractButton)component;
      if (btn.getModel() instanceof DefaultButtonModel) {
        DefaultButtonModel model = (DefaultButtonModel)btn.getModel();
        if (model.getGroup() != null) {
          context.registerButtonGroup(model.getGroup());
        }
      }
    }

    return result;
  }

  private static boolean isCompositeComponent(JComponent component) {
    if (component.getComponentCount() == 0) {
      return false;
    }

    JComponent instance;
    try {
      instance = component.getClass().newInstance();
    }
    catch (Exception ex) {
      return false;
    }
    return instance.getComponentCount() == component.getComponentCount();
  }

  protected void importSnapshotComponent(SnapshotContext context, JComponent component) {
  }

  @Nullable
  public String getComponentTitle() {
    Palette palette = Palette.getInstance(getProject());
    IntrospectedProperty[] props = palette.getIntrospectedProperties(this);
    for (IntrospectedProperty prop : props) {
      if (prop.getName().equals(SwingProperties.TEXT) && prop instanceof IntroStringProperty) {
        StringDescriptor value = (StringDescriptor)prop.getValue(this);
        if (value != null) {
          return "\"" + value.getResolvedValue() + "\"";
        }
      }
    }

    if (this instanceof RadContainer) {
      RadContainer container = (RadContainer)this;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null) {
        if (descriptor.getResolvedValue() == null) {
          descriptor.setResolvedValue(StringDescriptorManager.getInstance(getModule()).resolve(this, descriptor));
        }
        return "\"" + descriptor.getResolvedValue() + "\"";
      }
    }

    if (getParent() instanceof RadTabbedPane) {
      RadTabbedPane parentTabbedPane = (RadTabbedPane)getParent();
      StringDescriptor descriptor = parentTabbedPane.getChildTitle(this);
      if (descriptor != null) {
        if (descriptor.getResolvedValue() == null) {
          descriptor.setResolvedValue(StringDescriptorManager.getInstance(getModule()).resolve(this, descriptor));
        }
        return "\"" + descriptor.getResolvedValue() + "\"";
      }
      else {
        parentTabbedPane.getChildTitle(this);
      }
    }
    return null;
  }

  public String getDisplayName() {
    StringBuilder titleBuilder = new StringBuilder();
    if (getBinding() != null) {
      titleBuilder.append(getBinding());
    }
    else {
      String className = getComponentClassName();
      int pos = className.lastIndexOf('.');
      if (pos < 0) {
        titleBuilder.append(className);
      }
      else {
        titleBuilder.append(className.substring(pos + 1).replace('$', '.'));
      }
      String title = getComponentTitle();
      if (title != null) {
        titleBuilder.append(" ").append(title);
      }
    }
    return titleBuilder.toString();
  }
}
