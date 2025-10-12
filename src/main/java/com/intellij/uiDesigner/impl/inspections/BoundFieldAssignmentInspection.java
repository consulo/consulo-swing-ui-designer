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

package com.intellij.uiDesigner.impl.inspections;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.language.psi.*;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.impl.binding.FieldFormReference;
import com.intellij.uiDesigner.impl.binding.FormReferenceProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
@ExtensionImpl
public class BoundFieldAssignmentInspection extends BaseJavaLocalInspectionTool {
    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return UIDesignerLocalize.formInspectionsGroup();
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return UIDesignerLocalize.inspectionBoundFieldTitle();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "BoundFieldAssignment";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitorImpl(@Nonnull ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session, Object o) {
        return new JavaElementVisitor() {
            @Override
            public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                if (expression.getLExpression() instanceof PsiReferenceExpression) {
                    PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
                    if (method != null && AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) {
                        return;
                    }
                    PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
                    PsiElement lElement = lExpr.resolve();
                    if (lElement instanceof PsiField) {
                        PsiField field = (PsiField) lElement;
                        PsiReference formReference = FormReferenceProvider.getFormReference(field);
                        if (formReference instanceof FieldFormReference) {
                            FieldFormReference ref = (FieldFormReference) formReference;
                            if (!ref.isCustomCreate()) {
                                holder.registerProblem(expression, UIDesignerLocalize.inspectionBoundFieldMessage().get(),
                                    new LocalQuickFix[0]);
                            }
                        }
                    }
                }
            }
        };
    }
}
