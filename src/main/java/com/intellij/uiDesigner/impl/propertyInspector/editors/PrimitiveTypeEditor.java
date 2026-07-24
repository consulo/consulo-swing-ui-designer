
package com.intellij.uiDesigner.impl.propertyInspector.editors;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public final class PrimitiveTypeEditor<T> extends AbstractTextFieldEditor<T> {
  private final Class<T> myClass;

  public PrimitiveTypeEditor(Class<T> aClass) {
    myClass = aClass;
  }

  public T getValue() throws Exception {
    try {
      Method method = myClass.getMethod("valueOf", String.class);
      //noinspection unchecked
      return (T) method.invoke(null, myTf.getText());
    }
    catch (NumberFormatException e) {
      throw new RuntimeException("Entered value is not a valid number of this property type");
    }
  }
}
