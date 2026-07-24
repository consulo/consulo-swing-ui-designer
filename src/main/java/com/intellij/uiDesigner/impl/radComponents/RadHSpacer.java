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

import com.intellij.uiDesigner.impl.HSpacer;
import com.intellij.uiDesigner.impl.ModuleProvider;
import com.intellij.uiDesigner.impl.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.impl.palette.Palette;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadHSpacer extends RadAtomicComponent {
  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(ModuleProvider moduleProvider, Class aClass, String id) {
      return new RadHSpacer(moduleProvider, aClass, id);
    }

    public RadComponent newInstance(Class componentClass, String id, Palette palette) {
      throw new UnsupportedOperationException("Spacer instances should not be created by SnapShooter");
    }

    public RadComponent newInstance(ModuleProvider moduleProvider, String className, String id) throws ClassNotFoundException {
      return new RadHSpacer(moduleProvider, HSpacer.class, id);
    }
  }

  public RadHSpacer(ModuleProvider moduleProvider, String id) {
    super(moduleProvider, HSpacer.class, id);
  }

  public RadHSpacer(ModuleProvider moduleProvider, Class aClass, String id) {
    super(moduleProvider, aClass, id);
  }

  /**
   * Constructor for use in SnapShooter
   */
  public RadHSpacer(String id, int column) {
    super(null, HSpacer.class, id);
    getConstraints().setColumn(column);
    getConstraints().setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW |
                                    GridConstraints.SIZEPOLICY_WANT_GROW);
    getConstraints().setFill(GridConstraints.FILL_HORIZONTAL);
  }

  public void write(XmlWriter writer) {
    writer.startElement("hspacer");
    try {
      writeId(writer);
      writeConstraints(writer);
    }
    finally {
      writer.endElement(); // hspacer
    }
  }

  @Override
  public boolean hasIntrospectedProperties() {
    return false;
  }
}
