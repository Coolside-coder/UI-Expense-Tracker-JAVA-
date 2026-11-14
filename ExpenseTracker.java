import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Arrays;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

public class ExpenseTracker {
    private JFrame frame;
    private JTextField descField;
    private JTextField amountField;
    private JComboBox<String> categoryBox;
    private JTable table;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter; // 1. Added a row sorter
    private JLabel totalLabel;
    private double totalAmount = 0.0;
    
    private final String FILE_NAME = "expenses.txt";
    private boolean isLoading = false;

    public ExpenseTracker() {
        frame = new JFrame("Basic Expense Tracker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(550, 480); // Made window slightly taller to fit the new filter bar
        frame.setLayout(new BorderLayout(10, 10));

        // Create a wrapper panel to hold both the input grid and the filter bar at the top
        JPanel headerWrapper = new JPanel();
        headerWrapper.setLayout(new BoxLayout(headerWrapper, BoxLayout.Y_AXIS));

        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        inputPanel.add(new JLabel("Description:"));
        descField = new JTextField();
        inputPanel.add(descField);

        inputPanel.add(new JLabel("Amount:")); 
        amountField = new JTextField();
        inputPanel.add(amountField);

        inputPanel.add(new JLabel("Category:"));
        
        String[] categories = {"Food", "Transport", "Entertainment", "Utilities", "Other"};
        categoryBox = new JComboBox<>(categories);
        inputPanel.add(categoryBox);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteButton = new JButton("Delete Selected");
        JButton addButton = new JButton("Add Expense");
        buttonPanel.add(deleteButton);
        buttonPanel.add(addButton);

        // 2. Create the Filter panel
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("View by Category:"));
        JComboBox<String> filterBox = new JComboBox<>(new String[]{"All Categories", "Food", "Transport", "Entertainment", "Utilities", "Other"});
        filterPanel.add(filterBox);

        // Assemble the top section
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        headerWrapper.add(topPanel);
        headerWrapper.add(filterPanel); // Add filter bar just above the table
        frame.add(headerWrapper, BorderLayout.NORTH);

        String[] columns = {"S.No.", "Description", "Category", "Amount"};
        
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0; 
            }
        };
        
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // 3. Attach the sorter to the table
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        
        JComboBox<String> tableCategoryBox = new JComboBox<>(categories);
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(tableCategoryBox));
        
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        
        // Listen for filter dropdown changes
        filterBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) filterBox.getSelectedItem();
                if ("All Categories".equals(selected)) {
                    sorter.setRowFilter(null); // Show all rows
                } else {
                    // Filter specifically on column index 2 (Category). 
                    // The regex ^ and $ ensure exact matches (e.g., "Food" won't accidentally match "Dog Food" if you add that later)
                    sorter.setRowFilter(RowFilter.regexFilter("^" + selected + "$", 2));
                }
                recalculateTotal(); // Update the total to show only the filtered amount
            }
        });

        tableModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (isLoading || e.getType() != TableModelEvent.UPDATE) return;
                
                int modelRow = e.getFirstRow(); // Listener always returns the model index
                int col = e.getColumn();
                
                if (col == 3 && modelRow >= 0) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = true;
                            try {
                                String newAmtStr = tableModel.getValueAt(modelRow, 3).toString();
                                double parsed = Double.parseDouble(newAmtStr.replace("Rs. ", "").replace(",", "").trim());
                                if (parsed < 0) throw new NumberFormatException();
                                tableModel.setValueAt(String.format("Rs. %.2f", parsed), modelRow, 3);
                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(frame, "Invalid amount entered. Resetting to 0.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
                                tableModel.setValueAt("Rs. 0.00", modelRow, 3);
                            }
                            recalculateTotal();
                            saveData();
                            isLoading = false;
                        }
                    });
                } 
                else if (modelRow >= 0 && col > 0) {
                    saveData();
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        totalLabel = new JLabel("Total (Visible): Rs. 0.00");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));
        bottomPanel.add(totalLabel);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addExpense();
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteExpense();
            }
        });
        
        loadData();

        frame.setLocationRelativeTo(null); 
        frame.setVisible(true);
    }
    
    private void recalculateTotal() {
        totalAmount = 0.0;
        // 4. Changed from tableModel.getRowCount() to table.getRowCount()
        // This ensures we only add up the amounts of rows currently visible on screen
        for (int i = 0; i < table.getRowCount(); i++) {
            try {
                String amtStr = table.getValueAt(i, 3).toString();
                double amt = Double.parseDouble(amtStr.replace("Rs. ", "").replace(",", "").trim());
                totalAmount += amt;
            } catch (NumberFormatException ex) {
            }
        }
        totalLabel.setText(String.format("Total (Visible): Rs. %.2f", totalAmount));
    }

    private void addExpense() {
        String desc = descField.getText().trim();
        String amountStr = amountField.getText().trim();
        String category = (String) categoryBox.getSelectedItem();

        if (desc.isEmpty() || amountStr.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please fill in all fields.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) throw new NumberFormatException();
            
            int sNo = tableModel.getRowCount() + 1;
            tableModel.addRow(new Object[]{sNo, desc, category, String.format("Rs. %.2f", amount)});
            
            recalculateTotal();
            saveData();
            
            descField.setText("");
            amountField.setText("");
            descField.requestFocus();
            
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid positive number for the amount.", "Invalid Amount", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteExpense() {
        int[] selectedViewRows = table.getSelectedRows();
        
        if (selectedViewRows.length == 0) {
            JOptionPane.showMessageDialog(frame, "Please select at least one row from the table to delete.", "No Row Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 5. Convert View indices to Model indices
        int[] modelRows = new int[selectedViewRows.length];
        for (int i = 0; i < selectedViewRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(selectedViewRows[i]);
        }
        
        // Sort the model indices so we can safely delete from bottom to top
        Arrays.sort(modelRows);
        
        for (int i = modelRows.length - 1; i >= 0; i--) {
            tableModel.removeRow(modelRows[i]);
        }
        
        isLoading = true; 
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(i + 1, i, 0);
        }
        isLoading = false;
        
        recalculateTotal();
        saveData();
    }

    private void saveData() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_NAME))) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String desc = tableModel.getValueAt(i, 1).toString();
                String cat = tableModel.getValueAt(i, 2).toString();
                String amt = tableModel.getValueAt(i, 3).toString();
                bw.write(desc + "|" + cat + "|" + amt);
                bw.newLine();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error saving data to file.", "File Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadData() {
        File file = new File(FILE_NAME);
        if (!file.exists()) return; 
        
        isLoading = true; 
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split("\\|");
                if (data.length == 3) {
                    int sNo = tableModel.getRowCount() + 1;
                    tableModel.addRow(new Object[]{sNo, data[0], data[1], data[2]});
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error loading data from file.", "File Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            recalculateTotal();
            isLoading = false; 
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ExpenseTracker();
            }
        });
    }
}  
