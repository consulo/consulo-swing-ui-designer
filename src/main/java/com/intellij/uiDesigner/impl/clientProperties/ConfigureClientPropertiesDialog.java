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
package com.intellij.uiDesigner.impl.clientProperties;

import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.ActionToolbarPosition;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awt.ToolbarDecorator;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.uiDesigner.impl.localize.UIDesignerLocalize;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.*;

/**
 * @author yole
 */
public class ConfigureClientPropertiesDialog extends DialogWrapper {
  private JTree myClassTree;
  private JTable myPropertiesTable;
  private Class mySelectedClass;
  private List<ClientPropertiesManager.ClientProperty> mySelectedProperties = Collections.emptyList();
  private final MyTableModel myTableModel = new MyTableModel();
  private final Project myProject;
  private final ClientPropertiesManager myManager;
  private Splitter mySplitter;
  final private PropertiesComponent myPropertiesComponent = PropertiesComponent.getInstance();
  final private static String SPLITTER_PROPORTION_PROPERTY = "ConfigureClientPropertiesDialog.splitterProportion";

  public ConfigureClientPropertiesDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(UIDesignerLocalize.clientPropertiesTitle());
    myManager = ClientPropertiesManager.getInstance(project).clone();
    init();
  }

  public void save() {
    ClientPropertiesManager.getInstance(myProject).saveFrom(myManager);
  }

  @Override
  public void dispose() {
    myPropertiesComponent.setValue(SPLITTER_PROPORTION_PROPERTY, String.valueOf(mySplitter.getProportion()));
    super.dispose();
  }

  private void updateSelectedProperties() {
    mySelectedProperties = myManager.getConfiguredProperties(mySelectedClass);
    myTableModel.fireTableDataChanged();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myClassTree = new Tree();
    myClassTree.setRootVisible(false);
    myClassTree.getSelectionModel().addTreeSelectionListener(e -> {
      TreePath leadSelectionPath = e.getNewLeadSelectionPath();
      if (leadSelectionPath == null) return;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelectionPath.getLastPathComponent();
      mySelectedClass = (Class)node.getUserObject();
      updateSelectedProperties();
    });

    myClassTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        if (node.getUserObject() instanceof Class) {
          Class cls = (Class)node.getUserObject();
          if (cls != null) {
            append(cls.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      }
    });
    fillClassTree();

    myPropertiesTable = new JBTable();
    myPropertiesTable.setModel(myTableModel);


    mySplitter = new Splitter(false, Float.valueOf(myPropertiesComponent.getValue(SPLITTER_PROPORTION_PROPERTY, "0.5f")));
    mySplitter.setFirstComponent(
      ToolbarDecorator.createDecorator(myClassTree)
        .setAddAction(button -> {
          ClassNameInputDialog dlg = new ClassNameInputDialog(myProject, mySplitter);
          dlg.show();
          if (dlg.getExitCode() == OK_EXIT_CODE) {
            String className = dlg.getClassName();
            if (className.length() == 0) return;
            Class aClass;
            try {
              aClass = Class.forName(className);
            }
            catch (ClassNotFoundException ex) {
              Messages.showErrorDialog(
                mySplitter,
                UIDesignerLocalize.clientPropertiesClassNotFound(className).get(),
                UIDesignerLocalize.clientPropertiesTitle().get()
              );
              return;
            }
            if (!JComponent.class.isAssignableFrom(aClass)) {
              Messages.showErrorDialog(
                mySplitter,
                UIDesignerLocalize.clientPropertiesClassNotComponent(className).get(),
                UIDesignerLocalize.clientPropertiesTitle().get()
              );
              return;
            }
            myManager.addClientPropertyClass(className);
            fillClassTree();
          }
        })
        .setRemoveAction(button -> {
          if (mySelectedClass != null) {
            myManager.removeClientPropertyClass(mySelectedClass);
            fillClassTree();
          }
        })
        .setToolbarPosition(Platform.current().os().isMac() ? ActionToolbarPosition.BOTTOM : ActionToolbarPosition.RIGHT)
        .createPanel());

    mySplitter.setSecondComponent(
      ToolbarDecorator.createDecorator(myPropertiesTable).disableUpDownActions()
        .setAddAction(button -> {
          AddClientPropertyDialog dlg = new AddClientPropertyDialog(myProject);
          dlg.show();
          if (dlg.getExitCode() == OK_EXIT_CODE) {
            List<ClientPropertiesManager.ClientProperty> props = myManager.getClientProperties(mySelectedClass);
            for (ClientPropertiesManager.ClientProperty prop : props) {
              if (prop.getName().equalsIgnoreCase(dlg.getEnteredProperty().getName())) {
                Messages.showErrorDialog(
                  mySplitter,
                  UIDesignerLocalize.clientPropertiesAlreadyDefined(prop.getName()).get(),
                  UIDesignerLocalize.clientPropertiesTitle().get()
                );
                return;
              }
            }
            myManager.addConfiguredProperty(mySelectedClass, dlg.getEnteredProperty());
            updateSelectedProperties();
          }
        })
        .setRemoveAction(button -> {
          int row = myPropertiesTable.getSelectedRow();
          if (row >= 0 && row < mySelectedProperties.size()) {
            myManager.removeConfiguredProperty(mySelectedClass, mySelectedProperties.get(row).getName());
            updateSelectedProperties();
            if (mySelectedProperties.size() > 0) {
              if (row >= mySelectedProperties.size()) row--;
              myPropertiesTable.getSelectionModel().setSelectionInterval(row, row);
            }
          }
        })
        .createPanel()
    );

    return mySplitter;
  }

  private void fillClassTree() {
    List<Class> configuredClasses = myManager.getConfiguredClasses(myProject);
    Collections.sort(configuredClasses, new Comparator<>() {
      @Override
      public int compare(Class o1, Class o2) {
        return getInheritanceLevel(o1) - getInheritanceLevel(o2);
      }

      private int getInheritanceLevel(Class aClass) {
        int level = 0;
        while (aClass.getSuperclass() != null) {
          level++;
          aClass = aClass.getSuperclass();
        }
        return level;
      }
    });

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    Map<Class, DefaultMutableTreeNode> classToNodeMap = new HashMap<>();
    for (Class cls : configuredClasses) {
      DefaultMutableTreeNode parentNode = root;
      Class superClass = cls.getSuperclass();
      while (superClass != null) {
        if (classToNodeMap.containsKey(superClass)) {
          parentNode = classToNodeMap.get(superClass);
          break;
        }
        superClass = superClass.getSuperclass();
      }
      DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(cls);
      classToNodeMap.put(cls, childNode);
      parentNode.add(childNode);
    }
    myClassTree.setModel(treeModel);
    myClassTree.expandRow(0);
    myClassTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myClassTree.getSelectionModel().setSelectionPath(new TreePath(new Object[]{root, root.getFirstChild()}));
  }

  @Override
  protected String getDimensionServiceKey() {
    return "ConfigureClientPropertiesDialog";
  }

  private class MyTableModel extends AbstractTableModel {
    @Override
    public int getRowCount() {
      return mySelectedProperties.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return switch (columnIndex) {
            case 0 -> mySelectedProperties.get(rowIndex).getName();
            default -> mySelectedProperties.get(rowIndex).getValueClass();
        };
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> UIDesignerLocalize.clientPropertiesName().get();
            default -> UIDesignerLocalize.clientPropertiesClass().get();
        };
    }
  }
}
