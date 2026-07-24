/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.SerializedComponentData;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import consulo.dataContext.DataContext;
import consulo.logging.Logger;
import consulo.ui.ex.CopyProvider;
import consulo.ui.ex.CutProvider;
import consulo.ui.ex.PasteProvider;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.ui.ex.awt.dnd.FileCopyPasteUtil;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class CutCopyPasteSupport implements CopyProvider, CutProvider, PasteProvider {
    private static final Logger LOG = Logger.getInstance(CutCopyPasteSupport.class);
    private static final SAXBuilder SAX_BUILDER = new SAXBuilder();

    private final GuiEditor myEditor;
    @NonNls
    private static final String ELEMENT_SERIALIZED = "serialized";
    @NonNls
    private static final String ATTRIBUTE_X = "x";
    @NonNls
    private static final String ATTRIBUTE_Y = "y";
    @NonNls
    private static final String ATTRIBUTE_PARENT_LAYOUT = "parent-layout";

    public CutCopyPasteSupport(GuiEditor uiEditor) {
        myEditor = uiEditor;
    }

    @Override
    public boolean isCopyEnabled(@Nonnull DataContext dataContext) {
        return FormEditingUtil.getSelectedComponents(myEditor).size() > 0 && !myEditor.getInplaceEditingLayer().isEditing();
    }

    @Override
    public boolean isCopyVisible(@Nonnull DataContext dataContext) {
        return true;
    }

    @Override
    public void performCopy(@Nonnull DataContext dataContext) {
        doCopy();
    }

    private boolean doCopy() {
        ArrayList<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
        SerializedComponentData data = new SerializedComponentData(serializeForCopy(myEditor, selectedComponents));
        SimpleTransferable transferable = new SimpleTransferable<>(data, SerializedComponentData.class, ourDataFlavor);
        try {
            CopyPasteManager.getInstance().setContents(transferable);
            return true;
        }
        catch (Exception e) {
            LOG.debug(e);
            return false;
        }
    }

    @Override
    public boolean isCutEnabled(@Nonnull DataContext dataContext) {
        return isCopyEnabled(dataContext) && FormEditingUtil.canDeleteSelection(myEditor);
    }

    @Override
    public boolean isCutVisible(@Nonnull DataContext dataContext) {
        return true;
    }

    @Override
    public void performCut(@Nonnull DataContext dataContext) {
        if (doCopy() && myEditor.ensureEditable()) {
            CommandProcessor.getInstance().executeCommand(myEditor.getProject(), new Runnable() {
                @Override
                public void run() {
                    FormEditingUtil.deleteSelection(myEditor);
                }
            }, UIDesignerBundle.message("command.cut"), null);
        }
    }

    @Override
    public boolean isPastePossible(@Nonnull DataContext dataContext) {
        return isPasteEnabled(dataContext);
    }

    @Override
    public boolean isPasteEnabled(@Nonnull DataContext dataContext) {
        return getSerializedComponents() != null && !myEditor.getInplaceEditingLayer().isEditing();
    }

    @Override
    public void performPaste(@Nonnull DataContext dataContext) {
        String serializedComponents = getSerializedComponents();
        if (serializedComponents == null) {
            return;
        }

        ArrayList<RadComponent> componentsToPaste = new ArrayList<>();
        IntList xs = IntLists.newArrayList();
        IntList ys = IntLists.newArrayList();
        loadComponentsToPaste(myEditor, serializedComponents, xs, ys, componentsToPaste);

        myEditor.getMainProcessor().startPasteProcessor(componentsToPaste, xs, ys);
    }

    @Nullable
    private static ArrayList<RadComponent> deserializeComponents(GuiEditor editor, String serializedComponents) {
        ArrayList<RadComponent> components = new ArrayList<>();
        IntList xs = IntLists.newArrayList();
        IntList ys = IntLists.newArrayList();
        if (!loadComponentsToPaste(editor, serializedComponents, xs, ys, components)) {
            return null;
        }
        return components;
    }

    private static boolean loadComponentsToPaste(
        final GuiEditor editor,
        String serializedComponents,
        IntList xs,
        IntList ys,
        ArrayList<RadComponent> componentsToPaste) {
        PsiPropertiesProvider provider = new PsiPropertiesProvider(editor.getModule());

        try {
            //noinspection HardCodedStringLiteral
            Document document = SAX_BUILDER.build(new StringReader(serializedComponents), "UTF-8");

            Element rootElement = document.getRootElement();
            if (!rootElement.getName().equals(ELEMENT_SERIALIZED)) {
                return false;
            }

            List children = rootElement.getChildren();
            for (Object aChildren : children) {
                Element e = (Element) aChildren;

                // we need to add component to a container in order to read them
                LwContainer container = new LwContainer(JPanel.class.getName());

                String parentLayout = e.getAttributeValue(ATTRIBUTE_PARENT_LAYOUT);
                if (parentLayout != null) {
                    container.setLayoutManager(parentLayout);
                }

                int x = Integer.parseInt(e.getAttributeValue(ATTRIBUTE_X));
                int y = Integer.parseInt(e.getAttributeValue(ATTRIBUTE_Y));

                xs.add(x);
                ys.add(y);

                Element componentElement = (Element) e.getChildren().get(0);
                LwComponent lwComponent = LwContainer.createComponentFromTag(componentElement);

                container.addComponent(lwComponent);

                lwComponent.read(componentElement, provider);

                // pasted components should have no bindings
                FormEditingUtil.iterate(lwComponent, new FormEditingUtil.ComponentVisitor<LwComponent>() {
                    @Override
                    public boolean visit(LwComponent c) {
                        if (c.getBinding() != null && FormEditingUtil.findComponentWithBinding(editor.getRootContainer(), c.getBinding()) != null) {
                            c.setBinding(null);
                        }
                        c.setId(FormEditingUtil.generateId(editor.getRootContainer()));
                        return true;
                    }
                });

                ClassLoader loader = LoaderFactory.getInstance(editor.getProject()).getLoader(editor.getFile());
                RadComponent radComponent = XmlReader.createComponent(editor, lwComponent, loader, editor.getStringDescriptorLocale());
                componentsToPaste.add(radComponent);
            }
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }

    @Nullable
    private static String getSerializedComponents() {
        try {
            Object transferData = CopyPasteManager.getInstance().getContents(ourDataFlavor);
            if (!(transferData instanceof SerializedComponentData)) {
                return null;
            }

            SerializedComponentData dataProxy = (SerializedComponentData) transferData;
            return dataProxy.getSerializedComponents();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static final DataFlavor ourDataFlavor = FileCopyPasteUtil.createJvmDataFlavor(SerializedComponentData.class);

    @Nullable
    public static List<RadComponent> copyComponents(GuiEditor editor, List<RadComponent> components) {
        return deserializeComponents(editor, serializeForCopy(editor, components));
    }

    private static String serializeForCopy(GuiEditor editor, List<RadComponent> components) {
        XmlWriter writer = new XmlWriter();

        writer.startElement(ELEMENT_SERIALIZED, Utils.FORM_NAMESPACE);

        for (RadComponent component : components) {
            Point shift;
            if (component.getParent() != null) {
                shift = SwingUtilities.convertPoint(component.getParent().getDelegee(), component.getX(), component.getY(),
                    editor.getRootContainer().getDelegee());
            }
            else {
                shift = new Point(0, 0);
            }

            component.getX();

            writer.startElement("item");
            writer.addAttribute(ATTRIBUTE_X, shift.x);
            writer.addAttribute(ATTRIBUTE_Y, shift.y);
            if (component.getParent() != null) {
                String parentLayout = component.getParent().getLayoutManager().getName();
                if (parentLayout != null) {
                    writer.addAttribute(ATTRIBUTE_PARENT_LAYOUT, parentLayout);
                }
            }
            component.write(writer);

            writer.endElement();
        }

        writer.endElement();

        return writer.getText();
    }


}
