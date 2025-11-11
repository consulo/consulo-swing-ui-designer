/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.uiDesigner.impl.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.impl.quickFixes.PopupQuickFix;
import com.intellij.uiDesigner.impl.quickFixes.QuickFix;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.spellchecker.editor.SpellcheckerEngineManager;
import consulo.language.spellcheker.tokenizer.splitter.PlainTextTokenSplitter;
import consulo.language.spellcheker.tokenizer.splitter.SplitContext;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.popup.*;

import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class FormSpellCheckingInspection extends StringDescriptorInspection {
    public static final String SHORT_NAME = "SpellCheckingInspection";

    public FormSpellCheckingInspection() {
        super(SHORT_NAME);
    }

    @Override
    protected void checkStringDescriptor(
        Module module,
        IComponent component,
        final IProperty prop,
        final StringDescriptor descriptor,
        FormErrorCollector collector
    ) {
        String value = descriptor.getResolvedValue();
        if (value == null) {
            return;
        }
        Project project = module.getProject();
        final SpellcheckerEngineManager manager = project.getInstance(SpellcheckerEngineManager.class);
        PlainTextTokenSplitter.getInstance().split(
            SplitContext.of(
                value,
                textRange -> {
                    final String word = textRange.substring(value);
                    if (manager.hasProblem(project, word)) {
                        final List<String> suggestions = manager.getSuggestions(project, value);
                        if (suggestions.size() > 0 && prop instanceof IntroStringProperty) {
                            EditorQuickFixProvider changeToProvider =
                                (editor, component12) -> new PopupQuickFix<String>(editor, "Change to...", component12) {
                                    @Override
                                    public void run() {
                                        ListPopup popup = JBPopupFactory.getInstance().createListPopup(getPopupStep());
                                        popup.showUnderneathOf(component12.getDelegee());
                                    }

                                    @Override
                                    public ListPopupStep<String> getPopupStep() {
                                        return new BaseListPopupStep<String>("Select Replacement", suggestions) {
                                            @Override
                                            public PopupStep onChosen(String selectedValue, boolean finalChoice) {
                                                FormInspectionUtil.updateStringPropertyValue(
                                                    editor,
                                                    component12,
                                                    (IntroStringProperty) prop,
                                                    descriptor,
                                                    selectedValue
                                                );
                                                return FINAL_CHOICE;
                                            }
                                        };
                                    }
                                };

                            if (manager.canSaveUserWords(project)) {
                                EditorQuickFixProvider acceptProvider =
                                    (editor, component1) -> new QuickFix(editor, "Save '" + word + "' to dictionary", component1) {
                                        @Override
                                        public void run() {
                                            manager.acceptWordAsCorrect(editor.getProject(), word);
                                        }
                                    };
                                collector.addError(
                                    getID(),
                                    component,
                                    prop,
                                    "Typo in word '" + word + "'",
                                    changeToProvider,
                                    acceptProvider
                                );
                            }
                            else {
                                collector.addError(getID(), component, prop, "Typo in word '" + word + "'", changeToProvider);
                            }
                        }
                        else {
                            collector.addError(getID(), component, prop, "Typo in word '" + word + "'");
                        }
                    }
                }
            ),
            TextRange.allOf(value)
        );
    }
}
