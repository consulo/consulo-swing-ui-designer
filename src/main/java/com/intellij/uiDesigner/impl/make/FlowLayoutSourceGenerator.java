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

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;

import java.awt.*;

/**
 * @author yole
 */
public class FlowLayoutSourceGenerator extends LayoutSourceGenerator {
  private static final IntObjectMap<String> myAlignMap = IntMaps.newIntObjectHashMap();

  static {
    myAlignMap.put(FlowLayout.CENTER, "FlowLayout.CENTER");
    myAlignMap.put(FlowLayout.LEFT, "FlowLayout.LEFT");
    myAlignMap.put(FlowLayout.RIGHT, "FlowLayout.RIGHT");
    myAlignMap.put(FlowLayout.LEADING, "FlowLayout.LEADING");
    myAlignMap.put(FlowLayout.TRAILING, "FlowLayout.TRAILING");
  }

  @Override public void generateContainerLayout(LwContainer component,
                                                FormSourceCodeGenerator generator,
                                                String variable) {
    generator.startMethodCall(variable, "setLayout");

    FlowLayout layout = (FlowLayout) component.getLayout();

    generator.startConstructor(FlowLayout.class.getName());
    generator.push(layout.getAlignment(), myAlignMap);
    generator.push(layout.getHgap());
    generator.push(layout.getVgap());
    generator.endConstructor();

    generator.endMethod();
  }

  @Override
  public void generateComponentLayout(LwComponent component,
                                      FormSourceCodeGenerator generator,
                                      String variable,
                                      String parentVariable) {
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.endMethod();
  }
}
