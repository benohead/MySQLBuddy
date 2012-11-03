package com.benohead.tools.mysql;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Vector;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ComboBoxUI;

import org.jdesktop.swingx.JXFindBar;
import org.jdesktop.swingx.JXList;
import org.jdesktop.swingx.JXStatusBar;
import org.jdesktop.swingx.action.AbstractActionExt;
import org.jdesktop.swingx.search.AbstractSearchable;
import org.jdesktop.swingx.search.SearchFactory;
import org.jdesktop.swingx.search.Searchable;

public class MySQLBuddyApplication implements ChangeListener {

    private static JFrame frmMySQLbuddy;

    public static void handleException(Exception e) {
        if (e instanceof SQLException) {
            refreshRateTextField.setText("0");
            JOptionPane.showMessageDialog(frmMySQLbuddy, e.getLocalizedMessage(), "Connection error", JOptionPane.ERROR_MESSAGE);
        }
        else if (connection == null) {
            // No connection available
            JOptionPane.showMessageDialog(frmMySQLbuddy, "Connection to database missing. Trying to connect.", "Connection missing", JOptionPane.ERROR_MESSAGE);
            application.connect();
        }
        else if (e instanceof ClassNotFoundException) {
            refreshRateTextField.setText("0");
            JOptionPane.showMessageDialog(frmMySQLbuddy, e.getLocalizedMessage(), "Class not found", JOptionPane.ERROR_MESSAGE);
        }
        else {
            refreshRateTextField.setText("0");
            e.printStackTrace();
            JOptionPane.showMessageDialog(frmMySQLbuddy, e.getLocalizedMessage(), "General error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    application = new MySQLBuddyApplication();
                    MySQLBuddyApplication.frmMySQLbuddy.setVisible(true);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void readServerConfiguration() throws IOException {
        tabs.clear();
        Properties p = new Properties();
        p.load(new FileInputStream("servers.ini"));
        for (Entry<Object, Object> entry : p.entrySet()) {
            String keyName = entry.getKey().toString();
            String keyValue = entry.getValue().toString();
            servers.put(keyName, keyValue);
        }
    }

    static void writeServersToFile() throws FileNotFoundException, IOException {
        Properties p = new Properties();

        for (Enumeration<String> keys = MySQLBuddyApplication.servers.keys(); keys.hasMoreElements();) {
            String key = keys.nextElement();
            String value = MySQLBuddyApplication.servers.get(key);
            p.put(key, value);
        }
        p.store(new FileOutputStream("servers.ini"), "");
    }

    private JTextField userField;
    private JPasswordField passwordField;
    protected static Connection connection;
    private JLabel statusLabel;
    private static Hashtable<String, TabDefinition> tabs = new Hashtable<String, TabDefinition>();
    private static Hashtable<String, ArrayList<ContextMenuItem>> contextMenuItems = new Hashtable<String, ArrayList<ContextMenuItem>>();
    static Hashtable<String, String> servers = new Hashtable<String, String>();
    private JTabbedPane tabbedPane;
    private JComboBox serverBox;

    private JXFindBar searchPanel;

    private JComboBox databaseBox;

    protected static MySQLBuddyApplication application;

    private static JTextField refreshRateTextField;

    protected int refreshRate = 0;

    private javax.swing.Timer refreshTimer;

    /**
     * Create the application.
     */
    public MySQLBuddyApplication() {
        initialize();
    }

    private void connect() {
        try {
            statusLabel.setIcon(new ImageIcon(MySQLBuddyApplication.class.getResource("/disconnected.gif")));
            connection = null;

            Class.forName("com.mysql.jdbc.Driver").newInstance();

            String username = userField.getText();
            String password = new String(passwordField.getPassword());
            String serverName = serverBox.getSelectedItem().toString();
            if (servers.containsKey(serverName)) {
                serverName = servers.get(serverName);
            }
            if (!serverName.contains(":")) {
                serverName += ":3306";
            }
            String url = "jdbc:mysql://" + serverName;
            System.out.println(url);
            connection = DriverManager.getConnection(url, username, password);
            databaseConnected();
            setDatabases();
            if (refreshRate > 0) {
                startRefreshTimer();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            handleException(e);
        }
    }

    private void databaseConnected() throws InterruptedException, InvocationTargetException {
        statusLabel.setIcon(new ImageIcon(MySQLBuddyApplication.class.getResource("/connected.gif")));
        refresh();
    }

    private void exportAllTabs() {
        try {
            final JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnVal = fc.showSaveDialog(frmMySQLbuddy);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File selectedDirectory = fc.getSelectedFile();
                int tabCount = tabbedPane.getTabCount();
                Component selectedComponent = tabbedPane.getSelectedComponent();

                for (int i = 0; i < tabCount; i++) {
                    Component component = tabbedPane.getComponentAt(i);
                    tabbedPane.setSelectedComponent(component);
                    SqlTable sqlTable = ((SqlTable) ((JScrollPane) component).getViewport().getView());
                    String tabName = tabbedPane.getTitleAt(i);
                    sqlTable.refresh(true);
                    sqlTable.export(new File(selectedDirectory, tabName + ".xls"));
                }

                tabbedPane.setSelectedComponent(selectedComponent);
            }
        }
        catch (Exception e) {
            handleException(e);
        }
    }

    protected void exportCurrentTab() {
        try {
            SqlTable sqlTable = ((SqlTable) ((JScrollPane) tabbedPane.getSelectedComponent()).getViewport().getView());
            final JFileChooser fc = new JFileChooser();
            int returnVal = fc.showSaveDialog(frmMySQLbuddy);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                sqlTable.export(fc.getSelectedFile());
            }
        }
        catch (Exception e) {
            handleException(e);
        }
    }

    private void exportTabs() {
        int result = JOptionPane.showConfirmDialog(frmMySQLbuddy, "Do you want to export all tabs ?", "Export tabs", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            exportAllTabs();
        }
        else {
            exportCurrentTab();
        }
    }

    public Vector<String> getTabs() {
        Vector<String> tabs = new Vector<String>();
        int tabCount = tabbedPane.getTabCount();
        for (int i = 0; i < tabCount; i++) {
            String tabName = tabbedPane.getTitleAt(i);
            tabs.add(tabName);
        }
        return tabs;
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initialize() {
        frmMySQLbuddy = new JFrame();
        frmMySQLbuddy.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg0) {
            }
        });
        frmMySQLbuddy.getContentPane().setBackground(new Color(255, 250, 240));
        frmMySQLbuddy.setBackground(new Color(255, 250, 240));
        frmMySQLbuddy.setFont(new Font("Verdana", Font.PLAIN, 12));
        frmMySQLbuddy.setTitle("MySQLBuddy");
        frmMySQLbuddy.setIconImage(Toolkit.getDefaultToolkit().getImage(MySQLBuddyApplication.class.getResource("/icon.png")));
        frmMySQLbuddy.setBounds(100, 100, 1100, 768);
        frmMySQLbuddy.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(new Color(255, 250, 240));
        frmMySQLbuddy.getContentPane().add(mainPanel, BorderLayout.CENTER);
        mainPanel.setLayout(new BorderLayout(0, 0));

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        tabbedPane.removeAll();

        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(255, 250, 240));
        FlowLayout fl_controlPanel = (FlowLayout) controlPanel.getLayout();
        fl_controlPanel.setAlignment(FlowLayout.LEFT);
        frmMySQLbuddy.getContentPane().add(controlPanel, BorderLayout.NORTH);

