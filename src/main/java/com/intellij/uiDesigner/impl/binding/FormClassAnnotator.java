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
import consulo.ui.ex.localize.UILocalize;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author yole
 */
public class FormClassAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance(FormClassAnnotator.class);

  public void annotate(@Nonnull PsiElement psiElement, @Nonnull AnnotationHolder holder) {
    if (psiElement instanceof PsiField) {
      PsiField field = (PsiField) psiElement;
      final PsiFile boundForm = FormReferenceProvider.getFormFile(field);
      if (boundForm != null) {
        annotateFormField(field, boundForm, holder);
      }
    }
    else if (psiElement instanceof PsiClass) {
      PsiClass aClass = (PsiClass) psiElement;
      final List<PsiFile> formsBoundToClass = FormClassIndex.findFormsBoundToClass(aClass);
      if (formsBoundToClass.size() > 0) {
        Annotation boundClassAnnotation = holder.createInfoAnnotation(aClass.getNameIdentifier(), null);
        boundClassAnnotation.setGutterIconRenderer(new BoundIconRenderer(aClass));
      }
    }
  }

  private static void annotateFormField(final PsiField field, final PsiFile boundForm, final AnnotationHolder holder) {
    Annotation boundFieldAnnotation = holder.createInfoAnnotation(field, null);
    boundFieldAnnotation.setGutterIconRenderer(new BoundIconRenderer(field));

    LOG.assertTrue(boundForm instanceof PsiPlainTextFile);
    final PsiType guiComponentType = FormReferenceProvider.getGUIComponentType((PsiPlainTextFile)boundForm, field.getName());
    if (guiComponentType != null) {
      final PsiType fieldType = field.getType();
      if (!fieldType.isAssignableFrom(guiComponentType)) {
        String message = UIDesignerLocalize.boundFieldTypeMismatch(guiComponentType.getCanonicalText(), fieldType.getCanonicalText()).get();
        Annotation annotation = holder.createErrorAnnotation(field.getTypeElement(), message);
        annotation.registerFix(new ChangeFormComponentTypeFix((PsiPlainTextFile)boundForm, field.getName(), field.getType()), null, null);
        annotation.registerFix(new ChangeBoundFieldTypeFix(field, guiComponentType), null, null);
      }
    }

    if (field.hasInitializer()) {
      final LocalizeValue message = UIDesignerLocalize.fieldIsOverwrittenByGeneratedCode(field.getName());
      Annotation annotation = holder.createWarningAnnotation(field.getInitializer(), message.get());
      annotation.registerFix(new IntentionAction() {
        @Nonnull
        public LocalizeValue getText() {
          return message;
        }

        @Nonnull
        public LocalizeValue getFamilyName() {
          return UILocalize.removeFieldInitializerQuickFix();
        }

        public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
          return field.getInitializer() != null;
        }

        public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          if (!FileModificationService.getInstance().preparePsiElementForWrite(field)) return;
          final PsiExpression initializer = field.getInitializer();
          LOG.assertTrue(initializer != null);
          initializer.delete();
        }

        public boolean startInWriteAction() {
          return true;
        }
      });
    }
  }
}
