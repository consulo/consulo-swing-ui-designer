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
package com.intellij.uiDesigner.impl.propertyInspector.renderers;

import jakarta.annotation.Nonnull;

import com.intellij.uiDesigner.lw.StringDescriptor;
import consulo.util.lang.StringUtil;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringRenderer extends LabelPropertyRenderer<StringDescriptor> {

  protected void customize(@Nonnull StringDescriptor value) {
    String resolvedValue = value.getResolvedValue();
    if (resolvedValue == null) {
      resolvedValue = value.getValue();
    }
    if (resolvedValue != null) {
      setText(StringUtil.escapeStringCharacters(resolvedValue));
    }
    else {
      setText("");
    }
  }
}