        JLabel lblServer = new JLabel("Server:");
        lblServer.setBackground(new Color(255, 250, 240));
        lblServer.setFont(new Font("Verdana", Font.PLAIN, 12));
        controlPanel.add(lblServer);

        serverBox = new JComboBox() {

            private static final long serialVersionUID = 1L;

            @Override
            public void setUI(ComboBoxUI ui) {
                super.setUI(ui);
                ((JTextField) getEditor().getEditorComponent()).setColumns(10);
            }
        };
        serverBox.setBackground(new Color(255, 250, 240));
        serverBox.setFont(new Font("Verdana", Font.PLAIN, 12));
        serverBox.setEditable(true);
        lblServer.setLabelFor(serverBox);
        controlPanel.add(serverBox);

        JLabel lblUser = new JLabel("User:");
        lblUser.setBackground(new Color(255, 250, 240));
        lblUser.setFont(new Font("Verdana", Font.PLAIN, 12));
        controlPanel.add(lblUser);

        userField = new JTextField();
        userField.setText("root");
        userField.setBackground(new Color(255, 255, 255));
        userField.setFont(new Font("Verdana", Font.PLAIN, 12));
        lblUser.setLabelFor(userField);
        controlPanel.add(userField);
        userField.setColumns(10);

        JLabel lblPassword = new JLabel("Password:");
        lblPassword.setBackground(new Color(255, 250, 240));
        lblPassword.setFont(new Font("Verdana", Font.PLAIN, 12));
        controlPanel.add(lblPassword);

