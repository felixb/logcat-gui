package com.drsuperchamp.android.tools.logcat.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import com.drsuperchamp.android.tools.logcat.core.AdbWrapper;
import com.drsuperchamp.android.tools.logcat.core.AdbWrapper.DeviceConnectionListener;
import com.drsuperchamp.android.tools.logcat.core.LogCatWrapper;

public class MainFrame extends JFrame implements DeviceConnectionListener {
	private static final String DEFAULT_TABLE_NAME = "Log";
	private final String ADB_BIN_PATH;
	private AdbWrapper mAdb = null;
	private String mConnectedDevSerialNum = null;
	private LogCatWrapper mLogcat;
	private JTabbedPane mTabbedPane = null;
	private JButton mBtnConnect = null;
	private List<LogTable> mTables = new ArrayList<LogTable>();

	public MainFrame(String adb_bin_path) {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 640, 480);

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmLoad = new JMenuItem("Load");
		mnFile.add(mntmLoad);

		JToolBar toolBar = new JToolBar();
		getContentPane().add(toolBar, BorderLayout.NORTH);

		mBtnConnect = new JButton("No device");
		mBtnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				mBtnConnect.setEnabled(false);

				removeAllTables(mTabbedPane);

				LogTable defaultTable = findTable(DEFAULT_TABLE_NAME);
				defaultTable.clear();
				addTable(mTabbedPane, defaultTable);

				mLogcat = new LogCatWrapper(mConnectedDevSerialNum, 10000);
				mLogcat.setDefaultFilterOutput(defaultTable);

				Iterator<LogTable> it = mTables.iterator();
				while (it.hasNext()) {
					LogTable table = it.next();
					if (table.getName().equals(DEFAULT_TABLE_NAME)) {
						continue;
					}
					table.clear();
					addTable(mTabbedPane, table);
					mLogcat.addFilter(table.getName(), table.filterTag(),
							table.filterPid(), table.filterLogLevel(), null,
							table);
				}

				// added filters
				new Thread(new Runnable() {
					@Override
					public void run() {
						mAdb.executeShellCommand(mConnectedDevSerialNum,
								"logcat -v long",
								mLogcat.getShellOutputReceiver());
					}
				}).start();
			}
		});
		mBtnConnect.setEnabled(false);
		toolBar.add(mBtnConnect);

		mTabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(mTabbedPane, BorderLayout.CENTER);

		LogTable defaultTable = new LogTable(DEFAULT_TABLE_NAME);
		mTables.add(defaultTable);

		ADB_BIN_PATH = adb_bin_path;
		mAdb = AdbWrapper.getInstance();
		mAdb.connect(ADB_BIN_PATH, this);
	}

	private LogTable findTable(String name) {
		Iterator<LogTable> it = mTables.iterator();
		while (it.hasNext()) {
			LogTable table = it.next();
			if (table.getName().equals(name)) {
				return table;
			}
		}
		return null;
	}

	private void addTable(JTabbedPane tabbedPane, LogTable table) {
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(table);
		tabbedPane.addTab(table.getName(), null, scrollPane, table.getName());
	}

	private void removeTable(JTabbedPane tabbedPane, LogTable table) {
		Component target = table.getParent();
		int loop_len = tabbedPane.getTabCount();
		for (int n = 0; n < loop_len; n++) {
			Component component = tabbedPane.getTabComponentAt(n);
			if (component == target) {
				tabbedPane.removeTabAt(n);
				return;
			}
		}
	}

	private void removeAllTables(JTabbedPane tabbedPane) {
		while (tabbedPane.getTabCount() > 0) {
			tabbedPane.removeTabAt(0);
		}
	}

	@Override
	public void deviceConnected(String devSerialNumber) {
		mConnectedDevSerialNum = new String(devSerialNumber);
		runInEventThread(new Runnable() {
			@Override
			public void run() {
				mBtnConnect.setText("Connect: " + mConnectedDevSerialNum);
				mBtnConnect.setEnabled(true);
			}
		}, true);
	}

	private void runInEventThread(Runnable r, boolean isSynchronous) {
		if (isSynchronous) {
			try {
				SwingUtilities.invokeAndWait(r);
			} catch (InterruptedException e) {
			} catch (InvocationTargetException e) {
			}
		} else {
			SwingUtilities.invokeLater(r);
		}
	}

	@Override
	public void deviceDisconnected(String devSerialNumber) {
		mConnectedDevSerialNum = null;
		runInEventThread(new Runnable() {
			@Override
			public void run() {
				mBtnConnect.setText("No device");
				mBtnConnect.setEnabled(false);
			}
		}, true);
	}

	public static void main(String[] args) {
		String searchCmd;
		String adbCmd;
		if (System.getProperty("os.name").contains("Windows")) {
			searchCmd = "where";
			adbCmd = "adb.exe";
		} else { // I'm assuming Linux here
			searchCmd = "which";
			adbCmd = "adb";
		}

		ProcessBuilder procBuilder = new ProcessBuilder(searchCmd, adbCmd);
		Process process;
		try {
			process = procBuilder.start();

			ArrayList<String> filePaths = new ArrayList<String>();
			Scanner scanner = new Scanner(process.getInputStream());
			while (scanner.hasNextLine()) {
				filePaths.add(scanner.nextLine());
			}
			scanner.close();

			if (filePaths.size() == 0) {
				System.err
						.println("could not find adb. Please add it's path to $PATH");
				System.err
						.println("if you did not installed android SDK tools yet. please install it: http://d.android.com/sdk/");
			} else {
				MainFrame frame = new MainFrame(filePaths.get(0));
				frame.setVisible(true);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
