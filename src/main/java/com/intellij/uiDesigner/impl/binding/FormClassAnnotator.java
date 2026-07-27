/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.uiDesigner.impl.binding;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.annotation.Annotation;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Objects;

/**
 * @author yole
 */
public class FormClassAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance(FormClassAnnotator.class);

  @Override
  @RequiredReadAction
  public void annotate(@Nonnull PsiElement psiElement, @Nonnull AnnotationHolder holder) {
    if (psiElement instanceof PsiField) {
      PsiField field = (PsiField) psiElement;
      PsiFile boundForm = FormReferenceProvider.getFormFile(field);
      if (boundForm != null) {
        annotateFormField(field, boundForm, holder);
      }
    }
    else if (psiElement instanceof PsiClass) {
      PsiClass aClass = (PsiClass) psiElement;
      List<PsiFile> formsBoundToClass = FormClassIndex.findFormsBoundToClass(aClass);
      if (formsBoundToClass.size() > 0) {
        Annotation boundClassAnnotation = holder.createInfoAnnotation(aClass.getNameIdentifier(), null);
        boundClassAnnotation.setGutterIconRenderer(new BoundIconRenderer(aClass));
      }
    }
  }

  @RequiredReadAction
  private static void annotateFormField(final PsiField field, PsiFile boundForm, AnnotationHolder holder) {
    Annotation boundFieldAnnotation = holder.createInfoAnnotation(field, null);
    boundFieldAnnotation.setGutterIconRenderer(new BoundIconRenderer(field));

    LOG.assertTrue(boundForm instanceof PsiPlainTextFile);
    PsiType guiComponentType = FormReferenceProvider.getGUIComponentType((PsiPlainTextFile)boundForm, field.getName());
    if (guiComponentType != null) {
      PsiType fieldType = field.getType();
      if (!fieldType.isAssignableFrom(guiComponentType)) {
        holder.newError(UIDesignerLocalize.boundFieldTypeMismatch(guiComponentType.getCanonicalText(), fieldType.getCanonicalText()))
          .range(field.getTypeElement())
          .withFix(new ChangeFormComponentTypeFix((PsiPlainTextFile)boundForm, field.getName(), field.getType()))
          .withFix(new ChangeBoundFieldTypeFix(field, guiComponentType))
          .create();
      }
    }

    if (field.hasInitializer()) {
      holder.newWarn(UIDesignerLocalize.fieldIsOverwrittenByGeneratedCode(StringUtil.notNullize(field.getName())))
        .range(field.getInitializer())
        .withFix(new IntentionAction() {
          @Nonnull
          @Override
          public LocalizeValue getText() {
            return UIDesignerLocalize.fieldIsOverwrittenByGeneratedCode(field.getName());
          }

          @Override
          public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
            return field.getInitializer() != null;
          }

          @Override
          @RequiredWriteAction
          public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            if (!FileModificationService.getInstance().preparePsiElementForWrite(field)) return;
            PsiExpression initializer = Objects.requireNonNull(field.getInitializer());
            initializer.delete();
          }

          @Override
          public boolean startInWriteAction() {
            return true;
          }
        })
        .create();
    }
  }
}
