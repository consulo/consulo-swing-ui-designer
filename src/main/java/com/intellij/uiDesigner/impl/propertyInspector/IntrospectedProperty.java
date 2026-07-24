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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.uiDesigner.core.UIFormXmlConstants;
import com.intellij.uiDesigner.impl.SwingProperties;
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import com.intellij.uiDesigner.impl.radComponents.RadGridLayoutManager;
import com.intellij.uiDesigner.impl.snapShooter.SnapshotContext;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class IntrospectedProperty<V> extends Property<RadComponent, V> {
  protected final static Object[] EMPTY_OBJECT_ARRAY=new Object[]{};

  /**
   * This method is used to set property value to "delegee" JComponent
   */
  @Nonnull
  protected final Method myReadMethod;
  /**
   * This method is used to get property value from "delegee" JComponent
   */
  @Nonnull
  private final Method myWriteMethod;

  private final boolean myStoreAsClient;

  @NonNls private static final String INTRO_PREFIX = "Intro:";

  public IntrospectedProperty(String name,
                              @Nonnull Method readMethod,
                              @Nonnull Method writeMethod,
                              boolean storeAsClient) {
    super(null, name);
    myReadMethod = readMethod;
    myWriteMethod = writeMethod;
    myStoreAsClient = storeAsClient;
  }

  /**
   * <b>Do not overide this method without serious reason!</b>
   */
  public V getValue(RadComponent component){
    //noinspection unchecked
    return (V)invokeGetter(component);
  }

  protected Object invokeGetter(RadComponent component) {
    if (myStoreAsClient) {
      return component.getClientProperty(INTRO_PREFIX + getName());
    }
    try {
      myReadMethod.setAccessible(true);
      return myReadMethod.invoke(component.getDelegee(), EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * <b>Do not overide this method without serious reason!</b>
   */
  protected void setValueImpl(RadComponent component, V value) throws Exception{
    invokeSetter(component, value);
  }

  protected void invokeSetter(RadComponent component, Object value) throws IllegalAccessException, InvocationTargetException {
    if (myStoreAsClient) {
      component.putClientProperty(INTRO_PREFIX + getName(), value);
    }
    else {
      myWriteMethod.setAccessible(true);
      myWriteMethod.invoke(component.getDelegee(), value);
    }
  }

  /**
   * Serializes (writes) propertie's value
   *
   * @param value property value which should be serialized.
   * @param writer writer which should be used for serialization. It is assumed that
   * before invocation of this method <code>writer</code> already has opened tag
   * that corresponds to this property. You can just append some attributes
   * here or add some subtags.
   */
  public void write(@Nonnull V value, XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.toString());
  }

  @Override public boolean isModified(RadComponent component) {
    return component.isMarkedAsModified(this);
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    V defaultValue = getDefaultValue(component.getDelegee());
    invokeSetter(component, defaultValue);
    markTopmostModified(component, false);
  }

  public void importSnapshotValue(SnapshotContext context, JComponent component, RadComponent radComponent) {
    try {
      //noinspection unchecked
      V value = (V) myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
      V defaultValue = getDefaultValue(radComponent.getDelegee());
      if (!Comparing.equal(value, defaultValue)) {
        setValue(radComponent, value);
      }
    }
    catch (Exception e) {
      // ignore
    }
  }

  protected V getDefaultValue(JComponent delegee) throws Exception {
    if (myStoreAsClient) {
      return null;
    }
    Constructor constructor = delegee.getClass().getConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
    constructor.setAccessible(true);
    JComponent newComponent = (JComponent)constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
    //noinspection unchecked
    return (V) myReadMethod.invoke(newComponent, EMPTY_OBJECT_ARRAY);
  }

  @Override
  public boolean appliesTo(RadComponent component) {
    @NonNls String name = getName();
    //noinspection SimplifiableIfStatement
    if (name.equals(SwingProperties.PREFERRED_SIZE) ||
        name.equals(SwingProperties.MINIMUM_SIZE) ||
        name.equals(SwingProperties.MAXIMUM_SIZE)) {
      // our own properties must be used instead
      RadContainer parent = component.getParent();
      return parent != null && !(parent.getLayoutManager() instanceof RadGridLayoutManager);
    }

    // check if property is available in the JDK used by the module containing the component
    PsiManager psiManager = PsiManager.getInstance(component.getProject());
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(component.getModule(), true);
    PsiClass componentClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
    if (componentClass == null) return true;
    PsiMethod[] psiMethods = componentClass.findMethodsByName(myReadMethod.getName(), true);
    for(PsiMethod method: psiMethods) {
      if (!method.hasModifierProperty(PsiModifier.STATIC) &&
          method.getParameterList().getParametersCount() == 0) {
        return true;
      }
    }
    return false;
  }
}
