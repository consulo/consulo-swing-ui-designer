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
package com.intellij.uiDesigner.impl.wizard;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanProperty implements Comparable<BeanProperty>{
  /**
   * Property name.
   */
  @Nonnull
  public final String myName;
  /**
   * Property type.
   * There are two possible types:
   * <ul>
   *  <li>java.lang.String</li>
   *  <li>boolean</li>
   * </ul>
   */
  @Nonnull
  public final String myType;

  public BeanProperty(@Nonnull String name, @Nonnull String type) {
    if(!"java.lang.String".equals(type) && !"boolean".equals(type)){
      throw new IllegalArgumentException("unknown type: " + type);
    }

    myName = name;
    myType = type;
  }

  @Override
  public int compareTo(BeanProperty property) {
    if(property == null){
      return 1;
    }
    else{
      return myName.compareTo(property.myName);
    }
  }

  /**
   * This method is used by ComboBox editor of {@link BindToExistingBeanStep.MyTableCellEditor}
   */
  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    return obj instanceof BeanProperty that
        && myName.equals(that.myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }
}
