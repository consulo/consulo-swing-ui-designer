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
package com.intellij.uiDesigner.impl.actions;

import com.intellij.uiDesigner.impl.FormEditingUtil;
import com.intellij.uiDesigner.impl.designSurface.GuiEditor;
import com.intellij.uiDesigner.impl.radComponents.RadAtomicComponent;
import com.intellij.uiDesigner.impl.radComponents.RadComponent;
import com.intellij.uiDesigner.impl.radComponents.RadContainer;
import consulo.application.dumb.DumbAware;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
abstract class AbstractMoveSelectionAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
    private static final Logger LOG = Logger.getInstance(AbstractMoveSelectionAction.class);

    private final GuiEditor myEditor;
    private final boolean myExtend;
    private final boolean myMoveToLast;

    public AbstractMoveSelectionAction(@Nonnull final GuiEditor editor, boolean extend, final boolean moveToLast) {
        myEditor = editor;
        myExtend = extend;
        myMoveToLast = moveToLast;
    }

    @Override
    @RequiredUIAccess
    public final void actionPerformed(final AnActionEvent e) {
        List<RadComponent> selectedComponents = FormEditingUtil.getSelectedComponents(myEditor);
        final JComponent rootContainerDelegee = myEditor.getRootContainer().getDelegee();
        if (selectedComponents.size() == 0) {
            moveToFirstComponent(rootContainerDelegee);
            return;
        }
        RadComponent selectedComponent = myEditor.getSelectionLead();
        if (selectedComponent == null || !selectedComponents.contains(selectedComponent)) {
            selectedComponent = selectedComponents.get(0);
        }

        if (moveSelectionByGrid(selectedComponent) || myMoveToLast) {
            return;
        }

        // 1. We need to get coordinates of all editor's component in the same
        // coordinate system. For example, in the RadRootContainer rootContainerDelegee's coordinate system.

        final List<RadComponent> components = new ArrayList<>();
        final List<Point> points = new ArrayList<>();
        final RadComponent selectedComponent1 = selectedComponent;
        FormEditingUtil.iterate(
            myEditor.getRootContainer(),
            new FormEditingUtil.ComponentVisitor<RadComponent>() {
                @Override
                public boolean visit(final RadComponent component) {
                    if (component instanceof RadAtomicComponent) {
                        if (selectedComponent1.equals(component)) {
                            return true;
                        }
                        if (!FormEditingUtil.isComponentSwitchedInView(component)) {
                            return true;
                        }
                        components.add(component);
                        final JComponent _delegee = component.getDelegee();
                        final Point p = SwingUtilities.convertPoint(
                            _delegee,
                            new Point(0, 0),
                            rootContainerDelegee
                        );
                        p.x += _delegee.getWidth() / 2;
                        p.y += _delegee.getHeight() / 2;
                        points.add(p);
                    }
                    return true;
                }
            }
        );
        if (components.size() == 0) {
            return;
        }

        // 2.
        final Point source = SwingUtilities.convertPoint(
            selectedComponent.getDelegee(),
            new Point(0, 0),
            rootContainerDelegee
        );
        source.x += selectedComponent.getDelegee().getWidth() / 2;
        source.y += selectedComponent.getDelegee().getHeight() / 2;
        int min = Integer.MAX_VALUE;
        int nextSelectedIndex = -1;
        for (int i = points.size() - 1; i >= 0; i--) {
            final int distance = calcDistance(source, points.get(i));
            if (distance < min) {
                min = distance;
                nextSelectedIndex = i;
            }
        }
        if (min == Integer.MAX_VALUE) {
            return;
        }

        LOG.assertTrue(nextSelectedIndex != -1);
        final RadComponent component = components.get(nextSelectedIndex);
        selectOrExtend(component);
    }

    private void selectOrExtend(final RadComponent component) {
        if (myExtend) {
            FormEditingUtil.selectComponent(myEditor, component);
        }
        else {
            FormEditingUtil.selectSingleComponent(myEditor, component);
        }
    }

    private void moveToFirstComponent(final JComponent rootContainerDelegee) {
        final int[] minX = new int[]{Integer.MAX_VALUE};
        final int[] minY = new int[]{Integer.MAX_VALUE};
        final SimpleReference<RadComponent> componentToBeSelected = new SimpleReference<>();
        FormEditingUtil.iterate(
            myEditor.getRootContainer(),
            new FormEditingUtil.ComponentVisitor<RadComponent>() {
                @Override
                public boolean visit(final RadComponent component) {
                    if (component instanceof RadAtomicComponent) {
                        final JComponent _delegee = component.getDelegee();
                        final Point p = SwingUtilities.convertPoint(
                            _delegee,
                            new Point(0, 0),
                            rootContainerDelegee
                        );
                        if (minX[0] > p.x || minY[0] > p.y) {
                            minX[0] = p.x;
                            minY[0] = p.y;
                            componentToBeSelected.set(component);
                        }
                    }
                    return true;
                }
            }
        );
        if (!componentToBeSelected.isNull()) {
            FormEditingUtil.selectComponent(myEditor, componentToBeSelected.get());
        }
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(!myEditor.getMainProcessor().isProcessorActive());
    }

    private boolean moveSelectionByGrid(final RadComponent selectedComponent) {
        final RadContainer parent = selectedComponent.getParent();
        if (parent == null || !parent.getLayoutManager().isGrid()) {
            return false;
        }

        int row = selectedComponent.getConstraints().getRow();
        int column = selectedComponent.getConstraints().getColumn();

        RadComponent lastComponent = null;
        do {
            row += getRowMoveDelta();
            column += getColumnMoveDelta();
            if (row < 0 || row >= parent.getGridRowCount() || column < 0 || column >= parent.getGridColumnCount()) {
                if (myMoveToLast) {
                    break;
                }
                return false;
            }

            final RadComponent component = parent.getComponentAtGrid(row, column);
            if (component != null && component != selectedComponent) {
                if (myMoveToLast) {
                    if (myExtend) {
                        FormEditingUtil.selectComponent(myEditor, component);
                    }
                    lastComponent = component;
                }
                else {
                    selectOrExtend(component);
                    return true;
                }
            }
        }
        while (true);

        if (lastComponent != null) {
            selectOrExtend(lastComponent);
            return true;
        }
        return false;
    }

    protected abstract int calcDistance(Point source, Point point);

    protected int getColumnMoveDelta() {
        return 0;
    }

    protected int getRowMoveDelta() {
        return 0;
    }
}