        passwordField = new JPasswordField();
        passwordField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                connect();
            }
        });
        passwordField.setBackground(new Color(255, 255, 255));
        passwordField.setFont(new Font("Verdana", Font.PLAIN, 12));
        lblPassword.setLabelFor(passwordField);
        passwordField.setColumns(10);
        controlPanel.add(passwordField);

        JButton connectButton = new JButton("Connect");
        controlPanel.add(connectButton);

        statusLabel = new JLabel("");
        statusLabel.setIcon(new ImageIcon(MySQLBuddyApplication.class.getResource("/disconnected.gif")));
        JXStatusBar statusBar = new JXStatusBar();
        frmMySQLbuddy.getContentPane().add(statusBar, BorderLayout.SOUTH);
        statusBar.add(statusLabel);

        JLabel lblDatabase = new JLabel("Database:");
        lblDatabase.setFont(new Font("Verdana", Font.PLAIN, 12));
        controlPanel.add(lblDatabase);

        databaseBox = new JComboBox() {

            private static final long serialVersionUID = 1L;

            @Override
            public void setUI(ComboBoxUI ui) {
                super.setUI(ui);
                ((JTextField) getEditor().getEditorComponent()).setColumns(10);
            }
        };
        databaseBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if ((connection != null) && (databaseBox.getSelectedItem() != null)) {
                    try {
                        connection.createStatement().execute("use " + databaseBox.getSelectedItem().toString());
                        refresh();
                    }
                    catch (Exception e) {
                        handleException(e);
                    }
                }
            }
        });
        lblDatabase.setLabelFor(databaseBox);
        databaseBox.setBackground(new Color(255, 250, 240));
        controlPanel.add(databaseBox);

        refreshRateTextField = new JTextField("" + refreshRate);
        controlPanel.add(refreshRateTextField);
        refreshRateTextField.setColumns(2);

        refreshRateTextField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                updateRefreshRate();
            }

            public void insertUpdate(DocumentEvent e) {
                updateRefreshRate();
            }

            public void removeUpdate(DocumentEvent e) {
                updateRefreshRate();
            }

            public void updateRefreshRate() {
                try {
                    refreshRate = Integer.parseInt(refreshRateTextField.getText());
                    if (refreshTimer != null) {
                        refreshTimer.stop();
                        refreshTimer = null;
                    }
                }
                catch (Exception e) {
                    return;
                }
                if (refreshRate > 0) {
                    startRefreshTimer();
                }
            }
        });

        JButton refreshButton = new JButton("");
        refreshButton.setIcon(new ImageIcon(MySQLBuddyApplication.class.getResource("/refresh.png")));
        controlPanel.add(refreshButton);

        JButton selectTabsButton = new JButton("");
        selectTabsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                Vector<String> listOfTabs = new Vector<String>(tabs.keySet());
                JXList list = new JXList(listOfTabs);
                Vector<String> existingTabs = new Vector<String>();
                int index = 0;
                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                    existingTabs.add(tabbedPane.getTitleAt(i));
                }
                int[] indices = new int[existingTabs.size()];
                for (int i = 0; i < listOfTabs.size(); i++) {
                    String tabName = listOfTabs.get(i);
                    if (existingTabs.contains(tabName)) {
                        indices[index++] = i;
                    }
                }
                list.setSelectedIndices(indices);
                int result = JOptionPane.showConfirmDialog(null, list, "Select tabs", JOptionPane.OK_CANCEL_OPTION);

                if (result == JOptionPane.OK_OPTION) {
                    Object[] selectedTabs = list.getSelectedValues();
                    initTabs(selectedTabs);
                }
            }
        });
        selectTabsButton.setIcon(new ImageIcon(MySQLBuddyApplication.class.getResource("/tabs.png")));
        controlPanel.add(selectTabsButton);

        JButton configButton = new JButton("");
        configButton.setIcon(new ImageIcon(MySQLBuddyApplication.class.getResource("/config.png")));
        configButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                ConfigDialog dialog = new ConfigDialog();
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.setVisible(true);
            }
        });
        controlPanel.add(configButton);

        JButton exportButton = new JButton("");
        exportButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                exportTabs();
            }
        });
        exportButton.setIcon(new ImageIcon(MySQLBuddyApplication.class.getResource("/export.png")));
        controlPanel.add(exportButton);

        connectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                connect();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    refresh();
                }
                catch (Exception e) {
                    handleException(e);
                }
            }
        });

        searchPanel = SearchFactory.getInstance().createFindBar();
        searchPanel.setBackground(new Color(255, 250, 240));
        mainPanel.add(searchPanel, BorderLayout.SOUTH);

        try {
            readServerConfiguration();
            readTabConfiguration();
            readContextMenuConfiguration();
        }
        catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frmMySQLbuddy, e.getLocalizedMessage(), "Configuration file error", JOptionPane.ERROR_MESSAGE);
        }

        initTabs(null);

        serverBox.removeAllItems();
        for (String server : servers.keySet()) {
            serverBox.addItem(server);
        }
    }

    private void initTabs(Object[] selectedTabs) {
        Vector<String> v = new Vector<String>();
        if (selectedTabs != null) {
            for (Object selectedTab : selectedTabs) {
                v.add(selectedTab.toString());
            }
        }
        else {
            v.addAll(tabs.keySet());
        }
        tabbedPane.removeChangeListener(this);
        tabbedPane.removeAll();
        for (Entry<String, TabDefinition> entry : tabs.entrySet()) {
            if (v.contains(entry.getKey())) {
                SqlTable sqlTable = new SqlTable(entry.getValue());
                sqlTable.addMouseListener(new SqlTableClickListener(MySQLBuddyApplication.frmMySQLbuddy, contextMenuItems));
                installCustomSearch(sqlTable);
                JScrollPane scrollPane = new JScrollPane(sqlTable);
                tabbedPane.add(entry.getKey(), scrollPane);
            }
        }
        tabbedPane.addChangeListener(this);
    }

    private void installCustomFindAction(Action find, JComponent target) {
        // install the custom action
        target.getActionMap().put("find", find);
        // force incremental search mode
        target.putClientProperty(AbstractSearchable.MATCH_HIGHLIGHTER, Boolean.TRUE);
    }

    private void installCustomSearch(SqlTable table) {
        // create custom find action
        @SuppressWarnings("serial")
        Action find = new AbstractActionExt() {

            public void actionPerformed(ActionEvent e) {
                updateSearchPanel(e != null ? e.getSource() : null);
            }

        };
        // install custom find actions on all collection components of the
        // search demo
        installCustomFindAction(find, table);
    }

    private void readContextMenuConfiguration() throws IOException {
        contextMenuItems.clear();
        Properties p = new Properties();
        p.load(new FileInputStream("contextmenu.ini"));
        for (Entry<Object, Object> entry : p.entrySet()) {
            String keyName = entry.getKey().toString();
            String keyValue = entry.getValue().toString();
            if (keyName.startsWith("columnname_")) {
                String itemId = keyName.substring(11);
                String columnName = keyValue;
                String menuItem = p.get("menuitem_" + itemId).toString();
                String action = p.get("action_" + itemId).toString();
                String sql = p.get("sql_" + itemId).toString();
                ArrayList<ContextMenuItem> contextMenu = contextMenuItems.get(columnName.toLowerCase());
                if (contextMenu == null) {
                    contextMenu = new ArrayList<ContextMenuItem>();
                    contextMenuItems.put(columnName.toLowerCase(), contextMenu);
                }
                ContextMenuItem contextMenuItem = new ContextMenuItem();
                contextMenuItem.columnName = columnName;
                contextMenuItem.menuItem = menuItem;
                contextMenuItem.action = action;
                contextMenuItem.sql = sql;
                contextMenu.add(contextMenuItem);
            }
        }
    }

    private void readTabConfiguration() throws IOException {
        tabs.clear();
        Properties p = new Properties();
        p.load(new FileInputStream("tabs.ini"));
        for (Entry<Object, Object> entry : p.entrySet()) {
            String keyName = entry.getKey().toString();
            String keyValue = entry.getValue().toString();
            if (keyName.startsWith("tabname_")) {
                String tabId = keyName.substring(8);
                TabDefinition tabSql = new TabDefinition();
                tabSql.setSql(p.get("tabsql_" + tabId).toString());
                ColorDefinition colorDefinition;
                int colorIndex = 1;
                do {
                    colorDefinition = null;
                    if (p.containsKey("tabcolor_" + tabId + "_color" + colorIndex)) {
                        String colorString = p.get("tabcolor_" + tabId + "_color" + colorIndex).toString();
                        String column = p.get("tabcolumn_" + tabId + "_color" + colorIndex).toString();
                        String rule = p.get("tabrule_" + tabId + "_color" + colorIndex).toString();
                        Color color;
                        try {
                            color = Colors.parseRgb(colorString);
                            if (color == null) {
                                color = Colors.parseHsb(colorString);
                            }
                            if (color == null) {
                                Field field = Class.forName("com.benohead.tools.mysql.Colors").getField(colorString);
                                color = (Color) field.get(null);
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            color = null; // Not defined
                        }
                        if (color != null) {
                            colorDefinition = new ColorDefinition();
                            colorDefinition.setColor(color);
                            colorDefinition.setColumn(column);
                            colorDefinition.setRule(rule);
                            tabSql.addColor(colorDefinition);
                        }
                    }
                    colorIndex++;
                }
                while (colorDefinition != null);
                tabs.put(keyValue, tabSql);
            }
        }
    }

    private void refresh() throws InterruptedException, InvocationTargetException {
        SqlTable sqlTable = (SqlTable) ((JScrollPane) tabbedPane.getSelectedComponent()).getViewport().getView();
        sqlTable.refresh(false);
        updateSearchPanel(sqlTable);
    }

    private void setDatabases() throws SQLException {
        databaseBox.removeAllItems();
        ResultSet result = connection.createStatement().executeQuery("SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA");
        while (result.next()) {
            databaseBox.addItem(result.getString(1));
        }
    }

    private void startRefreshTimer() {
        ActionListener refreshActionListener = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                try {
                    refresh();
                }
                catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                catch (InvocationTargetException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        refreshTimer = new javax.swing.Timer(refreshRate * 1000, refreshActionListener);
        refreshTimer.start();
    }

    public void stateChanged(ChangeEvent event) {
        try {
            refresh();
        }
        catch (Exception e) {
            handleException(e);
        }
    }

    protected void updateSearchPanel(Object searchableProvider) {
        final Searchable s = ((SqlTable) searchableProvider).getSearchable();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                searchPanel.setSearchable(s);
                KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(searchPanel);
            }
        });
    }

}
