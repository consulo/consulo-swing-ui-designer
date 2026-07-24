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
package com.intellij.uiDesigner.impl.make;

import com.intellij.uiDesigner.compiler.GridBagConverter;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class GridBagLayoutSourceGenerator extends LayoutSourceGenerator {
  private boolean myHaveGbc = false;
  @NonNls private static final IntObjectMap<String> myFillMap = IntMaps.newIntObjectHashMap();
  @NonNls private static final IntObjectMap<String> myAnchorMap = IntMaps.newIntObjectHashMap();

  static {
    myFillMap.put(GridBagConstraints.HORIZONTAL, "java.awt.GridBagConstraints.HORIZONTAL");
    myFillMap.put(GridBagConstraints.VERTICAL, "java.awt.GridBagConstraints.VERTICAL");
    myFillMap.put(GridBagConstraints.BOTH, "java.awt.GridBagConstraints.BOTH");

    myAnchorMap.put(GridBagConstraints.NORTHWEST, "java.awt.GridBagConstraints.NORTHWEST");
    myAnchorMap.put(GridBagConstraints.NORTH, "java.awt.GridBagConstraints.NORTH");
    myAnchorMap.put(GridBagConstraints.NORTHEAST, "java.awt.GridBagConstraints.NORTHEAST");
    myAnchorMap.put(GridBagConstraints.EAST, "java.awt.GridBagConstraints.EAST");
    myAnchorMap.put(GridBagConstraints.SOUTHEAST, "java.awt.GridBagConstraints.SOUTHEAST");
    myAnchorMap.put(GridBagConstraints.SOUTH, "java.awt.GridBagConstraints.SOUTH");
    myAnchorMap.put(GridBagConstraints.SOUTHWEST, "java.awt.GridBagConstraints.SOUTHWEST");
    myAnchorMap.put(GridBagConstraints.WEST, "java.awt.GridBagConstraints.WEST");
  }

  @Override
  public void generateContainerLayout(LwContainer container,
                                      FormSourceCodeGenerator generator,
                                      String variable) {
    generator.startMethodCall(variable, "setLayout");

    generator.startConstructor(GridBagLayout.class.getName());
    generator.endConstructor();

    generator.endMethod();
  }

  @Override
  public void generateComponentLayout(LwComponent component,
                                      FormSourceCodeGenerator generator,
                                      String variable,
                                      String parentVariable) {
    GridBagConstraints gbc;
    if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
      gbc = (GridBagConstraints) component.getCustomLayoutConstraints();
    }
    else {
      gbc = new GridBagConstraints();
    }

    GridBagConverter.constraintsToGridBag(component.getConstraints(), gbc);

    generateGridBagConstraints(generator, gbc, variable, parentVariable);
  }

  private void generateConversionResult(FormSourceCodeGenerator generator,
                                        GridBagConverter.Result result,
                                        String variable, String parentVariable) {
    checkSetSize(generator, variable, "setMinimumSize", result.minimumSize);
    checkSetSize(generator, variable, "setPreferredSize", result.preferredSize);
    checkSetSize(generator, variable, "setMaximumSize", result.maximumSize);

    generateGridBagConstraints(generator, result.constraints, variable, parentVariable);
  }

  private void generateGridBagConstraints(FormSourceCodeGenerator generator,
                                          GridBagConstraints constraints,
                                          String variable,
                                          String parentVariable) {
    if (!myHaveGbc) {
      generator.append("java.awt.GridBagConstraints gbc;\n");
      myHaveGbc = true;
    }
    generator.append("gbc = new java.awt.GridBagConstraints();\n");

    GridBagConstraints defaults = new GridBagConstraints();
    if (defaults.gridx != constraints.gridx) {
      setIntField(generator, "gridx", constraints.gridx);
    }
    if (defaults.gridy != constraints.gridy) {
      setIntField(generator, "gridy", constraints.gridy);
    }
    if (defaults.gridwidth != constraints.gridwidth) {
      setIntField(generator, "gridwidth", constraints.gridwidth);
    }
    if (defaults.gridheight != constraints.gridheight) {
      setIntField(generator, "gridheight", constraints.gridheight);
    }
    if (defaults.weightx != constraints.weightx) {
      setDoubleField(generator, "weightx", constraints.weightx);
    }
    if (defaults.weighty != constraints.weighty) {
      setDoubleField(generator, "weighty", constraints.weighty);
    }
    if (defaults.anchor != constraints.anchor) {
      setIntField(generator, "anchor", constraints.anchor, myAnchorMap);
    }
    if (defaults.fill != constraints.fill) {
      setIntField(generator, "fill", constraints.fill, myFillMap);
    }
    if (defaults.ipadx != constraints.ipadx) {
      setIntField(generator, "ipadx", constraints.ipadx);
    }
    if (defaults.ipady != constraints.ipady) {
      setIntField(generator, "ipady", constraints.ipady);
    }
    if (!defaults.insets.equals(constraints.insets)) {
      generator.append("gbc.insets=");
      generator.newInsets(constraints.insets);
    }

    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.pushVar("gbc");
    generator.endMethod();
  }

  private static void setIntField(FormSourceCodeGenerator generator, @NonNls String fieldName, int value) {
    generator.append("gbc.");
    generator.append(fieldName);
    generator.append("=");
    generator.append(value);
    generator.append(";\n");
  }

  private static void setIntField(FormSourceCodeGenerator generator, @NonNls String fieldName, int value,
                                  IntObjectMap<String> map) {
    generator.append("gbc.");
    generator.append(fieldName);
    generator.append("=");
    if (map.containsKey(value)) {
      generator.append(map.get(value));
    }
    else {
      generator.append(value);
    }
    generator.append(";\n");
  }

  private static void setDoubleField(FormSourceCodeGenerator generator, @NonNls String fieldName, double value) {
    generator.append("gbc.");
    generator.append(fieldName);
    generator.append("=");
    generator.append(value);
    generator.append(";\n");
  }

  private static void checkSetSize(FormSourceCodeGenerator generator,
                                   String variable,
                                   @NonNls String methodName,
                                   Dimension dimension) {
    if (dimension != null) {
      generator.startMethodCall(variable, methodName);
      generator.newDimension(dimension);
      generator.endMethod();
    }
  }

  @Override
  public String mapComponentClass(String componentClassName) {
    if (componentClassName.equals(Spacer.class.getName())) {
      return JPanel.class.getName();
    }
    return super.mapComponentClass(componentClassName);
  }
}
