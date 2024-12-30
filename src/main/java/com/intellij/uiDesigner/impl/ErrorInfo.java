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
package com.intellij.uiDesigner.impl;

import jakarta.annotation.Nonnull;

import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.IComponent;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import org.jetbrains.annotations.NonNls;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorInfo {
  public static final ErrorInfo[] EMPTY_ARRAY = new ErrorInfo[0];

  private final RadComponent myComponent;
  public final String myDescription;
  private final String myPropertyName;
  public final QuickFix[] myFixes;
  private final HighlightDisplayLevel myHighlightDisplayLevel;
  private String myInspectionId;

  public ErrorInfo(IComponent component, @NonNls final String propertyName, @Nonnull final String description,
                   @Nonnull HighlightDisplayLevel highlightDisplayLevel, @Nonnull final QuickFix[] fixes) {
    myComponent = component instanceof RadComponent ? (RadComponent) component : null;
    myHighlightDisplayLevel = highlightDisplayLevel;
    myPropertyName = propertyName;
    myDescription = description;
    myFixes = fixes;
  }

  public String getPropertyName() {
    return myPropertyName;
  }

  public HighlightDisplayLevel getHighlightDisplayLevel() {
    return myHighlightDisplayLevel;
  }

  public String getInspectionId() {
    return myInspectionId;
  }

  public void setInspectionId(final String inspectionId) {
    myInspectionId = inspectionId;
  }

  public RadComponent getComponent() {
    return myComponent;
  }
}
